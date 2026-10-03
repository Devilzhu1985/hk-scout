// Flat incident-meter convention C=250 at ISO 100: EV100 = log2(lux / 2.5).
// A phone is not a calibrated incident meter. This is a display estimate only;
// the persisted record retains the measured lux, instrument and protocol.
// Reference: https://sekonic.com/content/Files/manual/L-358/L-358_operating_manual_en.pdf
export function slateLight(reading){
  if(!reading||!Number.isFinite(reading.value))return null;
  if(['phone_lux','external_lux'].includes(reading.method)){
    if(reading.value<0)return null;
    return {lux:reading.value,ev:reading.value>0?Math.log2(reading.value/2.5):null,estimated:true};
  }
  if(['camera_ev','manual_ev'].includes(reading.method))return {lux:null,ev:reading.value,estimated:false};
  return null;
}
export function latestSlateReading(records,setId){
  return records.filter(r=>r.kind==='reading'&&r.setId===setId&&!r.deleted&&slateLight(r)&&Number.isFinite(Date.parse(r.measuredAt)))
    .sort((a,b)=>b.measuredAt.localeCompare(a.measuredAt))[0]||null;
}
export function slateIdentity(set){return {app:'scout',v:2,tripId:set.tripId,setId:set.id,code:set.code};}
