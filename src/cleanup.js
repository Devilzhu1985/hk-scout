import {get,put,revise,now,active} from './model.js';

export const assetBlobIds=r=>r.kind==='asset'?[r.blobId,...(r.originals||[]).map(o=>o.blobId)].filter(Boolean):[];
// Archived records and unresolved incoming versions still own their files.
export const referencedBlobs=state=>new Set([...state.records,...state.conflicts.map(c=>c.incoming)].flatMap(assetBlobIds));

export function cleanupScope(state,request){
  const ids=new Set(request.mode==='archived'?state.records.filter(r=>r.deleted&&!r.purgedAt).map(r=>r.id):request.ids||[]);
  if(request.mode==='unused')return [];
  if(!['records','archived'].includes(request.mode))throw Error('Invalid cleanup request.');
  for(const id of ids)if(!get(state,id))throw Error('Record no longer exists');
  const trips=new Set(state.records.filter(r=>ids.has(r.id)&&r.kind==='trip').map(r=>r.id));
  const sets=new Set(state.records.filter(r=>r.kind==='set'&&(ids.has(r.id)||trips.has(r.tripId))).map(r=>r.id));
  const records=state.records.filter(r=>!r.purgedAt&&(ids.has(r.id)||trips.has(r.tripId)||sets.has(r.setId)));
  const affected=new Set(records.map(r=>r.id));
  if(state.conflicts.some(c=>affected.has(c.incoming.id)||trips.has(c.incoming.tripId)||sets.has(c.incoming.setId)))throw Error('Resolve this selection’s conflicts in Transfer before deleting.');
  return records;
}

export function planCleanup(snapshot,request){
  const records=cleanupScope(snapshot.state,request),ids=new Set(records.map(r=>r.id));
  const remaining={...snapshot.state,records:snapshot.state.records.filter(r=>!ids.has(r.id))};
  const protectedIds=referencedBlobs(remaining),candidates=new Set(records.flatMap(assetBlobIds));
  const files=snapshot.blobs.filter(b=>!protectedIds.has(b.id)&&(request.mode==='unused'||candidates.has(b.id)));
  return {request:structuredClone(request),generation:snapshot.state.generation,records,blobIds:files.map(b=>b.id),bytes:files.reduce((n,b)=>n+b.blob.size,0),
    counts:Object.fromEntries(['trip','set','asset','reading','clock'].map(kind=>[kind,records.filter(r=>r.kind===kind).length])),
    shared:[...candidates].filter(id=>protectedIds.has(id)).length};
}

// Keep revision ancestry and required identity fields, not GPS, notes or image metadata.
function tombstone(r,device){
  const revised=revise(r,{deleted:true,purgedAt:now()},device);
  const keys=['id','kind','revision','ancestors','updatedAt','device','deleted','purgedAt','tripId','setId'];
  const required={trip:['name','city','timezone','template'],set:['name','code','startedAt','endedAt','timezone'],asset:['fileName','source'],reading:['method','value','measuredAt'],clock:['from','to','offsetSeconds','utcOffsetMinutes']};
  const out=Object.fromEntries([...keys,...required[r.kind]].filter(k=>Object.hasOwn(revised,k)).map(k=>[k,revised[k]]));
  if(r.kind==='trip')out.city='';
  if(r.kind==='set')Object.assign(out,{notes:'',camera:{name:''}});
  if(r.kind==='reading')Object.assign(out,{instrument:'',protocol:'',calibration:''});
  if(r.kind==='clock')out.camera='';
  return out;
}

export function applyCleanup(state,attachments,local,plan){
  if(state.generation!==plan.generation)throw Error('Records changed. Review the cleanup again.');
  const records=cleanupScope(state,plan.request);
  for(const r of records)put(state,tombstone(r,local.device.id));
  if(records.some(r=>r.id==='legacy-hk'))state.legacy=[];
  attachments.removeUnused(plan.blobIds);
  if(!active(state,'trip').some(r=>r.id===local.activeTrip))local.activeTrip=active(state,'trip')[0]?.id||null;
  const target=local.nextDestination;
  if(target&&(get(state,target.tripId)?.deleted||get(state,target.recordId)?.deleted))delete local.nextDestination;
  return {records:records.length,files:plan.blobIds.length,bytes:plan.bytes};
}
