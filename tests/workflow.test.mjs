import test from 'node:test';
import assert from 'node:assert/strict';
import {h,t,setLanguage} from '../src/i18n.js';
import {chooseUpdate,newerVersion} from '../src/updates.js';
import {automaticPlaceChanges,coordinateName,lookupBrowserArea} from '../src/location.js';
import {preparePhotos} from '../src/photos.js';
import {blank,record,validateRecords} from '../src/model.js';
import {exportTrip,decodeField,unpack,sha256} from '../src/packets.js';
import {places,days} from '../src/hk-template.js';

test('localization preserves substitutions, user text, IDs, booleans, and code',()=>{
 setLanguage('zh');
 assert.equal(h`<h2>Reference images</h2><p>${'Open'}</p><input ${'disabled'} value="${'Field'}">`,'<h2>参考图片</h2><p>Open</p><input disabled value="Field">');
 assert.equal(h('<pre>Open</pre><label>Method</label>'),'<pre>Open</pre><label>方法</label>');
 assert.equal(h`<option value="${'lighting'}">${t('lighting')}</option>`,'<option value="lighting">光照</option>');
 assert.equal(h`<h2>Conflicts · ${3}</h2>`,'<h2>冲突 · 3</h2>'); // Slot numbering can change as the layout evolves.
 setLanguage('en');assert.equal(h`<p>Open</p><p>${'中文笔记'}</p>`,'<p>Open</p><p>中文笔记</p>');
});
test('Hong Kong itinerary has English names, descriptions and day titles',()=>{
 assert.equal(places.length,45);for(const p of places){assert(p.en&&p.descriptionEn&&p.n&&p.d);}for(const d of days)assert(d.en&&d.t);
});
test('updates require newer semantic version, expected repository APK URL and checksum',()=>{
 const release={tag_name:'v2.2.0-preview.1',prerelease:true,assets:[{name:'scout-2.2.0-debug.apk',browser_download_url:'https://github.com/Devilzhu1985/hk-scout/releases/download/v2.2.0-preview.1/scout-2.2.0-debug.apk',digest:'sha256:'+'a'.repeat(64),size:100}]};
 assert.equal(chooseUpdate([release]).sha256,'a'.repeat(64));
 assert.equal(chooseUpdate([{...release,draft:true}]),null);
 assert.equal(chooseUpdate([{...release,tag_name:'v2.1.0-preview.1'}]),null);
 for(const changes of [{digest:null},{browser_download_url:'https://example.com/scout-malicious.apk'}])assert.equal(chooseUpdate([{...release,assets:[{...release.assets[0],...changes}]}]),null);
 assert(newerVersion('v2.10.0-preview.1','2.9.9'));assert(!newerVersion('v2.1.0-preview.9'));assert(!newerVersion('invalid'));
});
test('late place lookup respects edited names, newer GPS and manually entered coordinates',()=>{
 const fix={lat:22.3193,lng:114.1694,capturedAt:new Date().toISOString(),method:'gps'};
 const place={name:'Nearby street',city:'Hong Kong',source:'Fixture'};
 const base={name:coordinateName(fix),autoName:true,location:fix,city:''};
 assert.equal(automaticPlaceChanges(base,fix,place).name,'Nearby street');
 assert.equal(automaticPlaceChanges({...base,autoName:false,name:'My subject'},fix,place).name,undefined);
 assert.deepEqual(automaticPlaceChanges({...base,location:{...fix,method:'manual'}},fix,place),{});
 assert.deepEqual(automaticPlaceChanges({...base,location:{...fix,capturedAt:'newer'}},fix,place),{});
});
test('browser area lookup never sends manual or stale coordinates and never uses IP fallback',async()=>{
 const old=globalThis.fetch;let calls=0;globalThis.fetch=async()=>{calls++;return {ok:true,json:async()=>({locality:'Fixture area',city:'Fixture city'})};};
 try{assert.equal(await lookupBrowserArea({method:'manual'},'en'),null);assert.equal(await lookupBrowserArea({method:'gps',capturedAt:'2000-01-01T00:00:00Z'},'en'),null);assert.equal(calls,0);assert.equal((await lookupBrowserArea({method:'gps',capturedAt:new Date().toISOString(),lat:1,lng:2},'en')).name,'Fixture area');assert.equal(calls,1);}finally{globalThis.fetch=old;}
});
test('DNG original survives preparation, backup and restoration byte-for-byte without a decoder',async()=>{
 const bytes=new Uint8Array([73,73,42,0,8,0,0,0,11,22,33]); // synthetic transport fixture, not a valid physical RAW capture
 const file=new File([bytes],'reference.dng',{type:'image/x-adobe-dng'}),prepared=await preparePhotos([file]);
 assert.equal(prepared.blobId,null);assert.equal(prepared.originals[0].sha256,await sha256(bytes));
 const state=blank();state.records=[record('trip',{name:'Original test',city:'',timezone:'UTC',template:'blank'},'test','trip'),record('set',{tripId:'trip',code:'S-TEST',name:'Test',city:'',timezone:'UTC',notes:'',startedAt:new Date().toISOString(),endedAt:null,camera:{name:'Test'}},'test','set'),record('asset',{tripId:'trip',setId:'set',fileName:file.name,source:'field',notes:'',blobId:null,originals:prepared.originals},'test','asset')];
 const [packet]=await exportTrip({state,blobs:prepared.blobs},'trip');const archive=unpack(packet.bytes),restored=await decodeField(archive);
 assert.deepEqual(new Uint8Array(await restored.blobs[0].blob.arrayBuffer()),bytes);
 const damaged=structuredClone(state.records);damaged.at(-1).originals[0].blobId='../escape';assert.throws(()=>validateRecords(damaged));
 const json=JSON.parse(new TextDecoder().decode(archive['field.json']));json.records.at(-1).originals[0].sha256='a'.repeat(64);archive['field.json']=new TextEncoder().encode(JSON.stringify(json));await assert.rejects(()=>decodeField(archive),/manifest/);
});
