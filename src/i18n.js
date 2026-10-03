// Only UI literals pass through this module. Template substitutions (notes, names,
// filenames, codes and persisted enum values) are never translated implicitly.
import {zh} from './zh.js';
let language='en';
const slotPattern=/SCOUTSLOT\d+END/g;
const normalize=text=>text.replace(slotPattern,'{slot}');
const parameterized=new Map(Object.entries(zh).filter(([key])=>key.includes('SCOUTSLOT')).map(([key,value])=>[normalize(key),{key,value}]));
export const getLanguage=()=>language;
export const locale=()=>language==='zh'?'zh-CN':'en-GB';
export function setLanguage(value){language=value==='zh'?'zh':'en';}
export function t(value){
  const text=String(value??'');
  if(language!=='zh')return text;
  const trimmed=text.trim();
  if(Object.hasOwn(zh,trimmed))return text.replace(trimmed,zh[trimmed]);
  const match=parameterized.get(normalize(trimmed));
  if(match){const originals=match.key.match(slotPattern)||[],slots=trimmed.match(slotPattern)||[];const mapping=Object.fromEntries(originals.map((key,i)=>[key,slots[i]]));return text.replace(trimmed,match.value.replace(slotPattern,slot=>mapping[slot]));}
  return text;
}
// Localize the literal HTML before inserting substitutions, protecting user data
// even when a note happens to be identical to an interface label such as "Open".
export function h(strings,...values){
  const source=typeof strings==='string'?strings:strings.reduce((s,part,i)=>s+(i?'SCOUTSLOT'+(i-1)+'END':'')+part,'');
  if(language!=='zh')return source.replace(/SCOUTSLOT(\d+)END/g,(_,i)=>values[i]);
  // Preserve markup byte-for-byte, including conditional attributes. Parsing and
  // serializing unfinished attributes would alter their names or boolean values.
  const translated=source.split(/(<pre\b[^>]*>[\s\S]*?<\/pre>|<code\b[^>]*>[\s\S]*?<\/code>)/gi).map((part,i)=>i%2?part:part
    .replace(/(^|>)([^<>]*)(?=<|$)/g,(_,prefix,text)=>prefix+t(text))
    .replace(/(placeholder|aria-label|title)="([^"]*)"/g,(_,attr,text)=>attr+'="'+t(text)+'"')).join('');
  return translated.replace(/SCOUTSLOT(\d+)END/g,(_,i)=>values[i]);
}
