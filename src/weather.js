export const WEATHER_SOURCE_URL='https://open-meteo.com/';
const finite=(v,min,max)=>Number.isFinite(v)&&v>=min&&v<=max;
const instant=v=>typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T.*Z$/.test(v)&&Number.isFinite(Date.parse(v));
const point=p=>p&&finite(p.lat,-90,90)&&finite(p.lng,-180,180);
const optional=(v,min,max)=>v===null||finite(v,min,max);
const zoneValid=zone=>{try{new Intl.DateTimeFormat('en',{timeZone:zone});return typeof zone==='string'&&!!zone;}catch{return false;}};
export function validWeather(w){
 return !!(w&&w.source==='Open-Meteo'&&w.kind==='model-estimate'&&instant(w.validAt)&&instant(w.retrievedAt)&&instant(w.fixAt)&&point(w.requestedLocation)&&point(w.gridLocation)&&zoneValid(w.timezone)&&Number.isInteger(w.code)&&w.code>=0&&w.code<=99&&optional(w.temperatureC,-100,100)&&optional(w.cloudCover,0,100)&&optional(w.windKmh,0,500)&&optional(w.precipitationMm,0,2000)&&Number.isInteger(w.intervalSeconds)&&w.intervalSeconds>0&&w.intervalSeconds<=86400);
}
export function freshWeatherFix(fix,at=Date.now()){
 return !!(fix?.method==='gps'&&point(fix)&&instant(fix.capturedAt)&&Math.abs(at-Date.parse(fix.capturedAt))<=30000);
}
export function parseWeather(data,fix,at=Date.now()){
 const c=data?.current,u=data?.current_units;
 if(!c||!Number.isFinite(c.time)||Math.abs(c.time*1000-at)>3600000||u?.time!=='unixtime'||u.temperature_2m!=='°C'||u.cloud_cover!=='%'||u.wind_speed_10m!=='km/h'||u.precipitation!=='mm')return null;
 const w={source:'Open-Meteo',kind:'model-estimate',validAt:new Date(c.time*1000).toISOString(),retrievedAt:new Date(at).toISOString(),fixAt:fix.capturedAt,
  requestedLocation:{lat:Number(fix.lat.toFixed(2)),lng:Number(fix.lng.toFixed(2))},gridLocation:{lat:data.latitude,lng:data.longitude},timezone:data.timezone,
  code:c.weather_code,temperatureC:c.temperature_2m??null,cloudCover:c.cloud_cover??null,windKmh:c.wind_speed_10m??null,precipitationMm:c.precipitation??null,intervalSeconds:c.interval};
 return validWeather(w)?w:null;
}
export async function lookupWeather(fix){
 if(!freshWeatherFix(fix))return null;
 const query=new URLSearchParams({latitude:fix.lat.toFixed(2),longitude:fix.lng.toFixed(2),current:'temperature_2m,weather_code,cloud_cover,wind_speed_10m,precipitation',timezone:'auto',timeformat:'unixtime',temperature_unit:'celsius',wind_speed_unit:'kmh',precipitation_unit:'mm'});
 const response=await fetch('https://api.open-meteo.com/v1/forecast?'+query,{signal:AbortSignal.timeout(8000),cache:'no-store'});
 if(!response.ok)return null;
 return parseWeather(await response.json(),fix);
}
export function automaticWeatherChanges(current,fix,weather){
 // Snapshot the new stop, not today's weather against an imported/historical stop.
 if(!current||current.deleted||current.endedAt||current.weather||!validWeather(weather)||weather.fixAt!==fix.capturedAt||weather.requestedLocation.lat!==Number(fix.lat.toFixed(2))||weather.requestedLocation.lng!==Number(fix.lng.toFixed(2))||current.location?.method!=='gps'||current.location.capturedAt!==fix.capturedAt||current.location.lat!==fix.lat||current.location.lng!==fix.lng||Math.abs(Date.parse(current.startedAt)-Date.parse(weather.validAt))>3600000)return {};
 return current.autoTimezone===true?{weather,timezone:weather.timezone}:{weather};
}
export function weatherLabel(code){
 if(code===0)return 'Clear sky';if(code===1)return 'Mainly clear';if(code===2)return 'Partly cloudy';if(code===3)return 'Overcast';
 if([45,48].includes(code))return 'Fog';if([51,53,55].includes(code))return 'Drizzle';if([56,57,66,67].includes(code))return 'Freezing rain / drizzle';
 if([61,63,65].includes(code))return 'Rain';if([71,73,75,77].includes(code))return 'Snow';if([80,81,82].includes(code))return 'Rain showers';if([85,86].includes(code))return 'Snow showers';if([95,96,97,99].includes(code))return 'Thunderstorm';return 'Weather condition unavailable';
}
