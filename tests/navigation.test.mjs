import test from 'node:test';
import assert from 'node:assert/strict';
import {itineraryStops,nextStopChoices,directionsURL,amapSearchURL,ownOpenSets} from '../src/navigation.js';
import {blank,record,validateRecords} from '../src/model.js';
import {exportTrip,decodeField,unpack} from '../src/packets.js';

test('planned visits distinguish dusk and night, with stable keys across languages',()=>{
 const en=itineraryStops(),zh=itineraryStops('zh');assert.deepEqual(en.map(s=>s.key),zh.map(s=>s.key));
 assert.equal(new Set(en.map(s=>s.key)).size,en.length);
 const visits=en.filter(s=>s.name==='Apliu Street');assert.equal(visits.length,2);assert.notEqual(visits[0].key,visits[1].key);
 assert.match(visits[0].group,/Dusk/);assert.match(visits[1].group,/Night/);
});
test('directions encode destinations and modes without an old GPS origin or hidden requests',()=>{
 for(const mode of ['walking','transit','driving']){const url=new URL(directionsURL({name:'A & B / 茶餐厅',city:'Hong Kong'},mode));assert.equal(url.searchParams.get('destination'),'A & B / 茶餐厅, Hong Kong');assert.equal(url.searchParams.get('travelmode'),mode);assert(!url.searchParams.has('origin'));}
 assert.equal(new URL(directionsURL({name:'Old photo',location:{lat:22.3,lng:114.17}})).searchParams.get('destination'),'22.3,114.17');
 assert.throws(()=>directionsURL({},'walking'));assert.throws(()=>directionsURL({name:'x'},'teleport'));
});
test('Amap gets explicit WGS84 marker coordinates, or city-qualified name search',()=>{
 const marker=new URL(amapSearchURL({name:'Reference',location:{lat:31.23,lng:121.47}}));assert.equal(marker.pathname,'/marker');assert.equal(marker.searchParams.get('coordinate'),'wgs84');assert.equal(marker.searchParams.get('position'),'121.47,31.23');
 const search=new URL(amapSearchURL({name:'外滩',city:'上海'}));assert.equal(search.pathname,'/search');assert.equal(search.searchParams.get('city'),'上海');assert.equal(search.searchParams.get('keyword'),'外滩 上海');assert(!search.searchParams.has('center'));
});
test('choices stay in their trip while unfinished-capture protection spans own trips only',()=>{
 const sets=[{id:'a',kind:'set',tripId:'one',createdDevice:'phone',name:'A'},{id:'b',kind:'set',tripId:'two',createdDevice:'phone',name:'B'},{id:'c',kind:'set',tripId:'one',createdDevice:'other',name:'C'},{id:'d',kind:'set',tripId:'one',createdDevice:'phone',name:'D',deleted:true}];
 assert.deepEqual(nextStopChoices({id:'one',template:'blank'},sets).map(x=>x.recordId),['a','c']);assert.deepEqual(ownOpenSets({records:sets},'phone').map(s=>s.id),['a','b']);
});
test('itinerary association survives export/import without changing original capture identity',async()=>{
 const state=blank();state.records=[record('trip',{name:'HK',city:'Hong Kong',timezone:'Asia/Hong_Kong',template:'hongkong'},'phone','trip'),record('set',{tripId:'trip',name:'Apliu',code:'S-123',timezone:'Asia/Hong_Kong',startedAt:new Date().toISOString(),endedAt:null,notes:'',camera:{name:'Sony'},plannedStopKey:itineraryStops()[3].key},'phone','capture')];
 validateRecords(state.records);const [part]=await exportTrip({state,blobs:[]},'trip');const restored=await decodeField(unpack(part.bytes));const set=restored.packet.records.find(r=>r.kind==='set');assert.equal(set.id,'capture');assert.equal(set.plannedStopKey,state.records[1].plannedStopKey);
 const bad=structuredClone(state.records);bad[1].plannedStopKey={};assert.throws(()=>validateRecords(bad));
});
