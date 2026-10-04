import {chromium,expect} from '@playwright/test';
import {spawn} from 'node:child_process';
import path from 'node:path';
import {mkdir} from 'node:fs/promises';
const root=path.resolve(import.meta.dirname,'..'),origin='http://127.0.0.1:4184';
const server=spawn(process.execPath,['scripts/serve.mjs'],{cwd:root,env:{...process.env,SCOUT_PORT:'4184'},stdio:['ignore','pipe','inherit'],windowsHide:true});
await new Promise((resolve,reject)=>{server.stdout.once('data',resolve);server.once('error',reject);});let browser;
try{
 browser=await chromium.launch({headless:true,...(process.platform==='win32'?{executablePath:process.env.SCOUT_BROWSER_PATH||path.join(process.env.LOCALAPPDATA,'ms-playwright/chromium-1223/chrome-win64/chrome.exe')}:{})});
 const context=await browser.newContext({viewport:{width:390,height:844},locale:'en-GB',serviceWorkers:'block'});await context.route('**/*',r=>r.request().url().startsWith(origin)?r.continue():r.abort());
 await context.addInitScript(()=>{
  window.androidBridge={};window.cameraCalls=0;window.gpsCalls=0;window.testMeterMode='success';
  window.Capacitor={PluginHeaders:[{name:'ReferenceCamera',methods:[{name:'measureEV',rtype:'promise'}]},{name:'LightMeter',methods:[{name:'info',rtype:'promise'}]},{name:'Geolocation',methods:[{name:'getCurrentPosition',rtype:'promise'}]}],nativePromise:async(plugin,method)=>{
   if(plugin==='LightMeter')return {available:true,device:'Fixture phone',name:'Fixture lux sensor'};
   if(plugin==='Geolocation'){window.gpsCalls++;return {timestamp:Date.now(),coords:{latitude:22.3193,longitude:114.1694,accuracy:12}};}
   if(plugin==='ReferenceCamera'){
    window.cameraCalls++;
    if(window.testMeterMode==='hold')await new Promise(resolve=>window.resolveMeter=resolve);
    if(window.testMeterMode==='cancel')return {cancelled:true};
    if(window.testMeterMode==='error')throw Error('Camera permission denied. Allow camera access in system settings or enter EV manually.');
    return {source:'android_camera2_ae',cameraId:'0',device:'Fixture phone',aperture:2,exposureNs:7812500,iso:100,postRawBoost:100,aeState:2,compensationSteps:0,metering:'center_requested',frameTimestampNs:'1234567890123456',previewClippedFraction:0,samples:{count:8,windowMs:700,minEv:9,maxEv:9},ev100:window.testMeterMode==='invalid'?99:9,measuredAt:new Date().toISOString()};
   }
  }};
 });
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(origin);await page.locator('[data-action=quick-start]').click();await expect(page.locator('.clapper-sticks')).toBeVisible();await expect(page.locator('[data-action=slate-camera-ev]')).toBeVisible();
 const read=()=>page.evaluate(async()=>{const db=await new Promise(resolve=>{const q=indexedDB.open('scout-v2');q.onsuccess=()=>resolve(q.result);});return new Promise(resolve=>{const tx=db.transaction(['state','blobs']),r=tx.objectStore('state').get('main'),b=tx.objectStore('blobs').getAllKeys();tx.oncomplete=()=>{db.close();resolve({state:r.result,blobs:b.result});};});});
 await page.locator('[data-action=slate-camera-ev]').click();await expect(page.locator('#slate-light')).toContainText('Camera EV100 · estimate');await expect(page.locator('#slate-light strong')).toHaveText('9');await expect(page.locator('#slate-light')).not.toContainText('lux');expect((await read()).state.records.filter(r=>r.kind==='reading')).toHaveLength(1);expect((await read()).blobs).toHaveLength(0);expect(await page.evaluate(()=>window.gpsCalls)).toBe(1);
 for(const mode of ['cancel','error','invalid']){await page.evaluate(mode=>window.testMeterMode=mode,mode);await page.locator('[data-action=slate-camera-ev]').click();await expect(page.locator('[data-action=slate-camera-ev]')).toBeEnabled();expect((await read()).state.records.filter(r=>r.kind==='reading')).toHaveLength(1);await expect(page.locator('#slate-light strong')).toHaveText('9');}
 await page.evaluate(()=>window.testMeterMode='hold');await page.locator('[data-action=slate-camera-ev]').click();await expect(page.locator('[data-action=slate-camera-ev]')).toBeDisabled();const calls=await page.evaluate(()=>window.cameraCalls);await page.locator('[data-action=slate-camera-ev]').dispatchEvent('click');expect(await page.evaluate(()=>window.cameraCalls)).toBe(calls);await page.evaluate(()=>{window.testMeterMode='success';window.resolveMeter();});await expect(page.locator('[data-action=slate-camera-ev]')).toBeEnabled();expect((await read()).state.records.filter(r=>r.kind==='reading')).toHaveLength(2);
 await page.locator('[data-action=slate-confirm]').click();await page.locator('#language').selectOption('zh');await page.getByRole('button',{name:'显示相机识别板',exact:true}).click();await expect(page.locator('#slate-light')).toContainText('相机 EV100 · 估算');await expect(page.locator('#slate-photo')).toContainText('地点 / 场景编号');
 for(const [width,height]of [[390,844],[360,640],[320,568],[844,390]]){await page.setViewportSize({width,height});for(const expanded of [false,true]){await page.locator('.slate-details').evaluate((el,open)=>el.open=open,expanded);const positions=await page.locator('.slate-footer').evaluate(el=>[...el.querySelectorAll('button:not([hidden])')].map(b=>{const r=b.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight+1&&r.left>=0&&r.right<=innerWidth+1&&r.height>=44&&document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('button')===b;}));expect(positions.every(Boolean)).toBe(true);}expect(await page.locator('#slate-photo').evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true);}
 await page.setViewportSize({width:390,height:844});await page.locator('.slate-details').evaluate(el=>el.open=false);await page.locator('.slate-scroll').evaluate(el=>el.scrollTop=0);await mkdir(path.join(root,'test-results'),{recursive:true});await page.screenshot({path:path.join(root,'test-results/clapper-camera-ev-chinese.png')});expect(errors).toEqual([]);
 console.log('Camera EV bridge fixture: save, estimate label, metadata persistence, no image capture, cancellation, permission/error/invalid result, reentry guard, GPS reuse and bilingual clapperboard footer passed. Not a hardware accuracy test.');
}finally{await browser?.close();server.kill();}
