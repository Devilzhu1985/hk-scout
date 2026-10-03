import {places,days} from './hk-template.js';

// Keys identify a visit in the original itinerary, not just a physical place.
// Keep v1 keys stable if a future template introduces a different arrangement.
export const itineraryKey=(day,slot,placeId)=>`hk-v1:${day}:${slot}:${placeId}`;
export function itineraryStops(language='en'){
 return days.flatMap((day,d)=>day.slots.flatMap(([slot,ids],s)=>ids.map(id=>{
  const place=places.find(p=>p.id===id);
  return {key:itineraryKey(d,s,id),plannedStopKey:itineraryKey(d,s,id),name:language==='zh'?place.n:place.en,city:'Hong Kong',timezone:'Asia/Hong_Kong',query:place.q+', Hong Kong',group:(language==='zh'?day.t:day.en)+' · '+(language==='zh'?slot:({'白天':'Daytime','傍晚':'Dusk','夜晚':'Night','有空就补':'Flexible additions'}[slot]||slot)),location:null};
 })));
}
export function nextStopChoices(trip,sets,language='en'){
 if(!trip)return [];
 return [...(trip.template==='hongkong'?itineraryStops(language):[]),...sets.filter(s=>s.tripId===trip.id&&!s.deleted).map(s=>({key:'record:'+s.id,recordId:s.id,name:s.name,city:s.city,timezone:s.timezone,location:s.location,plannedStopKey:s.plannedStopKey||null,group:language==='zh'?'去过的地点':'Recorded stops'}))];
}
export function destinationQuery(target){
 const p=target?.location;
 if(p&&Number.isFinite(p.lat)&&Math.abs(p.lat)<=90&&Number.isFinite(p.lng)&&Math.abs(p.lng)<=180)return `${p.lat},${p.lng}`;
 const query=(target?.query||[target?.name,target?.city].filter(Boolean).join(', ')).trim();
 if(!query||query.length>600)throw Error('Choose a destination first.');
 return query;
}
export function directionsURL(target,mode='walking'){
 if(!['walking','transit','driving'].includes(mode))throw Error('Unsupported travel mode');
 // Omit origin deliberately: Maps obtains the traveller's current location.
 // A saved photograph's GPS must never become a purported live starting point.
 const params=new URLSearchParams({api:'1',destination:destinationQuery(target),travelmode:mode});
 return 'https://www.google.com/maps/dir/?'+params.toString();
}
export function ownOpenSets(state,deviceId){
 return state.records.filter(s=>s.kind==='set'&&!s.deleted&&!s.endedAt&&(s.createdDevice||s.device)===deviceId);
}
export function amapSearchURL(target){
 const p=target.location;
 if(p&&Number.isFinite(p.lat)&&Math.abs(p.lat)<=90&&Number.isFinite(p.lng)&&Math.abs(p.lng)<=180)return 'https://uri.amap.com/marker?'+new URLSearchParams({position:`${p.lng},${p.lat}`,name:target.name||'',coordinate:'wgs84',src:'Scout',callnative:'1'});
 // Name search avoids passing WGS84 capture coordinates as GCJ-02 map points.
 const query=(target.query||[target.name,target.city].filter(Boolean).join(' ')).trim();
 if(!query||query.length>600)throw Error('Choose a destination first.');
 return 'https://uri.amap.com/search?'+new URLSearchParams({keyword:query,city:target.city||'',src:'Scout',callnative:'1'});
}
