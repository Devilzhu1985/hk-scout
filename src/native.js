import {Capacitor,registerPlugin} from '@capacitor/core';
import {Filesystem,Directory} from '@capacitor/filesystem';
import {Share} from '@capacitor/share';
import {Geolocation} from '@capacitor/geolocation';
import {summarize} from './model.js';
export const native=Capacitor.isNativePlatform();
const LightMeter=registerPlugin('LightMeter');
let stopCurrent=()=>{};
export function stopMeter(){stopCurrent();stopCurrent=()=>{};}
export async function measureLight(onReading) {
  stopMeter();
  if(native && Capacitor.getPlatform()==='android') {
    const info=await LightMeter.info(); if(!info.available)throw Error('This phone does not expose a light sensor. Use a manual meter reading.');
    let finished=false;
    stopCurrent=()=>{if(!finished)LightMeter.stop().catch(()=>{});};
    const listener=await LightMeter.addListener('reading',r=>onReading(r.lux));
    try {const result=await LightMeter.capture();finished=true;return {...result,instrument:info.device+' / '+info.name,source:'Android TYPE_LIGHT'};}
    finally{await listener.remove();stopCurrent=()=>{};}
  }
  if(!('AmbientLightSensor' in globalThis))throw Error('Live lux needs the Scout Android app on a phone with a light sensor. This browser supports manual readings.');
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
