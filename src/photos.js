import {uid} from './model.js';
import {sha256} from './packets.js';
export const ORIGINAL_MIMES=['image/jpeg','image/png','image/webp','image/heic','image/heif','image/x-adobe-dng'];
export function imageType(file){
  const extension=file.name.split('.').at(-1).toLowerCase();
  const type={jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',heic:'image/heic',heif:'image/heif',dng:'image/x-adobe-dng'}[extension];
  if(!type||!ORIGINAL_MIMES.includes(type))throw Error('Choose JPEG, PNG, WebP, HEIC or DNG. Use the desktop importer for Sony RAW.');
  return type;
}
// Originals and preview have separate identities and checksums. Never describe a
// canvas-generated JPEG as an untouched camera file.
export async function preparePhotos(files){
  if(files.reduce((n,f)=>n+f.size,0)>56*1024*1024)throw Error('Originals exceed 56 MiB per image record. Keep them on your computer and use the desktop importer.');
  const originals=[],blobs=[];let preview=null;
  for(const file of files){
    const mime=imageType(file),id=uid(),hash=await sha256(await file.arrayBuffer());
    originals.push({blobId:id,fileName:file.name,mime,sha256:hash,size:file.size});
    blobs.push({id,blob:new Blob([file],{type:mime}),sha256:hash});
    if(!preview&&mime!=='image/x-adobe-dng'){
      let bitmap;
      try{bitmap=await createImageBitmap(file);const scale=Math.min(1,1600/Math.max(bitmap.width,bitmap.height)),c=document.createElement('canvas');c.width=Math.round(bitmap.width*scale);c.height=Math.round(bitmap.height*scale);c.getContext('2d').drawImage(bitmap,0,0,c.width,c.height);const blob=await new Promise(r=>c.toBlob(r,'image/jpeg',.85));if(blob){preview={id:uid(),blob,sha256:await sha256(await blob.arrayBuffer())};blobs.push(preview);}}
      catch{/* An unsupported HEIC decoder must not discard the original. */}finally{bitmap?.close();}
    }
  }
  return {originals,blobs,blobId:preview?.id||null};
}
