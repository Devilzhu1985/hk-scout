import {readdir,readFile,writeFile,mkdir,realpath,lstat,rename,unlink} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {createHash,randomUUID} from 'node:crypto';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {exiftool} from 'exiftool-vendored';
import sharp from 'sharp';
import {zipSync,strToU8} from 'fflate';

const extensions=new Set(['.arw','.dng','.jpg','.jpeg','.png','.heic','.heif','.tif','.tiff']);
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
export async function hashFile(file){
  const hash=createHash('sha256');
  for await(const chunk of createReadStream(file))hash.update(chunk);
  return hash.digest('hex');
}
export function wallTime(value){
  // Never infer a camera time zone from the computer's location or filesystem mtime.
  const match=String(value||'').match(/^(\d{4})[:-](\d{2})[:-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})(\.\d+)?/);
  if(!match)return null;
  const [,y,m,d,h,mi,s,sub='']=match, result=y+'-'+m+'-'+d+'T'+h+':'+mi+':'+s+sub;
  return Number.isFinite(Date.parse(result+'Z'))?result:null;
}
const within=(parent,child)=>{const r=path.relative(parent,child);return !r||!r.startsWith('..'+path.sep)&&r!=='..'&&!path.isAbsolute(r);};
async function atomicJSON(file,data){
  const tmp=file+'.'+randomUUID()+'.tmp';
  await writeFile(tmp,JSON.stringify(data,null,2));await rename(tmp,file);
}
async function walk(dir,root,files,skipped){
  for(const entry of await readdir(dir,{withFileTypes:true})){
    const file=path.join(dir,entry.name),rel=path.relative(root,file).split(path.sep).join('/');
    if(entry.isSymbolicLink()){skipped.push({path:rel,reason:'Symbolic link / junction not followed'});continue;}
    if(entry.isDirectory())await walk(file,root,files,skipped);
    else if(entry.isFile()&&extensions.has(path.extname(file).toLowerCase()))files.push(file);
  }
}
async function metadata(file){
  const tags=await exiftool.readRaw(file,{readArgs:['-n']});
  const names=['Make','Model','LensModel','SerialNumber','BodySerialNumber','DateTimeOriginal','SubSecTimeOriginal','OffsetTimeOriginal','ExposureTime','FNumber','ISO','ExposureCompensation','WhiteBalance','ColorTemperature','ColorSpace','Orientation','ImageWidth','ImageHeight','GPSLatitude','GPSLongitude','GPSLatitudeRef','GPSLongitudeRef'];
  const exif=Object.fromEntries(names.filter(k=>tags[k]!=null).map(k=>[k,tags[k]]));
  return {camera:String(tags.Model||'Unknown camera'),captureWall:wallTime(tags.DateTimeOriginal),exif,warnings:[tags.Warning,tags.Error].filter(Boolean).map(String)};
}
async function preview(file,out,hash){
  const temp=path.join(out,'previews',hash+'.extracted-'+randomUUID()+'.jpg');
  let input=file,source='Rendered source image';
  const ext=path.extname(file).toLowerCase();
  try{
    if(['.arw','.dng'].includes(ext)){
      let extracted=false;
      for(const method of ['extractPreview','extractJpgFromRaw','extractThumbnail']){
        try{await exiftool[method](file,temp);input=temp;source='Embedded JPEG ('+method+')';extracted=true;break;}
        catch{await unlink(temp).catch(()=>{});}
      }
      if(!extracted)throw Error('No supported embedded preview. Export an sRGB JPEG externally; original RAW remains catalogued.');
    }
    const buffer=await sharp(input,{limitInputPixels:150000000}).rotate().resize({width:1600,height:1600,fit:'inside',withoutEnlargement:true}).withIccProfile('srgb').jpeg({quality:85}).toBuffer();
    const previewPath='previews/'+hash+'.jpg';
    await writeFile(path.join(out,previewPath),buffer);
    return {previewPath,previewSha256:digest(buffer),previewSource:source};
  }finally{await unlink(temp).catch(()=>{});}
}
export async function catalogue(input,output,{log=console.log}={}){
  const root=await realpath(input);
  if(!(await lstat(root)).isDirectory())throw Error('Input must be a directory.');
  // Resolve the nearest existing ancestor before creating anything. Junctions cannot
  // make a seemingly separate output directory write into the originals.
  let candidate=path.resolve(output),ancestor=candidate,tail=[];
  while(true){try{ancestor=await realpath(ancestor);break;}catch(err){if(err.code!=='ENOENT')throw err;tail.unshift(path.basename(ancestor));const parent=path.dirname(ancestor);if(parent===ancestor)throw err;ancestor=parent;}}
  candidate=path.join(ancestor,...tail);
  if(within(root,candidate)||within(candidate,root))throw Error('Input and output must be separate, non-nested folders.');
  await mkdir(candidate,{recursive:true});const out=await realpath(candidate);
  const checkpointPath=path.join(out,'catalogue.json');
  let old=null;
  try{old=JSON.parse(await readFile(checkpointPath,'utf8'));if(old.format!=='scout-camera'||old.schemaVersion!==1)throw Error('Output has an incompatible catalogue.');}
  catch(err){if(err.code!=='ENOENT')throw err;}
  if(old?.sourceRoot!==root) {if(old)throw Error('This review folder belongs to a different source folder. Choose a new output folder.');}
  await mkdir(path.join(out,'previews'),{recursive:true});
  const previous=new Map((old?.assets||[]).map(a=>[a.sha256,a])),files=[],skipped=[],errors=[],assets=[],seen=new Map();
  await walk(root,root,files,skipped);files.sort();
  const save=()=>atomicJSON(checkpointPath,{format:'scout-camera',schemaVersion:1,sourceRoot:root,generatedAt:new Date().toISOString(),assets,skipped,errors,complete:false});
  for(let i=0;i<files.length;i++){
    const file=files[i],rel=path.relative(root,file).split(path.sep).join('/');
    log('['+(i+1)+'/'+files.length+'] '+rel);
    try{
      const before=await lstat(file),sha256=await hashFile(file);
      if(seen.has(sha256)){
        const a=seen.get(sha256);a.duplicatePaths=[...new Set([...(a.duplicatePaths||[]),rel])];await save();continue;
      }
      let a=previous.get(sha256),reusable=false;
      if(a?.previewPath==='previews/'+sha256+'.jpg'){
        try{reusable=await hashFile(path.join(out,a.previewPath))===a.previewSha256;}catch{}
      }
      if(reusable)a={...a,originalRelativePath:rel,fileName:path.basename(file),duplicatePaths:[]};
      else{
        a={sha256,fileName:path.basename(file),originalRelativePath:rel,size:before.size,duplicatePaths:[],warnings:[]};
        try{Object.assign(a,await metadata(file));if(!a.captureWall)a.warnings.push('No original capture timestamp; manual association required.');}
        catch(err){a.camera='Unknown camera';a.captureWall=null;a.warnings.push('Metadata: '+err.message);}
        try{Object.assign(a,await preview(file,out,sha256));}
        catch(err){a.warnings.push('Preview: '+err.message);}
      }
      const after=await lstat(file);
      if(after.size!==before.size||after.mtimeMs!==before.mtimeMs)throw Error('Source changed during import. Close the copy/edit operation and retry.');
      assets.push(a);seen.set(sha256,a);await save();
    }catch(err){errors.push({path:rel,error:err.message});await save();}
  }
  // A new batch generation has its own directory. Old batches remain available,
  // never silently overwritten while another device may be importing them.
  const generation=randomUUID(),batchDir=path.join(out,'batches',generation);
  await mkdir(batchDir,{recursive:true});const batches=[];let group=[],bytes=0,index=0;
  async function flush(){
    if(!group.length)return;
    const packet={format:'scout-camera',schemaVersion:1,packageId:generation+'-'+index,assets:group};
    const entries={'catalogue.json':strToU8(JSON.stringify(packet))};
    for(const a of group)if(a.previewPath)entries[a.previewPath]=new Uint8Array(await readFile(path.join(out,a.previewPath)));
    const filename='camera-'+String(++index).padStart(3,'0')+'.zip';
    await writeFile(path.join(batchDir,filename),zipSync(entries,{level:1}));
    batches.push('batches/'+generation+'/'+filename);group=[];bytes=0;
  }
  for(const a of assets){
    const size=a.previewPath?(await lstat(path.join(out,a.previewPath))).size:0;
    if(group.length&&(group.length>=100||bytes+size>48*1024*1024))await flush();
    group.push(a);bytes+=size;
  }
  await flush();
  const result={format:'scout-camera',schemaVersion:1,sourceRoot:root,generatedAt:new Date().toISOString(),assets,skipped,errors,complete:errors.length===0,batches};
  await atomicJSON(checkpointPath,result);
  await writeFile(path.join(out,'IMPORT.txt'),'Import these ZIP files into the destination trip in Scout:\n'+batches.join('\n')+'\n\n'+assets.length+' unique originals; '+errors.length+' unreadable files; '+assets.filter(a=>!a.previewPath).length+' missing previews.\nOriginals remain in: '+root+'\nKeep a second independent copy before erasing a card.\n');
  log('Ready: '+assets.length+' unique originals, '+batches.length+' batches, '+errors.length+' unreadable files, '+assets.filter(a=>!a.previewPath).length+' missing previews.');
  return result;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
  const args=process.argv.slice(2),input=args[args.indexOf('--input')+1],output=args[args.indexOf('--output')+1];
  try{
    if(!args.includes('--input')||!args.includes('--output')||!input||!output)throw Error('Usage: npm run catalogue -- --input "D:/Trip/Originals" --output "D:/Trip/Review"');
    const result=await catalogue(input,output);if(result.errors.length)process.exitCode=2;
  }catch(err){console.error(err.message);process.exitCode=1;}
  finally{await exiftool.end();}
}
