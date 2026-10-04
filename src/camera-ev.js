export function cameraEV100(m){
 if(!m||!Number.isFinite(m.aperture)||m.aperture<=0||!Number.isSafeInteger(m.exposureNs)||m.exposureNs<=0||!Number.isInteger(m.iso)||m.iso<=0||!Number.isInteger(m.postRawBoost)||m.postRawBoost<=0)return null;
 return Math.log2(m.aperture*m.aperture/(m.exposureNs/1e9))-Math.log2(m.iso*m.postRawBoost/10000);
}
export function validCameraMeter(m,value){
 const calculated=cameraEV100(m),sample=m?.samples;
 return calculated!==null&&Number.isFinite(value)&&Math.abs(calculated-value)<0.0001&&m.source==='android_camera2_ae'&&
  typeof m.cameraId==='string'&&m.cameraId.length>0&&m.cameraId.length<=100&&typeof m.device==='string'&&m.device.length<=200&&
  m.aeState===2&&m.compensationSteps===0&&['center_requested','whole_frame'].includes(m.metering)&&
  /^\d{1,24}$/.test(m.frameTimestampNs)&&Number.isFinite(m.previewClippedFraction)&&m.previewClippedFraction>=0&&m.previewClippedFraction<=0.2&&
  sample&&Number.isInteger(sample.count)&&sample.count>=6&&sample.count<=100&&Number.isFinite(sample.windowMs)&&sample.windowMs>=600&&sample.windowMs<=2000&&
  Number.isFinite(sample.minEv)&&Number.isFinite(sample.maxEv)&&sample.maxEv>=sample.minEv&&sample.maxEv-sample.minEv<=0.25001&&value>=sample.minEv-0.0001&&value<=sample.maxEv+0.0001;
}
export function cameraReading(result,{tripId,setId,label}){
 const {measuredAt,ev100,...cameraMeter}=result||{};
 if(!validCameraMeter(cameraMeter,ev100)||typeof measuredAt!=='string'||!/^\d{4}-\d{2}-\d{2}T.*Z$/.test(measuredAt)||!Number.isFinite(Date.parse(measuredAt))||Math.abs(Date.now()-Date.parse(measuredAt))>30000)throw Error('No fresh, stable camera exposure was returned. Try again.');
 return {tripId,setId,label,method:'camera_ev',value:ev100,measuredAt,cameraMeter,
  instrument:cameraMeter.device+' / Camera2 rear '+cameraMeter.cameraId,
  protocol:(cameraMeter.metering==='center_requested'?'Central 20% AE region requested; camera algorithm is not a calibrated spot meter. ':'Whole-frame camera AE; center-region control unavailable. ')+'Reflected scene exposure at 1x, flash off, compensation 0; actual aperture/shutter/sensor ISO/post-RAW boost normalized to ISO 100.',
  calibration:'Uncalibrated camera estimate'};
}
