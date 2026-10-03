import {zipSync, unzipSync, strToU8, strFromU8} from 'fflate';
import {VERSION,uid,now,validateRecords,validateReferences,active,canonical} from './model.js';
export const MAX_PACKAGE=64*1024*1024;
export const sha256=async bytes=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(b=>b.toString(16).padStart(2,'0')).join('');
export const escapeHTML=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const csvCell=v=>'"'+String(v??'').replace(/^[=+@\-]/,"'$&").replaceAll('"','""')+'"';
export function unpack(bytes) {
  if(bytes.byteLength>MAX_PACKAGE)throw Error('Package exceeds 64 MiB. Export a smaller trip or split desktop batches.');
  let total=0;
  const files=unzipSync(bytes,{filter:f=>{total+=f.originalSize; if(total>MAX_PACKAGE || f.originalSize>MAX_PACKAGE)throw Error('Expanded package exceeds 64 MiB.');return true;}});
  for(const name of Object.keys(files))if(name.includes('..') || name.startsWith('/') || name.includes('\\'))throw Error('Unsafe package path.');
  return files;
}
export async function exportField(snapshot,tripId,handoff=false,assetIds=null) {
  if(snapshot.state.conflicts.length)throw Error('Resolve import conflicts before exporting a handoff or backup.');
  const records=snapshot.state.records.filter(r=>(r.id===tripId||r.tripId===tripId)&&(r.kind!=='asset'||!assetIds||assetIds.has(r.id)));
  validateRecords(records);validateReferences(records);
  const originalIds=new Set(records.filter(r=>r.kind==='asset').flatMap(r=>(r.originals||[]).map(o=>o.blobId)));
  const refs=new Set([...records.filter(r=>r.kind==='asset'&&r.blobId).map(r=>r.blobId),...originalIds]);
  const files={},attachments=[];
  let total=0;
  for(const id of refs){const item=snapshot.blobs.find(b=>b.id===id);if(!item)throw Error('A referenced image is missing: '+id);const bytes=new Uint8Array(await item.blob.arrayBuffer());total+=bytes.length;if(total>MAX_PACKAGE-2*1024*1024)throw Error('Trip images exceed the portable package limit. Use a smaller trip.');const ext={'image/jpeg':'jpg','image/png':'png','image/webp':'webp','image/heic':'heic','image/heif':'heif','image/x-adobe-dng':'dng'}[item.blob.type]||'bin';const path=originalIds.has(id)?'originals/'+id+'.'+ext:'photos/'+id+'.jpg';files[path]=bytes;attachments.push({id,path,sha256:await sha256(bytes),mime:item.blob.type});}
  const packet={format:'scout-field',schemaVersion:VERSION,packageId:uid(),exportedAt:now(),records,attachments,legacy:tripId==='legacy-hk'?snapshot.state.legacy:[]};
  files['field.json']=strToU8(JSON.stringify(packet));
  const trip=records.find(r=>r.kind==='trip');
  const sets=records.filter(r=>r.kind==='set'&&!r.deleted);
  const assets=records.filter(r=>r.kind==='asset'&&!r.deleted);
  const summary=assets.map(a=>({assetId:a.id,file:a.fileName,original:a.originalRelativePath||'',sha256:a.sha256||'',set:sets.find(s=>s.id===a.setId)?.code||'UNRESOLVED',role:a.role||'unclassified',notes:a.notes||''}));
  files['catalogue.csv']=strToU8('\ufeff'+[['Asset ID','File','Original relative path','SHA256','Photo set','Role','Notes'],...summary.map(a=>Object.values(a))].map(row=>row.map(csvCell).join(',')).join('\r\n'));
  const cards=assets.map(a=>`<article>${a.blobId?`<img loading="lazy" src="photos/${escapeHTML(a.blobId)}.jpg" alt="">`:'<p>No preview</p>'}<h3>${escapeHTML(a.fileName)}</h3><p>${escapeHTML(sets.find(s=>s.id===a.setId)?.code||'UNRESOLVED')} · ${escapeHTML(a.role||'unclassified')}</p><p>${escapeHTML(a.notes)}</p><small>${escapeHTML(a.originalRelativePath||'Phone reference')}</small></article>`).join('');
  files['contact-sheet.html']=strToU8(`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'"><title>${escapeHTML(trip.name)} · Scout</title><style>body{font:16px system-ui;background:#f1f0e8;padding:24px;color:#1d342d}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:18px}article{padding:14px;background:white;overflow-wrap:anywhere}img{width:100%;height:210px;object-fit:contain}small{color:#666}</style><h1>${escapeHTML(trip.name)}</h1><p>Sony originals are external. Phone originals are in originals/ when listed in field.json. This sheet displays previews, not RAW data.</p><main>${cards}</main>`);
  files['HANDOFF.md']=strToU8(`# Scout handoff: ${trip.name}\n\nExported: ${packet.exportedAt}\nPackage: ${packet.packageId}\nCity: ${trip.city}; trip display zone: ${trip.timezone}\n\n## Purpose\nOrganize references for environment art and lighting. Review previews with field.json and catalogue.csv. Sony originals remain in the separate camera folder. Phone originals, when recorded, are included under originals/ with filenames, types and checksums in each asset.originals manifest. Older records may only have previews. Match by stable asset IDs and hashes, not filenames alone.\n\n## Review rules\nPreserve RAW originals and exposure brackets. Treat field measurements and original EXIF as recorded evidence; label image interpretations and suggested matches separately. Do not infer measured lux, CCT, physical albedo or exact locations from appearance. The Sony lens cover is an uncalibrated reference. Never auto-delete similar images. Mark ambiguous associations unresolved. Preview inspection does not establish RAW-level sharpness or clipping.\n\n## Inventory\n${sets.length} photo sets; ${assets.length} images; ${assets.filter(a=>!a.setId).length} unresolved images.\n\n## Photo sets\n${sets.map(s=>`- ${s.code}: ${s.name}, ${s.startedAt} to ${s.endedAt||'OPEN'}, ${s.timezone}\n  ${s.notes||'No notes'}`).join('\n')}\n\n## Weather snapshots\nWeather in each set.weather is a saved Open-Meteo model estimate, not an on-site observation. Source: https://open-meteo.com/. Preserve validAt, retrievedAt, fixAt, source, requested/grid coordinates, timezone, units and intervalSeconds; do not reinterpret it as measured lighting or replace it with current weather.\n\n## Measurements\n${records.filter(r=>r.kind==='reading'&&!r.deleted).map(r=>`- ${r.label}: ${r.value} ${r.method.endsWith('lux')?'lux':'EV100'}; ${r.instrument}; ${r.protocol}; ${r.calibration}`).join('\n')||'None recorded.'}\n\n## Restore\nImport this ZIP into Scout, review the merge summary, then apply it. Keep the originals and this package on two independent storage devices. This export does not send files to any AI service.\n`);
  if(Object.values(files).reduce((sum,file)=>sum+file.length,0)>MAX_PACKAGE)throw Error('Expanded package exceeds 64 MiB. Use a smaller trip.');
  const bytes=zipSync(files,{level:3});
  if(bytes.length>MAX_PACKAGE)throw Error('Package exceeds 64 MiB.');
  return {bytes,packet,filename:'Scout-'+trip.name.replace(/[^\p{L}\p{N}_-]/gu,'_')+'-'+new Date().toISOString().slice(0,10)+(handoff?'-handoff':'-backup')+'.zip'};
}
export function planTripExport(snapshot,tripId,handoff=false) {
  const assets=snapshot.state.records.filter(r=>r.kind==='asset'&&r.tripId===tripId);
  const sizes=new Map(snapshot.blobs.map(b=>[b.id,b.blob.size]));
  const groups=[];let ids=new Set(),total=0;
  for(const asset of assets){
    const size=(sizes.get(asset.blobId)||0)+(asset.originals||[]).reduce((sum,o)=>sum+(sizes.get(o.blobId)||0),0);
    if(ids.size&&(ids.size>=100||total+size>32*1024*1024)){groups.push(ids);ids=new Set();total=0;}
    ids.add(asset.id);total+=size;
  }
  if(ids.size||!groups.length)groups.push(ids);
  return groups.map((assetIds,index)=>({tripId,handoff,assetIds,index,count:groups.length}));
}
export async function exportTripPart(snapshot,part){
  const result=await exportField(snapshot,part.tripId,part.handoff,part.assetIds);
  if(part.count>1)result.filename=result.filename.replace(/\.zip$/,'-part-'+(part.index+1)+'-of-'+part.count+'.zip');
  return result;
}
export async function exportTrip(snapshot,tripId,handoff=false){
  const results=[];for(const part of planTripExport(snapshot,tripId,handoff))results.push(await exportTripPart(snapshot,part));return results;
}
export async function decodeField(files) {
  if(!files['field.json'])throw Error('No field.json found.');
  const p=JSON.parse(strFromU8(files['field.json']));
  if(p.format!=='scout-field'||p.schemaVersion!==VERSION||typeof p.packageId!=='string'||p.packageId.length>180)throw Error('Unsupported field package version.');
  validateRecords(p.records); validateReferences(p.records);
  if(!Array.isArray(p.attachments)||p.attachments.length>10000)throw Error('Invalid attachment manifest.');
  const blobs=[],ids=new Set();
  for(const a of p.attachments){if(!/^[\w-]{1,180}$/.test(a.id)||ids.has(a.id)||!['image/jpeg','image/png','image/webp','image/heic','image/heif','image/x-adobe-dng'].includes(a.mime)||!files[a.path])throw Error('Invalid or missing attachment.');ids.add(a.id);const bytes=files[a.path];if(await sha256(bytes)!==a.sha256)throw Error('Attachment checksum mismatch: '+a.path);blobs.push({id:a.id,blob:new Blob([bytes],{type:a.mime}),sha256:a.sha256});}
  for(const r of p.records)if(r.kind==='asset'&&r.blobId&&!ids.has(r.blobId))throw Error('Package is missing an image referenced by a record.');
  for(const r of p.records)for(const original of r.originals||[]){const attachment=p.attachments.find(a=>a.id===original.blobId);if(!attachment||attachment.sha256!==original.sha256||attachment.mime!==original.mime||files[attachment.path].length!==original.size)throw Error('Original image manifest does not match its attachment.');}
  if(p.legacy && (!Array.isArray(p.legacy)||JSON.stringify(p.legacy).length>8*1024*1024))throw Error('Invalid legacy archive.');
  return {packet:p,blobs};
}
