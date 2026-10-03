import test from 'node:test';
import assert from 'node:assert/strict';
import {lookupWeather,parseWeather,validWeather,automaticWeatherChanges} from '../src/weather.js';
import {blank,record,validateRecords} from '../src/model.js';
import {exportTrip,decodeField,unpack} from '../src/packets.js';
const at=Date.parse('2026-10-03T12:00:00Z'),fix={lat:22.3193,lng:114.1694,method:'gps',capturedAt:new Date(at).toISOString()};
const response={latitude:22.3,longitude:114.2,timezone:'Asia/Hong_Kong',current_units:{time:'unixtime',temperature_2m:'°C',cloud_cover:'%',wind_speed_10m:'km/h',precipitation:'mm'},current:{time:at/1000,interval:900,weather_code:3,temperature_2m:27,cloud_cover:85,wind_speed_10m:12,precipitation:0}};
test('weather validates source timestamps, explicit units and nullable data without inventing zeros',()=>{
 const weather=parseWeather(response,fix,at);assert(validWeather(weather));assert.equal(weather.validAt,fix.capturedAt);assert.equal(weather.precipitationMm,0);assert.deepEqual(weather.requestedLocation,{lat:22.32,lng:114.17});
 assert.equal(parseWeather({...response,current:{...response.current,temperature_2m:null}},fix,at).temperatureC,null);
 assert.equal(parseWeather({...response,current:{...response.current,time:at/1000-7200}},fix,at),null);
 assert.equal(parseWeather({...response,current_units:{...response.current_units,temperature_2m:'°F'}},fix,at),null);
 assert.equal(parseWeather({...response,current:{...response.current,cloud_cover:300}},fix,at),null);
});
test('weather never overwrites a saved snapshot, closed/historical stop or newer GPS',()=>{
 const weather=parseWeather(response,fix,at),current={startedAt:fix.capturedAt,location:fix};
 assert.deepEqual(automaticWeatherChanges(current,fix,weather),{weather});
 assert.deepEqual(automaticWeatherChanges({...current,autoTimezone:true,timezone:'America/Los_Angeles'},fix,weather),{weather,timezone:'Asia/Hong_Kong'});
 assert.deepEqual(automaticWeatherChanges({...current,autoTimezone:false,timezone:'UTC'},fix,weather),{weather});
 for(const changed of [{weather},{endedAt:fix.capturedAt},{deleted:true},{startedAt:'2020-01-01T00:00:00Z'},{location:{...fix,capturedAt:'2026-10-03T12:01:00Z'}},{location:{...fix,method:'manual'}}])assert.deepEqual(automaticWeatherChanges({...current,...changed},fix,weather),{});
 assert.deepEqual(automaticWeatherChanges(current,fix,{...weather,fixAt:'2026-10-03T12:01:00Z'}),{});
});
test('weather request sends rounded fresh GPS only, with no stop IDs or IP fallback',async()=>{
 const old=globalThis.fetch;let url=null;globalThis.fetch=async u=>{url=new URL(u);return {ok:true,json:async()=>({...response,current:{...response.current,time:Date.now()/1000}})};};
 try{
  for(const f of [{...fix,capturedAt:new Date(Date.now()-60000).toISOString()},{...fix,capturedAt:'invalid'},{...fix,method:'manual'},{...fix,lat:NaN}])assert.equal(await lookupWeather(f),null);
  assert.equal(url,null);await lookupWeather({...fix,capturedAt:new Date().toISOString()});
  assert.equal(url.origin,'https://api.open-meteo.com');assert.equal(url.searchParams.get('latitude'),'22.32');assert.equal(url.searchParams.get('longitude'),'114.17');assert.equal(url.searchParams.has('setId'),false);
 }finally{globalThis.fetch=old;}
});
test('weather survives a field backup and validates on import',async()=>{
 const state=blank();state.records=[record('trip',{name:'Weather test',city:'',timezone:'UTC',template:'blank'},'test','trip'),record('set',{tripId:'trip',code:'S-WEATHER',name:'Stop',city:'',timezone:'UTC',notes:'Actual local observation',startedAt:fix.capturedAt,endedAt:null,camera:{name:'Sony'},location:fix,weather:parseWeather(response,fix,at)},'test','stop')];
 const [packet]=await exportTrip({state,blobs:[]},'trip');const restored=await decodeField(unpack(packet.bytes));assert.deepEqual(restored.packet.records.find(r=>r.id==='stop').weather,state.records[1].weather);
 const broken=structuredClone(state.records);broken[1].weather.kind='on-site-measurement';assert.throws(()=>validateRecords(broken),/weather/);
});
