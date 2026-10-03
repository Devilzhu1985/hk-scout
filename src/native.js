import {Capacitor,registerPlugin} from '@capacitor/core';
import {Filesystem,Directory} from '@capacitor/filesystem';
import {Share} from '@capacitor/share';
import {Geolocation} from '@capacitor/geolocation';
import {summarize} from './model.js';
import {inspectLightCapability} from './light-capability.js';
export const native=Capacitor.isNativePlatform();
const LightMeter=registerPlugin('LightMeter');
let stopCurrent=()=>{},meterEpoch=0;
export function stopMeter(){meterEpoch++;stopCurrent();stopCurrent=()=>{};}
export async function getLightCapability() {
  return inspectLightCapability({
    android:native && Capacitor.getPlatform()==='android',
    native,bridge:Capacitor.isPluginAvailable('LightMeter'),
    browserSensor:typeof globalThis.AmbientLightSensor==='function',
    secure:globalThis.isSecureContext,
    readInfo:()=>new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('Sensor check timed out')),4000);
      LightMeter.info().then(resolve,reject).finally(()=>clearTimeout(timer));
    })
  });
}
export async function measureLight(onReading) {
  stopMeter();
  const epoch=meterEpoch;
  const capability=await getLightCapability();
  if(epoch!==meterEpoch)throw Error('Measurement interrupted. Please capture again.');
  if(!capability.available)throw Error(capability.title+'. '+capability.detail);
  if(capability.mode==='android') {
    const info=capability.info;
    let finished=false;
    stopCurrent=()=>{if(!finished)LightMeter.stop().catch(()=>{});};
    const listener=await LightMeter.addListener('reading',r=>onReading(r.lux));
    try {if(epoch!==meterEpoch)throw Error('Measurement interrupted. Please capture again.');const result=await LightMeter.capture();finished=true;return {...result,instrument:info.device+' / '+info.name,source:'Android TYPE_LIGHT'};}
    finally{await listener.remove();if(epoch===meterEpoch)stopCurrent=()=>{};}
  }
  return new Promise((resolve,reject)=>{
    const values=[];let last=null,receivedAt=0,done=false;
    const sensor=new AmbientLightSensor({frequency:5});
    const finish=(error)=>{if(done)return;done=true;clearInterval(timer);sensor.stop();stopCurrent=()=>{};if(error)reject(error);else resolve({...summarize(values),durationMs:4000,instrument:'Browser ambient light sensor',source:'Experimental browser sensor'});};
    const start=performance.now();
    const timer=setInterval(()=>{if(last!==null && performance.now()-receivedAt<2000)values.push(last);if(performance.now()-start>=4000)finish(!values.length?Error('No fresh sensor values received.'):null);},200);
    sensor.addEventListener('reading',()=>{if(Number.isFinite(sensor.illuminance)&&sensor.illuminance>=0){last=sensor.illuminance;receivedAt=performance.now();onReading(last);}});
    sensor.addEventListener('error',e=>finish(Error(e.error?.message||'Sensor access unavailable. Use manual entry.')));
    stopCurrent=()=>finish(Error('Measurement interrupted. Please capture again.'));
    try{sensor.start();}catch(e){finish(e);}
  });
}
export async function locate() {
  const p=native?await Geolocation.getCurrentPosition({enableHighAccuracy:true,timeout:15000,maximumAge:0}):await new Promise((resolve,reject)=>navigator.geolocation?navigator.geolocation.getCurrentPosition(resolve,reject,{enableHighAccuracy:true,timeout:15000,maximumAge:0}):reject(Error('Location unavailable')));
  if(!Number.isFinite(p.timestamp)||Math.abs(Date.now()-p.timestamp)>30000)throw Error('The location provider returned an old fix. Try again or enter coordinates.');
  return {lat:p.coords.latitude,lng:p.coords.longitude,accuracy:p.coords.accuracy,capturedAt:new Date(p.timestamp).toISOString(),method:'gps'};
}
export async function deliver(filename,bytes) {
  if(native){
    let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
    const result=await Filesystem.writeFile({path:filename,data:btoa(binary),directory:Directory.Cache});
    await Share.share({title:'Scout field package',files:[result.uri],dialogTitle:'Save your field package'});
    return;
  }
  const file=new File([bytes],filename,{type:'application/zip'});
  const url=URL.createObjectURL(file),a=document.createElement('a');a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);
}
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopMeter();});
window.addEventListener('pagehide',stopMeter);
