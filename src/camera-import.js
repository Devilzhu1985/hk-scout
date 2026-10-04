import * as exifr from 'exifr/dist/full.esm.mjs';
import {sha256} from './packets.js';
import {record} from './model.js';

export const cameraFile = file=>/\.(arw|dng|cr2|cr3|nef|nrw|raf|orf|rw2|pef|srw|jpe?g|png|webp|heic|heif|tiff?)$/i.test(file.name);
export function cameraWall(value){
  const m=typeof value==='string'&&value.match(/^(\d{4})[:-](\d{2})[:-](\d{2})[ T](\d{2}:\d{2}:\d{2}(?:\.\d+)?)/);
  return m?`${m[1]}-${m[2]}-${m[3]}T${m[4]}`:null;
}
const keys=['Make','Model','LensModel','BodySerialNumber','SerialNumber','DateTimeOriginal','SubSecTimeOriginal','OffsetTimeOriginal','ExposureTime','FNumber','ISO','WhiteBalance','ColorTemperature','Orientation','ImageWidth','ImageHeight'];
async function preview(file){
  let bitmap;
  try{bitmap=await createImageBitmap(file);}catch{
    // exifr exposes embedded JPEG thumbnails where supported; this is not a RAW render.
    const bytes=await exifr.thumbnail(file).catch(()=>null);
    if(!bytes)return null;
    bitmap=await createImageBitmap(new Blob([bytes],{type:'image/jpeg'}));
  }
  try{const scale=Math.min(1,1000/Math.max(bitmap.width,bitmap.height)),canvas=document.createElement('canvas');
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));
    canvas.getContext('2d').drawImage(bitmap,0,0,canvas.width,canvas.height);
    return await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',0.8));
  }finally{bitmap.close();}
}
export async function prepareCameraFiles(files,{tripId,device,existing=[],progress=()=>{},signal}={}){
  const accepted=files.filter(cameraFile),records=[],blobs=[],failed=[];
  if(accepted.length>500)throw Error('Choose up to 500 camera files per import, or use the desktop RAW importer.');
  const seen=new Set(existing.map(a=>a.sha256));let duplicates=0,total=0;
  for(const [index,file]of accepted.entries()){
    if(signal?.aborted)throw new DOMException('Cancelled','AbortError');
    progress(index,accepted.length,file.name);
    try{
      if(!file.size||file.size>256*1024*1024)throw Error('File is empty or larger than 256 MiB. Use the desktop RAW importer.');
      const hash=await sha256(await file.arrayBuffer());if(seen.has(hash)){duplicates++;continue;}
      const warnings=[];let exif={};
      try{exif=await exifr.parse(file,{pick:keys,reviveValues:false})||{};}catch{warnings.push('Metadata unavailable in browser; use the desktop RAW importer for full extraction.');}
      // Only plain scalar EXIF values travel in the notebook.
      exif=Object.fromEntries(Object.entries(exif).filter(([key,v])=>keys.includes(key)&&['string','number','boolean'].includes(typeof v)));
      let blob=null;try{blob=await preview(file);}catch{ /* unsupported format still has a stable file identity */ }
      if(!blob)warnings.push('Preview unavailable in browser; import a desktop catalogue for a RAW preview.');
      const previewHash=blob?await sha256(await blob.arrayBuffer()):null;
      const blobId=previewHash?'preview-'+previewHash:null;
      if(blob){total+=blob.size;if(total>48*1024*1024)throw Error('Preview budget reached. Choose a smaller folder.');blobs.push({id:blobId,blob,sha256:previewHash});}
      const captureWall=cameraWall(exif.SubSecTimeOriginal||exif.DateTimeOriginal);
      if(!captureWall)warnings.push('No readable camera timestamp; assign this image manually.');
      records.push(record('asset',{tripId,setId:null,source:'camera',fileName:file.name,originalRelativePath:file.webkitRelativePath||file.name,sha256:hash,blobId,captureWall,camera:String(exif.Model||'Unknown camera'),exif,warnings,previewSource:'Browser-rendered image or embedded JPEG; original stays external',role:'unclassified',notes:'',tags:[]},device,'camera-'+tripId+'-'+hash));seen.add(hash);
    }catch(err){failed.push({name:file.name,reason:err.message});}
    await new Promise(resolve=>setTimeout(resolve,0));
  }
  if(signal?.aborted)throw new DOMException('Cancelled','AbortError');
  return {records,blobs,duplicates,failed,ignored:files.length-accepted.length};
}
