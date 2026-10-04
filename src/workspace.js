import {get,put,revise,now} from './model.js';

export function workspaceAssets(state,tripId,{filter='all',search='',stop='',camera=''}={}){
  const query=search.trim().toLocaleLowerCase();
  return state.records.filter(a=>a.kind==='asset'&&a.tripId===tripId&&!a.purgedAt&&
    (filter==='trash'?a.deleted:!a.deleted)&&
    (filter!=='unassigned'||!a.setId)&&(filter!=='phone'||a.source==='field')&&
    (filter!=='camera'||a.source==='camera')&&(!stop||a.setId===stop)&&(!camera||a.camera===camera)&&
    (!query||[a.fileName,a.role,a.camera,a.notes,...(a.tags||[])].join(' ').toLocaleLowerCase().includes(query)));
}

export function rangeSelection(visible,selected,id,anchor,shift){
  const result=new Set(selected),end=visible.indexOf(id),start=visible.indexOf(anchor);
  if(end<0)return result;
  if(shift&&start>=0)for(const key of visible.slice(Math.min(start,end),Math.max(start,end)+1))result.add(key);
  else if(result.has(id))result.delete(id);else result.add(id);
  return result;
}

// The caller commits this inside Store.change. Validate the complete batch before writing.
export function batchAssets(state,expected,operation,value,device){
  if(!expected.length)throw Error('Select images first.');
  const rows=expected.map(({id,revision})=>{const a=get(state,id);
    if(!a||a.kind!=='asset'||a.purgedAt||a.revision!==revision)throw Error('Files changed. Select them again.');
    if(state.conflicts.some(c=>[a.id,a.tripId,a.setId].includes(c.incoming.id)))throw Error('Resolve related import conflicts first.');
    if(get(state,a.tripId)?.deleted||get(state,a.setId)?.deleted)throw Error('Restore the parent trip or stop first.');
    if((operation==='restore')!==a.deleted)throw Error('Files changed. Select them again.');
    if(operation==='assign'&&value){const set=get(state,value);if(!set||set.kind!=='set'||set.deleted||set.tripId!==a.tripId)throw Error('Choose a stop in the same trip.');}
    return a;
  });
  if(!['trash','restore','assign','role'].includes(operation))throw Error('Invalid batch action.');
  if(operation==='role'&&(typeof value!=='string'||value.length>100))throw Error('Invalid image role.');
  const receipt=[];
  for(const a of rows){const changes=operation==='trash'?{deleted:true}:operation==='restore'?{deleted:false}:operation==='role'?{role:value}:{setId:value||null,association:{method:'manual',confirmedAt:now()}};
    const updated=revise(a,changes,device);put(state,updated);receipt.push({id:a.id,revision:updated.revision});
  }
  return receipt;
}
