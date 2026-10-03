import test from 'node:test';
import assert from 'node:assert/strict';
import 'fake-indexeddb/auto';
import {blank,record,revise,put,get,merge,resolve,migrateLegacy,ev100,comparable,cameraTime,suggest,validateRecords,validateReferences} from '../src/model.js';
import {Store} from '../src/store.js';
import {exportField,exportTrip,decodeField,unpack,sha256,csvCell,MAX_PACKAGE} from '../src/packets.js';
import {daylight} from '../src/sun.js';
import {zipSync,strToU8} from 'fflate';
const trip=()=>record('trip',{name:'Field test',city:'Hong Kong',timezone:'Asia/Hong_Kong',template:'blank'},'phone','trip');
const set=()=>record('set',{tripId:'trip',code:'S-TEST',name:'Market',notes:'',city:'Hong Kong',timezone:'Asia/Hong_Kong',startedAt:'2026-10-03T04:00:00.000Z',endedAt:'2026-10-03T04:10:00.000Z',camera:{name:'ILCE-7CR'}},'phone','set');
const initial=()=>{const s=blank();s.records=[trip(),set()];return s;};

test('exposure settings and comparable lux have separate semantics',()=>{
  assert.equal(ev100(4,1/100,400),Math.log2(400));
  assert.equal(ev100(0,1,100),null);
  const a={method:'phone_lux',instrument:'phone A',protocol:'upward',calibration:'unverified',value:100};
  assert(comparable(a,{...a,value:200}));
  for(const changes of [{value:0},{method:'camera_ev'},{instrument:'phone B'},{protocol:'toward window'},{calibration:'different'}])assert(!comparable(a,{...a,...changes}));
});
test('divergent offline edits require review; resolved ancestry rejects stale deletion reversal',()=>{
  const source=initial(),base=get(source,'set');
  const local=revise(base,{notes:'local'},'phone'),remote=revise(base,{notes:'remote'},'desktop');
  put(source,local);
  const result=merge(source,[remote],'packet1');
  const c=result.state.conflicts.find(c=>c.incoming.id==='set');
  assert(c);assert.equal(get(result.state,'set').notes,'local');
  resolve(result.state,c.id,true,'phone');assert.equal(get(result.state,'set').notes,'remote');
  const deleted=revise(get(result.state,'set'),{deleted:true},'phone');put(result.state,deleted);
  const stale=merge(result.state,[base],'old-package');
  assert.equal(get(stale.state,'set').deleted,true);
  assert.equal(merge(stale.state,[base],'old-package').duplicate,true);
});
test('same revision with changed content and invalid references are rejected',()=>{
  const s=initial();assert.throws(()=>merge(s,[{...get(s,'set'),name:'tampered'}],'tamper'),/Same revision/);
  assert.throws(()=>validateReferences([set()]),/missing trip/);
  assert.throws(()=>validateRecords([{...set(),startedAt:'2026-10-03 12:00:00'}]),/interval/);
});
test('camera matching needs a unique explicit clock segment and a closed matching-camera interval',()=>{
  const asset={captureWall:'2026-10-03T11:59:00',tripId:'trip',camera:'ILCE-7CR'};
  const clock={camera:'ILCE-7CR',from:'2026-10-03T00:00:00Z',to:'2026-10-03T23:59:59Z',utcOffsetMinutes:480,offsetSeconds:90};
  assert.equal(cameraTime(asset,[]).time,null);
  assert.equal(cameraTime(asset,[clock]).time,'2026-10-03T04:00:30.000Z');
  assert.equal(suggest(asset,[set()],[clock]).sets.length,1);
  assert.equal(suggest(asset,[{...set(),endedAt:null}],[clock]).sets.length,0);
  assert.equal(cameraTime(asset,[clock,clock]).reason,'Overlapping clock segments');
  assert.equal(suggest(asset,[set(),{...set(),id:'second'}],[clock]).sets.length,2);
});
test('v1 conversion is deterministic and does not invent historic global settings',()=>{
  const old={v:1,settings:{wb:5500,fnum:1.8},points:[{id:'p',t:Date.parse('2026-10-03T12:00:00Z'),notes:'n'.repeat(5000),photos:['p1'],lux:{A:'',B:0,C:'100',D:'bad'}}]};
  const a=migrateLegacy(old,'browser'),b=migrateLegacy(old,'file');
  assert.deepEqual(a,b);validateRecords(a);validateReferences(a);
  assert.equal(a[1].camera.wb,null);assert.equal(a[1].camera.aperture,null);
  assert.equal(a[1].legacyPoint.notes.length,5000);
  assert.deepEqual(a.filter(r=>r.kind==='reading').map(r=>r.value),[0,100]);
});
test('transactions serialize concurrent edits and abort records and photos together',async()=>{
  const store=await new Store().open('transactions-'+crypto.randomUUID());
  await store.change(s=>s.records.push(trip()));
  await Promise.all(Array.from({length:30},(_,n)=>store.change(s=>{s.legacy.push(n);})));
  assert.equal((await store.read()).state.legacy.length,30);
  await assert.rejects(store.change((s,b)=>{b.put({id:'ghost',sha256:'a',blob:new Blob(['abc'])});s.records.push({...set(),tripId:'missing'});}),/missing trip/);
  assert.equal((await store.read()).blobs.length,0);
  assert.equal((await store.read()).state.records.length,1);
  await store.change((s,b)=>b.put({id:'one',sha256:'abc',blob:new Blob(['abc'])}));
  await assert.rejects(store.change((s,b)=>{s.legacy.push('should roll back');b.put({id:'one',sha256:'def',blob:new Blob(['def'])});}),/collision/);
  assert.equal((await store.read()).state.legacy.length,30);
  assert.equal(await (await store.read()).blobs[0].blob.text(),'abc');
  store.db.close();
});
test('ZIP round trip preserves image bytes, revisions, tombstones and handoff; corrupt data is rejected',async()=>{
  const state=initial(),bytes=new Uint8Array([1,2,3]),hash=await sha256(bytes);
  state.records.push(record('asset',{tripId:'trip',setId:'set',source:'field',fileName:'a.jpg',blobId:'photo',notes:'',deleted:true},'phone'));
  const result=await exportField({state,blobs:[{id:'photo',blob:new Blob([bytes],{type:'image/jpeg'})}]},'trip',true);
  const files=unpack(result.bytes),decoded=await decodeField(files);
  assert.deepEqual(decoded.packet.records,state.records);assert.equal(decoded.blobs[0].sha256,hash);
  assert(files['HANDOFF.md']);assert(files['contact-sheet.html']);assert(files['catalogue.csv']);
  files['photos/photo.jpg']=new Uint8Array([9]);
  await assert.rejects(decodeField(files),/checksum/);
  assert.throws(()=>unpack(zipSync({'../oops':strToU8('x')})),/Unsafe/);
  assert.throws(()=>unpack(zipSync({'huge':new Uint8Array(MAX_PACKAGE+1)})),/Expanded/);
  assert.equal(csvCell('=evil'),'"\'=evil"');
});
test('solar estimates use the capture coordinates and local date, including the date line',()=>{
  for(const [lat,lng,tz]of [[22.3,114.17,'Asia/Hong_Kong'],[40.7,-74,'America/New_York'],[-13.8,-171.8,'Pacific/Apia']]){
    const d=daylight('2026-10-03',lat,lng,tz);assert(d?.rise&&d?.set);
    const local=new Intl.DateTimeFormat('en-CA',{timeZone:tz,year:'numeric',month:'2-digit',day:'2-digit'});
    assert.equal(local.format(new Date(d.rise)),'2026-10-03');
    assert.equal(local.format(new Date(d.set)),'2026-10-03');
    assert(Date.parse(d.set)>Date.parse(d.rise));
  }
  assert(daylight('2026-06-21',89,0,'UTC').polar.includes('above'));
});
test('multipart handoffs restore a complete trip without repeating shared context',async()=>{
  const state=initial();
  for(let i=0;i<101;i++)state.records.push(record('asset',{tripId:'trip',setId:'set',source:'camera',fileName:i+'.arw',notes:''},'desktop'));
  const parts=await exportTrip({state,blobs:[]},'trip',true);assert.equal(parts.length,2);
  let restored=blank();
  for(const part of parts){const data=await decodeField(unpack(part.bytes));restored=merge(restored,data.packet.records,data.packet.packageId).state;}
  assert.equal(restored.records.length,state.records.length);assert.equal(restored.conflicts.length,0);
});
