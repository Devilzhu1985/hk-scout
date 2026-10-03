export const coordinateName=location=>location.lat.toFixed(5)+', '+location.lng.toFixed(5);
export function automaticPlaceChanges(current,fix,place){
  // A delayed lookup must not replace a newer fix, a manual coordinate, or a
  // location/subject that the photographer has already edited.
  if(current.deleted||current.location?.capturedAt!==fix.capturedAt||current.location?.method!=='gps')return {};
  const updates={};
  if(place?.name&&typeof place.name==='string'){
    updates.placeLookup={name:place.name.slice(0,300),city:String(place.city||'').slice(0,200),source:place.source,approximate:true};
    if(current.autoName)updates.name=updates.placeLookup.name;
    if(!current.city&&place.city)updates.city=updates.placeLookup.city;
  }
  return updates;
}
export async function lookupBrowserArea(fix,language){
  // This free service permits ONLY current coordinates obtained on this device.
  // Never look up imported/manual/history points and never fall back to IP.
  if(fix.method!=='gps'||Math.abs(Date.now()-Date.parse(fix.capturedAt))>30000)return null;
  const params=new URLSearchParams({latitude:String(fix.lat),longitude:String(fix.lng),localityLanguage:language==='zh'?'zh':'en'});
  const response=await fetch('https://api.bigdatacloud.net/data/reverse-geocode-client?'+params,{signal:AbortSignal.timeout(7000)});
  if(!response.ok)return null;const data=await response.json();
  const name=data.locality||data.city;if(typeof name!=='string'||!name)return null;
  return {name,city:typeof data.city==='string'?data.city:'',source:'BigDataCloud',approximate:true};
}
