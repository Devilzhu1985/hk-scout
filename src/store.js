import {blank, uid, validateRecords, validateReferences} from './model.js';
import {referencedBlobs} from './cleanup.js';
export class Store {
  async open(name='scout-v2') {
    this.db=await new Promise((resolve,reject)=>{ const q=indexedDB.open(name,1); q.onupgradeneeded=()=>{q.result.createObjectStore('state');q.result.createObjectStore('blobs',{keyPath:'id'});q.result.createObjectStore('local');}; q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error); });
    await this.change((s,blobs,local)=>{ if(!local.device) local.device={id:uid(),name:'My phone / computer'}; });
    return this;
  }
  async read() {
    return new Promise((resolve,reject)=>{const tx=this.db.transaction(['state','blobs','local']);const a=tx.objectStore('state').get('main'),b=tx.objectStore('blobs').getAll(),c=tx.objectStore('local').get('preferences');tx.oncomplete=()=>resolve({state:a.result||blank(),blobs:b.result||[],local:c.result||{}});tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error('Read aborted'));});
  }
  // Read and write happen inside one serialized transaction, including attachments.
  async change(fn) {
    return new Promise((resolve,reject)=>{
      const tx=this.db.transaction(['state','blobs','local'],'readwrite'); const states=tx.objectStore('state'),localStore=tx.objectStore('local');
      const a=states.get('main'),b=localStore.get('preferences');let output,reason;
      const blobStore=tx.objectStore('blobs'),removals=new Set();let pending=0,finalState=null;
      const remove=()=>{if(pending||!finalState)return;const retained=referencedBlobs(finalState);for(const id of removals)if(!retained.has(id))blobStore.delete(id);};
      const attachments={put(item){
        pending++;
        const q=blobStore.get(item.id);
        q.onsuccess=()=>{if(q.result&&q.result.sha256!==item.sha256){reason=Error('Attachment ID collision. No changes saved.');tx.abort();}else{blobStore.put(item);pending--;remove();}};
      },removeUnused(ids){for(const id of ids)removals.add(id);}};
      b.onsuccess=()=>{try {const state=a.result||blank(),local=b.result||{};output=fn(state,attachments,local);if(output?.then)throw Error('Transactions must be synchronous');validateRecords(state.records);validateReferences(state.records);state.generation++;states.put(state,'main');localStore.put(local,'preferences');finalState=state;remove();}catch(e){reason=e;tx.abort();}};
      tx.oncomplete=()=>resolve(output);tx.onerror=()=>reject(reason||tx.error);tx.onabort=()=>reject(reason||tx.error||Error('Save interrupted'));
    });
  }
}
