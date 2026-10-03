import test from 'node:test';
import assert from 'node:assert/strict';
import {slateLight,latestSlateReading,slateIdentity} from '../src/slate.js';

test('slate distinguishes lux-derived estimates from entered EV, including zero and negative EV',()=>{
 assert.deepEqual(slateLight({method:'phone_lux',value:320}),{lux:320,ev:7,estimated:true});
 assert.equal(slateLight({method:'external_lux',value:640}).ev,8);
 assert.deepEqual(slateLight({method:'camera_ev',value:0}),{lux:null,ev:0,estimated:false});
 assert.deepEqual(slateLight({method:'manual_ev',value:-2}),{lux:null,ev:-2,estimated:false});
 assert.deepEqual(slateLight({method:'phone_lux',value:0}),{lux:0,ev:null,estimated:true});
 for(const value of [-1,NaN,Infinity])assert.equal(slateLight({method:'phone_lux',value}),null);
 assert.equal(slateLight({method:'exposure_compensation',value:2}),null);
});
test('slate uses the latest active reading of this stop only',()=>{
 const r={kind:'reading',setId:'here',method:'phone_lux',value:320,measuredAt:'2026-10-03T10:00:00Z'};
 const newer={...r,id:'newer',method:'camera_ev',value:8,measuredAt:'2026-10-03T11:00:00Z'};
 const records=[{...r,id:'old'},newer,{...r,setId:'elsewhere',measuredAt:'2026-10-03T12:00:00Z'},{...r,deleted:true,measuredAt:'2026-10-03T13:00:00Z'},{...r,measuredAt:'invalid'}];
 assert.equal(latestSlateReading(records,'here').id,'newer');
 assert.equal(latestSlateReading(records,'missing'),null);
 assert.equal(records[0].id,'old');
});
test('QR carries stable readable identity, not a changing measurement or an upload link',()=>{
 assert.deepEqual(slateIdentity({id:'set-123',tripId:'trip-456',code:'S-123',name:'Place'}),{app:'scout',v:2,tripId:'trip-456',setId:'set-123',code:'S-123'});
});
