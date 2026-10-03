// Julian-day solar approximation. UTC noon anchors the requested local calendar date;
// search neighboring UTC dates so locations on either side of the date line work.
export function daylight(date,lat,lng,timezone) {
  if(!Number.isFinite(lat)||!Number.isFinite(lng))return null;
  const rad=Math.PI/180;
  const dayKey=t=>new Intl.DateTimeFormat('en-CA',{timeZone:timezone,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(t));
  for(const delta of [-1,0,1]) {
    const jd=(Date.parse(date+'T12:00:00Z')+delta*86400000)/86400000+2440587.5;
    const lw=-lng*rad, n=Math.round(jd-2451545-0.0009-lw/(2*Math.PI)), ds=0.0009+lw/(2*Math.PI)+n;
    const M=(357.5291+0.98560028*ds)*rad;
    const L=M+(1.9148*Math.sin(M)+0.02*Math.sin(2*M)+0.0003*Math.sin(3*M))*rad+102.9372*rad+Math.PI;
    const dec=Math.asin(Math.sin(L)*Math.sin(23.4397*rad));
    const noon=2451545+ds+0.0053*Math.sin(M)-0.0069*Math.sin(2*L);
    const ms=j=>(j-2440587.5)*86400000;
    if(dayKey(ms(noon))!==date)continue;
    const angle=(Math.sin(-0.833*rad)-Math.sin(lat*rad)*Math.sin(dec))/(Math.cos(lat*rad)*Math.cos(dec));
    if(Math.abs(angle)>1)return {polar:angle<0?'Sun stays above the horizon':'Sun stays below the horizon'};
    const span=Math.acos(angle)/(2*Math.PI);
    return {rise:new Date(ms(noon-span)).toISOString(),set:new Date(ms(noon+span)).toISOString()};
  }
  return null;
}
