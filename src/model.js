import {validWeather} from './weather.js';
export const VERSION = 2;
export const uid = () => crypto.randomUUID();
export const now = () => new Date().toISOString();
export const kinds = ['trip', 'set', 'reading', 'asset', 'clock'];
export const methods = ['phone_lux', 'external_lux', 'camera_ev', 'manual_ev'];
export const blank = () => ({schemaVersion: VERSION, records: [], conflicts: [], packages: [], legacy: [], generation: 0});
export const active = (state, kind) => state.records.filter(r => !r.deleted && r.kind === kind);
export const get = (state, id) => state.records.find(r => r.id === id);
export function record(kind, data, device, id = uid()) {
  return {id, kind, revision: uid(), ancestors: [], updatedAt: now(), device, deleted: false, ...data};
}
export function revise(r, changes, device) {
  return {...r, ...changes, id: r.id, kind: r.kind, revision: uid(), ancestors: [...new Set([...r.ancestors, r.revision])], updatedAt: now(), device};
}
export function put(state, r) {
  const i = state.records.findIndex(x => x.id === r.id);
  if (i < 0) state.records.push(r); else state.records[i] = r;
}
export function timezoneValid(tz) { try { new Intl.DateTimeFormat('en', {timeZone: tz}).format(); return !!tz; } catch { return false; } }
export function localTime(value, tz, seconds = false, locale = 'en-GB') {
  if (!value) return 'Unknown';
  try { return new Intl.DateTimeFormat(locale, {timeZone: tz, dateStyle: 'medium', timeStyle: seconds ? 'medium' : 'short'}).format(new Date(value)); } catch { return 'Unknown'; }
}
export function ev100(aperture, shutter, iso) {
  return [aperture, shutter, iso].every(v => Number.isFinite(v) && v > 0) ? Math.log2(aperture ** 2 / shutter) - Math.log2(iso / 100) : null;
}
export function shutterSeconds(v) {
  const s = String(v).trim();
  if (/^\d+(\.\d+)?\/\d+(\.\d+)?$/.test(s)) { const [a,b] = s.split('/').map(Number); return b > 0 ? a/b : null; }
  const n = Number(s); return n > 0 && Number.isFinite(n) ? n : null;
}
export function summarize(samples) {
  const values = samples.filter(v => Number.isFinite(v) && v >= 0);
  if (!values.length) throw Error('No valid sensor readings were received.');
  const mean = values.reduce((a,b) => a+b,0)/values.length;
  return {value: mean, min: Math.min(...values), max: Math.max(...values), count: values.length, variation: mean ? (Math.max(...values)-Math.min(...values))/mean : null};
}
export function comparable(a,b) {
  return !a.deleted && !b.deleted && a.method === b.method && a.instrument === b.instrument && a.protocol === b.protocol && a.calibration === b.calibration && ['phone_lux','external_lux'].includes(a.method) && a.value > 0 && b.value > 0;
}
function assert(ok, message) { if (!ok) throw Error(message); }
function text(v,max=4000) { return typeof v === 'string' && v.length <= max; }
function date(v) { return typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T.*Z$/.test(v) && Number.isFinite(Date.parse(v)); }
function safeJSON(value, depth=0) {
  assert(depth < 30, 'Package nesting is too deep.');
  if (!value || typeof value !== 'object') return;
  for (const key of Object.keys(value)) { assert(!['__proto__','constructor','prototype'].includes(key), 'Unsafe property in package.'); safeJSON(value[key], depth+1); }
}
export function validateRecords(records) {
  assert(Array.isArray(records) && records.length <= 50000, 'Invalid record list.');
  safeJSON(records);
  const ids = new Set();
  for (const r of records) {
    assert(r && text(r.id,180) && r.id && !ids.has(r.id), 'Duplicate or invalid record ID.'); ids.add(r.id);
    assert(kinds.includes(r.kind) && text(r.revision,180) && r.revision && date(r.updatedAt), 'Invalid record revision.');
    assert(Array.isArray(r.ancestors) && r.ancestors.length <= 10000 && r.ancestors.every(x=>text(x,180)) && !r.ancestors.includes(r.revision), 'Invalid revision ancestry.');
    assert(typeof r.deleted === 'boolean' && text(r.device,180), 'Invalid device or deletion marker.');
    if (r.kind === 'trip') assert(text(r.name,200) && text(r.city,200) && timezoneValid(r.timezone) && ['hongkong','blank'].includes(r.template), 'Invalid trip.');
    else assert(text(r.tripId,180) && r.tripId, 'Missing trip reference.');
    if (r.kind === 'set') {
      assert(text(r.code,100) && text(r.name,300) && date(r.startedAt) && (!r.endedAt || date(r.endedAt) && Date.parse(r.endedAt)>=Date.parse(r.startedAt)), 'Invalid photo-set interval.');
      assert(timezoneValid(r.timezone) && text(r.notes ?? '') && r.camera && text(r.camera.name,200), 'Invalid photo set.');
      if (r.location) assert(Number.isFinite(r.location.lat) && Math.abs(r.location.lat)<=90 && Number.isFinite(r.location.lng) && Math.abs(r.location.lng)<=180 && (r.location.accuracy == null || Number.isFinite(r.location.accuracy) && r.location.accuracy >= 0), 'Invalid coordinates.');
      if(r.weather!=null)assert(validWeather(r.weather),'Invalid weather snapshot.');
      if(r.autoTimezone!==undefined)assert(typeof r.autoTimezone==='boolean','Invalid automatic time zone preference.');
      if(r.plannedStopKey!=null)assert(text(r.plannedStopKey,180)&&/^hk-v1:\d+:\d+:[a-z0-9_]+$/.test(r.plannedStopKey),'Invalid itinerary visit reference.');
    }
    if (r.kind === 'reading') {
      assert(methods.includes(r.method) && Number.isFinite(r.value) && (r.method.endsWith('lux') ? r.value>=0 : true), 'Invalid lighting reading.');
      assert(text(r.setId,180) && text(r.instrument,200) && text(r.protocol,500) && text(r.calibration,200) && date(r.measuredAt), 'Incomplete measurement provenance.');
    }
    if (r.kind === 'asset') {
      assert(text(r.fileName,500) && ['field','camera'].includes(r.source) && (!r.setId || text(r.setId,180)) && text(r.notes ?? ''), 'Invalid image record.');
      if(r.originals!==undefined){
        assert(Array.isArray(r.originals)&&r.originals.length<=2,'Invalid original image list.');
        const ids=new Set();
        for(const o of r.originals){assert(o&&/^[\w-]{1,180}$/.test(o.blobId)&&!ids.has(o.blobId)&&text(o.fileName,500)&&/^[a-f0-9]{64}$/.test(o.sha256)&&['image/jpeg','image/png','image/webp','image/heic','image/heif','image/x-adobe-dng'].includes(o.mime)&&Number.isSafeInteger(o.size)&&o.size>=0&&o.size<=56*1024*1024,'Invalid original image.');ids.add(o.blobId);}
      }
    }
    if (r.kind === 'clock') assert(text(r.camera,200) && date(r.from) && date(r.to) && Date.parse(r.from)<=Date.parse(r.to) && Number.isFinite(r.offsetSeconds) && Math.abs(r.offsetSeconds)<=172800 && Number.isInteger(r.utcOffsetMinutes) && Math.abs(r.utcOffsetMinutes)<=840, 'Invalid camera clock segment.');
  }
  return records;
}
export function validateReferences(records) {
  const map = new Map(records.map(r=>[r.id,r]));
  for (const r of records) {
    if (r.kind !== 'trip') assert(map.get(r.tripId)?.kind === 'trip', 'A record refers to a missing trip.');
    if (r.setId) assert(map.get(r.setId)?.kind === 'set' && map.get(r.setId).tripId === r.tripId, 'A record refers to a missing or different-trip photo set.');
  }
}
export function merge(state, incoming, packageId) {
  validateRecords(incoming);
  const result = structuredClone(state);
  if (result.packages.includes(packageId)) return {state: result, added: 0, updated: 0, conflicts: 0, duplicate: true};
  let added=0,updated=0,conflicts=0;
  for (const r of incoming) {
    const existing = get(result,r.id);
    if (!existing) { put(result,r); added++; }
    else if (r.revision === existing.revision) {
      assert(JSON.stringify(r) === JSON.stringify(existing) || canonical(r) === canonical(existing), 'Same revision contains different content.');
    } else if (existing.ancestors.includes(r.revision)) { /* old package cannot undo newer records or deletions */ }
    else if (r.ancestors.includes(existing.revision)) { put(result,r); updated++; }
    else if (!result.conflicts.some(c=>c.incoming.id === r.id && c.incoming.revision === r.revision)) {
      result.conflicts.push({id:uid(), incoming:r}); conflicts++;
    }
  }
  validateReferences(result.records);
  result.packages.push(packageId);
  return {state:result,added,updated,conflicts,duplicate:false};
}
export function canonical(v) { return JSON.stringify(v,(_,x)=>x && typeof x==='object' && !Array.isArray(x) ? Object.fromEntries(Object.keys(x).sort().map(k=>[k,x[k]])) : x); }
export function resolve(state, conflictId, useIncoming, device) {
  const c = state.conflicts.find(x=>x.id===conflictId); if (!c) throw Error('This conflict was already resolved.');
  const local=get(state,c.incoming.id); const chosen=useIncoming?c.incoming:local;
  put(state,{...chosen,revision:uid(),ancestors:[...new Set([...local.ancestors,local.revision,...c.incoming.ancestors,c.incoming.revision])],updatedAt:now(),device});
  state.conflicts=state.conflicts.filter(x=>x.id!==conflictId);
}
// Raw camera wall time is intentionally interpreted only using an explicit clock segment.
export function cameraTime(asset, clocks) {
  if (!asset.captureWall) return {time:null,reason:'No capture timestamp'};
  const wall=Date.parse(asset.captureWall+'Z');
  if (!Number.isFinite(wall)) return {time:null,reason:'Unreadable capture timestamp'};
  const matches=clocks.filter(c=>!c.deleted && c.camera===asset.camera && wall>=Date.parse(c.from) && wall<=Date.parse(c.to));
  if (matches.length !== 1) return {time:null,reason:matches.length?'Overlapping clock segments':'Add a camera clock segment'};
  const c=matches[0];
  return {time:new Date(wall-c.utcOffsetMinutes*60000+c.offsetSeconds*1000).toISOString(),reason:'Clock-corrected suggestion'};
}
export function suggest(asset, sets, clocks) {
  const result=cameraTime(asset,clocks); if (!result.time) return {...result,sets:[]};
  const t=Date.parse(result.time);
  return {...result,sets:sets.filter(s=>!s.deleted && s.tripId===asset.tripId && s.endedAt && s.camera.name===asset.camera && t>=Date.parse(s.startedAt) && t<=Date.parse(s.endedAt))};
}
export function migrateLegacy(old, device='legacy-v1') {
  device='legacy-v1';
  if (!old || old.v !== 1 || !Array.isArray(old.points)) throw Error('Unsupported legacy backup.');
  const trip=record('trip',{name:'Hong Kong · imported records',city:'Hong Kong',timezone:'Asia/Hong_Kong',template:'hongkong'},device,'legacy-hk');
  trip.revision='legacy-hk-v1'; trip.updatedAt='2026-01-01T00:00:00.000Z';
  const records=[trip];
  for (const p of old.points) {
    const id='legacy-set-'+p.id;
    const s=record('set',{tripId:trip.id,name:p.place||p.code||'Imported stop',code:p.code||p.id,startedAt:new Date(p.t).toISOString(),endedAt:new Date(p.t).toISOString(),timezone:'Asia/Hong_Kong',city:'Hong Kong',camera:{name:'Legacy camera',aperture:null,wb:null,iso:p.iso||'',shutter:p.shutter||''},location:p.lat!=null&&p.lng!=null?{lat:p.lat,lng:p.lng,accuracy:p.acc??null,capturedAt:null,method:'legacy'}:null,notes:[p.notes,'Historical aperture / white balance were global and cannot be established.',p.colors?.length?'Legacy color notes: '+JSON.stringify(p.colors):'',p.weather?.length?'Weather: '+p.weather.join(', '):''].filter(Boolean).join('\n'),mode:'quick',tags:[],legacyPoint:p},device,id);
    if(s.notes.length>4000)s.notes=s.notes.slice(0,3850)+'\nFull original notes preserved in legacyPoint and the v1 archive.';
    s.revision='legacy-'+p.id+'-v1'; s.updatedAt=new Date(p.t).toISOString(); records.push(s);
    for (const [zone,v] of Object.entries(p.lux||{})) if (v!=='' && Number.isFinite(Number(v)) && Number(v)>=0) {
      const r=record('reading',{tripId:trip.id,setId:id,label:zone,value:Number(v),method:'external_lux',instrument:'Legacy source unknown',protocol:'Unknown',calibration:'Unverified legacy entry',measuredAt:s.startedAt},device,'legacy-light-'+p.id+'-'+zone);
      r.revision=r.id+'-v1'; r.updatedAt=s.startedAt; records.push(r);
    }
  }
  return records;
}
