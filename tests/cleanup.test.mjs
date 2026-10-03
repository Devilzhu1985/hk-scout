import test from 'node:test';
import assert from 'node:assert/strict';
import 'fake-indexeddb/auto';
import {blank,record,get,put,revise,merge,validateRecords,validateReferences} from '../src/model.js';
import {Store} from '../src/store.js';
import {planCleanup,applyCleanup,referencedBlobs} from '../src/cleanup.js';
import {exportField,decodeField,unpack,sha256} from '../src/packets.js';
const trip=id=>record('trip',{name:id,city:'Hong Kong',timezone:'Asia/Hong_Kong',template:'blank'},'phone',id);
const stop=(id,tripId)=>record('set',{tripId,name:id,code:id,startedAt:'2026-10-03T00:00:00.000Z',endedAt:null,timezone:'Asia/Hong_Kong',camera:{name:'Sony'},notes:'keep me',location:{lat:22,lng:114}},'phone',id);
const asset=(id,setId,tripId,blobId)=>record('asset',{tripId,setId,blobId,source:'field',fileName:id+'.jpg',notes:'image notes'},'phone',id);
async function fixture(){
 const db=await new Store().open('cleanup-'+crypto.randomUUID()),state=blank();
 state.records=[trip('A'),trip('B'),stop('sa','A'),stop('sb','B'),asset('a1','sa','A','shared'),asset('a2','sa','A','unique'),asset('b1','sb','B','shared')];
 const blobs=await Promise.all(['shared','unique','orphan'].map(async id=>{const blob=new Blob([id],{type:'image/jpeg'});return {id,blob,sha256:await sha256(await blob.arrayBuffer())};}));
 await db.change((s,b,l)=>{s.records=state.records;for(const blob of blobs)b.put(blob);l.activeTrip='A';l.nextDestination={tripId:'A',recordId:'sa'};});return db;
}
test('trip cleanup cascades, protects shared originals, clears active route and exports deletion markers',async()=>{
 const db=await fixture(),before=await db.read(),plan=planCleanup(before,{mode:'records',ids:['A']});
 assert.deepEqual(plan.counts,{trip:1,set:1,asset:2,reading:0,clock:0});assert.equal(plan.shared,1);assert.deepEqual(plan.blobIds,['unique']);
 await db.change((s,b,l)=>applyCleanup(s,b,l,plan));const after=await db.read();
 assert.equal(after.local.activeTrip,'B');assert.equal(after.local.nextDestination,undefined);assert.equal(get(after.state,'sa').location,undefined);assert.equal(get(after.state,'sa').notes,'');
 for(const id of ['A','sa','a1','a2'])assert(get(after.state,id).purgedAt);
 assert.deepEqual(after.blobs.map(b=>b.id),['orphan','shared']);assert.equal(get(after.state,'b1').deleted,false);
 const decoded=await decodeField(unpack((await exportField(after,'A')).bytes));assert.equal(decoded.blobs.length,0);assert(decoded.packet.records.every(r=>r.purgedAt));
 const result=merge(before.state,decoded.packet.records,'delete-package');assert.equal(get(result.state,'A').deleted,true);
 db.db.close();
});
test('stale backup cannot restore cleaned images or reattach their bytes',async()=>{
 const db=await fixture(),before=await db.read();await db.change((s,b,l)=>applyCleanup(s,b,l,planCleanup(before,{mode:'records',ids:['a2']})));
 await db.change((s,b)=>{Object.assign(s,merge(s,before.state.records,'old').state);const refs=referencedBlobs(s);for(const blob of before.blobs)if(refs.has(blob.id))b.put(blob);});
 const after=await db.read();assert(get(after.state,'a2').purgedAt);assert(!after.blobs.some(b=>b.id==='unique'));db.db.close();
});
test('preview revision check rejects a concurrent addition without deleting any files',async()=>{
 const db=await fixture(),plan=planCleanup(await db.read(),{mode:'records',ids:['A']});await db.change(s=>put(s,asset('new','sa','A','shared')));
 await assert.rejects(db.change((s,b,l)=>applyCleanup(s,b,l,plan)),/Records changed/);
 const after=await db.read();assert.equal(get(after.state,'A').deleted,false);assert.equal(after.blobs.length,3);db.db.close();
});
test('conflicting versions block related deletion and protect unreferenced-looking attachments',async()=>{
 const db=await fixture();await db.change(s=>s.conflicts.push({id:'conflict',incoming:revise(get(s,'a2'),{blobId:'orphan'},'desktop')}));const before=await db.read();
 assert.throws(()=>planCleanup(before,{mode:'records',ids:['A']}),/conflicts/);
 assert.equal(planCleanup(before,{mode:'unused'}).blobIds.length,0);db.db.close();
});
test('archive cleanup includes active children; failed transaction rolls back markers and bytes',async()=>{
 const db=await fixture();await db.change(s=>put(s,revise(get(s,'sa'),{deleted:true},'phone')));const plan=planCleanup(await db.read(),{mode:'archived'});
 assert.equal(plan.counts.asset,2);
 await assert.rejects(db.change((s,b,l)=>{applyCleanup(s,b,l,plan);get(s,'B').timezone='invalid';}));
 let data=await db.read();assert.equal(data.blobs.length,3);assert(!get(data.state,'sa').purgedAt);
 await db.change((s,b,l)=>applyCleanup(s,b,l,plan));data=await db.read();assert.equal(data.local.nextDestination,undefined);assert.equal(get(data.state,'a1').deleted,true);db.db.close();
});
test('unused-file cleanup protects archive bytes and pending writes, last-trip cleanup remains valid',async()=>{
 const db=await fixture();await db.change(s=>put(s,revise(get(s,'a2'),{deleted:true},'phone')));let data=await db.read();const plan=planCleanup(data,{mode:'unused'});assert.deepEqual(plan.blobIds,['orphan']);
 await db.change((s,b,l)=>applyCleanup(s,b,l,plan));
 await db.change((s,b)=>{const item={id:'pending',blob:new Blob(['pending']),sha256:'a'.repeat(64)};b.put(item);b.removeUnused(['pending','shared']);});
 data=await db.read();assert.deepEqual(data.blobs.map(b=>b.id),['shared','unique']);
 await db.change((s,b,l)=>applyCleanup(s,b,l,planCleanup(data,{mode:'records',ids:['A','B']})));data=await db.read();assert.equal(data.local.activeTrip,null);assert.equal(data.blobs.length,0);validateRecords(data.state.records);validateReferences(data.state.records);db.db.close();
});
test('purged markers cannot retain originals or be restored under a deleted parent',async()=>{
 const db=await fixture(),before=await db.read();await db.change((s,b,l)=>applyCleanup(s,b,l,planCleanup(before,{mode:'records',ids:['A']})));const after=await db.read();
 assert.throws(()=>validateRecords([{...get(after.state,'a1'),blobId:'shared'}]),/permanent deletion/);
 assert.throws(()=>validateRecords([{...get(after.state,'a1'),deleted:false}]),/permanent deletion/);
 const live=asset('new','sa','A','shared');assert.throws(()=>validateReferences([...after.state.records,live]),/permanently deleted/);db.db.close();
});
