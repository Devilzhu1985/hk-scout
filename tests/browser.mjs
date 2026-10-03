import {chromium,webkit,expect} from '@playwright/test';
import {mkdir,readdir,writeFile,readFile} from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import sharp from 'sharp';
import {zipSync,strToU8} from 'fflate';
import {createHash} from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..'),out=path.join(root,'test-results');
await mkdir(out,{recursive:true});
const port=4180,origin='http://127.0.0.1:'+port;
const server=spawn(process.execPath,['scripts/serve.mjs'],{cwd:root,env:{...process.env,SCOUT_PORT:String(port)},stdio:['ignore','pipe','inherit'],windowsHide:true});
await new Promise((resolve,reject)=>{server.stdout.once('data',resolve);server.once('error',reject);server.once('exit',code=>reject(Error('Server exited '+code)));});
const engine=process.env.SCOUT_TEST_ENGINE==='webkit'?webkit:chromium;
let executablePath=process.env.SCOUT_BROWSER_PATH;
if(engine===chromium&&!executablePath&&process.platform==='win32'){
  const cache=path.join(process.env.LOCALAPPDATA||'','ms-playwright');
  const dirs=(await readdir(cache).catch(()=>[])).filter(n=>/^chromium-\d+$/.test(n)).sort((a,b)=>Number(a.split('-')[1])-Number(b.split('-')[1]));
  if(dirs.length)executablePath=path.join(cache,dirs.at(-1),'chrome-win64','chrome.exe');
}
let browser;
try{
  browser=await engine.launch({headless:true,...(executablePath?{executablePath}:{})});
  const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1,acceptDownloads:true,...(engine===chromium?{geolocation:{latitude:22.3193,longitude:114.1694,accuracy:12},permissions:['geolocation']}:{})});
  await context.addInitScript(()=>Object.defineProperty(window,'AmbientLightSensor',{configurable:true,value:undefined}));
  const page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto(origin);await page.getByRole('button',{name:'Plan a trip',exact:true}).click();
  await page.locator('#trip-form [name=name]').fill('Hong Kong · field test');
  await page.locator('#trip-form [name=template]').selectOption('hongkong');
  await expect(page.locator('#trip-form [name=timezone]')).toHaveValue('Asia/Hong_Kong');
  await page.getByRole('button',{name:'Create trip',exact:true}).click();
  await page.getByRole('button',{name:'+ Capture here',exact:true}).click();
  await expect(page.locator('#qr')).toBeVisible();
  await page.screenshot({path:path.join(out,'mobile-slate.png'),fullPage:true});
  await page.getByRole('button',{name:'Code photographed → continue',exact:true}).click();await expect(page.locator('#modal')).not.toBeVisible();
  await page.locator('#lighting-details summary').click();await expect(page.getByText('Live lux is unavailable in this browser',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Measure light',exact:true})).toHaveCount(0);
  await expect(page.getByRole('link',{name:'Download Scout for Android',exact:true})).toHaveAttribute('href',/scout-2\.1\.0-preview\.1-debug\.apk$/);
  await expect(page.getByText(/complete Scout app/)).toBeVisible();
  await page.locator('#set-name').fill('Sham Shui Po · awning');
  await page.locator('#set-name').press('Tab');
  await expect(page.locator('#saved')).toHaveText('Saved on this device');
  if(engine===chromium){
    await page.getByRole('button',{name:'Get fresh GPS',exact:true}).click();
    await expect(page.locator('#set-form').getByText(/22.31930, 114.16940/)).toBeVisible();
    await expect(page.locator('#set-name')).toHaveValue('Sham Shui Po · awning');
  }
  for(const [label,value]of [['Awning','100'],['Street','400']]){
    await page.getByRole('button',{name:'Manual reading',exact:true}).click();
    await page.locator('#reading-form [name=label]').fill(label);
    await page.locator('#reading-form [name=value]').fill(value);
    await page.locator('#reading-form [name=instrument]').fill('Test meter');
    await page.locator('#reading-form [name=protocol]').fill('Face up · 1.5 m');
    await page.getByRole('button',{name:'Save reading',exact:true}).click();
  }
  await expect(page.getByText(/Last two comparable readings: 4× · 2 stops/)).toBeVisible();
  const jpg=await sharp({create:{width:600,height:400,channels:3,background:'#8a8273'}}).jpeg().toBuffer();
  const phone=path.join(out,'phone-reference.jpg');await writeFile(phone,jpg);
  // The capture button supplies the destination set before the file input is used.
  await page.getByRole('button',{name:'+ Add images',exact:true}).click();
  await page.locator('#photo-file').setInputFiles(phone);
  await expect(page.locator('.thumbs img')).toHaveCount(1);
  await page.getByRole('button',{name:'Color sample',exact:true}).click();
  await page.locator('#color-canvas').click({position:{x:60,y:60}});
  await expect(page.locator('#color-result')).toContainText('sRGB');
  await page.getByRole('button',{name:'Save sample in image notes',exact:true}).click();
  await expect(page.locator('#modal')).not.toBeVisible();
  await page.locator('#set-form [name=notes]').fill('Keep the sodium-light appearance. Unfinished edit survives connectivity changes.');
  await context.setOffline(true);
  await expect(page.locator('#set-form [name=notes]')).toHaveValue(/Unfinished edit/);
  await page.locator('#set-form [name=notes]').press('Tab');
  await expect(page.locator('#saved')).toHaveText('Saved on this device');
  await page.getByRole('button',{name:'Finish stop',exact:true}).click();
  await page.screenshot({path:path.join(out,'mobile-record.png'),fullPage:true});
  assertNoOverflow(await page.evaluate(()=>({width:document.documentElement.scrollWidth,viewport:innerWidth})));
  await context.setOffline(false);
  await page.evaluate(()=>navigator.serviceWorker.ready);
  await page.reload();await expect(page.getByRole('heading',{name:'Hong Kong · field test',exact:true})).toBeVisible();
  await context.setOffline(true);await page.reload();
  await expect(page.getByRole('heading',{name:'Hong Kong · field test',exact:true})).toBeVisible();
  await context.setOffline(false);
  await page.locator('[data-action=nav][data-id=library]').click();
  const hash=createHash('sha256').update(jpg).digest('hex');
  const batch=path.join(out,'camera-test.zip');
  await writeFile(batch,zipSync({'catalogue.json':strToU8(JSON.stringify({format:'scout-camera',schemaVersion:1,assets:[{fileName:'DSC00001.ARW',sha256:'a'.repeat(64),originalRelativePath:'CARD01/DSC00001.ARW',camera:'ILCE-7CR',captureWall:'2026-10-03T12:00:00',previewPath:'previews/a.jpg',previewSha256:hash}]})),'previews/a.jpg':jpg}));
  await page.locator('#import-file').setInputFiles(batch);
  await page.getByRole('button',{name:'Import catalogue',exact:true}).click();
  await expect(page.locator('.asset')).toHaveCount(2);
  await page.locator('.asset').filter({hasText:'DSC00001.ARW'}).getByRole('button',{name:'Assign / describe',exact:true}).click();
  await page.locator('#asset-form [name=setId]').selectOption({index:1});
  await page.locator('#asset-form [name=role]').selectOption('lighting');
  await page.locator('#asset-form [name=notes]').fill('Backlit street view');
  await page.getByRole('button',{name:'Save association',exact:true}).click();
  await page.setViewportSize({width:1365,height:900});
  await page.screenshot({path:path.join(out,'desktop-library.png'),fullPage:true});
  await page.locator('[data-action=nav][data-id=transfer]').click();
  const download=page.waitForEvent('download');
  await page.getByRole('button',{name:'Prepare AI handoff',exact:true}).click();
  await expect(page.locator('#notice')).not.toHaveClass(/error/);
  const packagePath=path.join(out,'handoff.zip');await (await download).saveAs(packagePath);
  const restored=await browser.newContext({viewport:{width:390,height:844},acceptDownloads:true});
  const second=await restored.newPage();second.on('pageerror',e=>errors.push(e.message));
  await second.goto(origin);await second.locator('[data-action=nav][data-id=transfer]').click();
  await second.getByRole('button',{name:'Choose package',exact:true}).click();
  await second.locator('#import-file').setInputFiles(packagePath);
  await second.getByRole('button',{name:'Apply merge',exact:true}).click();
  await second.locator('[data-action=nav][data-id=library]').click();
  await expect(second.locator('.asset')).toHaveCount(2);
  await second.locator('[data-action=nav][data-id=transfer]').click();
  await second.getByRole('button',{name:'Choose package',exact:true}).click();
  await second.locator('#import-file').setInputFiles(packagePath);
  await expect(second.getByText('This package has already been imported.')).toBeVisible();
  await second.getByRole('button',{name:'Apply merge',exact:true}).click();
  await second.locator('[data-action=nav][data-id=library]').click();
  await expect(second.locator('.asset')).toHaveCount(2);
  await second.locator('[data-action=nav][data-id=field]').click();
  await second.getByRole('button',{name:'Open',exact:true}).click();
  await expect(second.locator('#set-form [name=notes]')).toHaveValue(/Unfinished edit/);
  await expect(second.getByText(/Last two comparable readings: 4× · 2 stops/)).toBeVisible();
  const legacy=await browser.newContext();
  await legacy.addInitScript(()=>{localStorage.setItem('hkscout_v1',JSON.stringify({v:1,settings:{wb:5500,fnum:1.8},points:[{id:'old',t:Date.parse('2026-01-01T00:00:00Z'),place:'Legacy street',notes:'Keep me',photos:[],lux:{A:50}}]}));});
  const third=await legacy.newPage();await third.goto(origin);
  await expect(third.getByRole('heading',{name:'Hong Kong · imported records',exact:true})).toBeVisible();
  await third.getByRole('button',{name:'Open',exact:true}).click();
  await expect(third.locator('#set-form [name=notes]')).toHaveValue(/Keep me/);
  // Controlled sensor fixture: verifies in-app integration, never hardware accuracy.
  const sensorContext=await browser.newContext({viewport:{width:390,height:844}});
  await sensorContext.addInitScript(()=>{
    localStorage.setItem('hkscout_v1',JSON.stringify({v:1,points:[{id:'sensor',t:Date.parse('2026-01-01T00:00:00Z'),place:'Sensor test fixture',notes:'Synthetic browser sensor',photos:[],lux:{}}]}));
    window.sensorTestMode='reading';window.sensorTestActive=0;
    window.AmbientLightSensor=class extends EventTarget{
      start(){
        this.running=true;window.sensorTestActive++;
        if(window.sensorTestMode==='error'){
          this.timer=setTimeout(()=>{const event=new Event('error');event.error=Error('Sensor permission denied in controlled test');this.dispatchEvent(event);},100);
        }else if(window.sensorTestMode==='reading'){
          this.timer=setInterval(()=>{this.illuminance=200;this.dispatchEvent(new Event('reading'));},50);
        }
      }
      stop(){clearInterval(this.timer);clearTimeout(this.timer);if(this.running){this.running=false;window.sensorTestActive--;}}
    };
  });
  const sensorPage=await sensorContext.newPage();sensorPage.on('pageerror',e=>errors.push(e.message));
  await sensorPage.goto(origin);await sensorPage.getByRole('button',{name:'Open',exact:true}).click();
  await sensorPage.locator('#lighting-details summary').click();await expect(sensorPage.getByText('Browser light sensor · experimental',{exact:true})).toBeVisible();
  await sensorPage.getByRole('button',{name:'Measure light',exact:true}).click();
  await sensorPage.getByRole('button',{name:'Capture 4 seconds',exact:true}).click();
  await expect(sensorPage.getByRole('heading',{name:'Save phone measurement',exact:true})).toBeVisible({timeout:10000});
  await expect(sensorPage.locator('#reading-form [name=value]')).toHaveValue('200');
  await sensorPage.locator('#reading-form [name=label]').fill('Controlled sensor fixture');
  await sensorPage.locator('#reading-form [name=protocol]').fill('Synthetic events only');
  await sensorPage.getByRole('button',{name:'Save reading',exact:true}).click();
  await expect(sensorPage.locator('#modal')).not.toBeVisible();
  await expect(sensorPage.getByText('200 lux',{exact:true})).toBeVisible();
  await sensorPage.evaluate(()=>window.sensorTestMode='error');
  await sensorPage.getByRole('button',{name:'Measure light',exact:true}).click();
  await sensorPage.getByRole('button',{name:'Capture 4 seconds',exact:true}).click();
  await expect(sensorPage.locator('#meter-status')).toContainText('No reading saved. Sensor permission denied');
  await expect(sensorPage.locator('#meter-value')).toHaveText('—');
  await sensorPage.getByRole('button',{name:'Manual reading',exact:true}).last().click();
  await expect(sensorPage.getByRole('heading',{name:'Add lighting reading',exact:true})).toBeVisible();
  await expect.poll(()=>sensorPage.evaluate(()=>window.sensorTestActive)).toBe(0);
  await sensorPage.getByRole('button',{name:'Cancel',exact:true}).click();
  await sensorPage.evaluate(()=>window.sensorTestMode='hold');
  await sensorPage.getByRole('button',{name:'Measure light',exact:true}).click();
  await sensorPage.getByRole('button',{name:'Capture 4 seconds',exact:true}).click();
  await expect.poll(()=>sensorPage.evaluate(()=>window.sensorTestActive)).toBe(1);
  await sensorPage.getByRole('button',{name:'Manual reading',exact:true}).last().click();
  await expect.poll(()=>sensorPage.evaluate(()=>window.sensorTestActive)).toBe(0);
  await sensorPage.getByRole('button',{name:'Cancel',exact:true}).click();
  await expect(sensorPage.getByText('200 lux',{exact:true})).toHaveCount(1);
  // New flow: no trip/city typing, automatic GPS, optional photos/light, explicit finish.
  const simpleContext=await browser.newContext({viewport:{width:390,height:844},locale:'en-GB',acceptDownloads:true});
  await simpleContext.addInitScript(()=>{
    window.gpsMode='hold';window.pendingGPS=null;
    navigator.geolocation.getCurrentPosition=(ok,fail)=>{window.pendingGPS=()=>window.gpsMode==='denied'?fail({code:1,message:'Denied fixture'}):ok({timestamp:Date.now(),coords:{latitude:22.3193,longitude:114.1694,accuracy:12}});if(window.gpsMode!=='hold')window.pendingGPS();};
  });
  const simple=await simpleContext.newPage();simple.on('pageerror',e=>errors.push(e.message));
  await simple.route('https://api.bigdatacloud.net/**',route=>route.fulfill({json:{locality:'Test district',city:'Test city'}}));
  await simple.route('https://api.github.com/repos/Devilzhu1985/hk-scout/releases*',route=>route.fulfill({json:[{tag_name:'v9.0.0-preview.1',prerelease:true,assets:[{name:'scout-9.0.0-debug.apk',browser_download_url:'https://github.com/Devilzhu1985/hk-scout/releases/download/v9.0.0-preview.1/scout-9.0.0-debug.apk',digest:'sha256:'+'a'.repeat(64),size:1000000}]}]}));
  await simple.goto(origin);await simple.getByRole('button',{name:'Capture here now',exact:true}).click();
  await simple.locator('#modal').getByRole('button',{name:'Phone only / skip code',exact:true}).click();await expect(simple.locator('#modal')).not.toBeVisible();
  await simple.locator('#set-name').fill('Open'); // Matches a UI label; must remain user content.
  await simple.locator('#set-name').press('Tab');await expect(simple.locator('#saved')).toHaveText('Saved on this device');
  await simple.evaluate(()=>window.pendingGPS());
  await expect(simple.locator('#set-form').getByText(/22.31930, 114.16940/)).toBeVisible();
  await expect(simple.locator('#set-name')).toHaveValue('Open');
  await simple.locator('#set-form [name=notes]').fill('Keep my English notes: Open, Finish stop, Lighting.');
  await simple.locator('#language').selectOption('zh');
  await expect(simple.locator('#next-step h2')).toHaveText('2 · 拍摄手机参考图');
  await expect(simple.locator('#set-name')).toHaveValue('Open');
  await expect(simple.locator('#set-form [name=notes]')).toHaveValue('Keep my English notes: Open, Finish stop, Lighting.');
  await expect(simple.locator('html')).toHaveAttribute('lang','zh-CN');
  await simple.getByRole('button',{name:'拍摄参考图',exact:true}).click();
  await expect(simple.getByText(/浏览器可以打开相机/)).toBeVisible();
  await expect(simple.locator('#camera-file')).toHaveAttribute('capture','environment');
  await simple.getByRole('button',{name:'打开手机相机',exact:true}).click();
  await simple.locator('#camera-file').setInputFiles(phone);
  await expect(simple.locator('.thumbs img')).toHaveCount(1);
  await simple.getByRole('button',{name:'编辑',exact:true}).click();
  await simple.locator('#asset-form [name=role]').selectOption('lighting');
  await simple.getByRole('button',{name:'保存关联',exact:true}).click();
  await simple.getByRole('button',{name:'跳过光照',exact:true}).click();
  await expect(simple.locator('#next-step h2')).toHaveText('4 · 完成此地点');
  await simple.getByRole('button',{name:'完成此地点',exact:true}).click();
  await expect(simple.getByRole('heading',{name:'此地点已完成',exact:true})).toBeVisible();
  await simple.screenshot({path:path.join(out,'streamlined-chinese.png'),fullPage:true});
  assertNoOverflow(await simple.evaluate(()=>({width:document.documentElement.scrollWidth,viewport:innerWidth})));
  await simple.getByRole('button',{name:'编辑',exact:true}).click();
  const originalDownload=simple.waitForEvent('download');await simple.getByRole('button',{name:/保存原文件/}).click();
  const original=await originalDownload;await original.saveAs(path.join(out,'preserved-original.jpg'));
  if(createHash('sha256').update(await readFile(path.join(out,'preserved-original.jpg'))).digest('hex')!==createHash('sha256').update(jpg).digest('hex'))throw Error('Phone original changed');
  await simple.getByRole('button',{name:'取消',exact:true}).click();
  await simple.locator('#language').selectOption('en');
  await expect(simple.locator('.thumbs figcaption')).toContainText('lighting');
  await simple.getByRole('button',{name:'Update Scout',exact:true}).click();
  await expect(simple.getByText(/v9.0.0-preview.1/)).toBeVisible();
  await expect(simple.getByText(/This is the browser edition/)).toBeVisible();
  await simple.getByRole('button',{name:'Close',exact:true}).click();
  await simple.evaluate(()=>window.gpsMode='denied');
  await simple.getByRole('button',{name:'Start next stop',exact:true}).click();
  await simple.locator('#modal').getByRole('button',{name:'Phone only / skip code',exact:true}).click();await expect(simple.locator('#modal')).not.toBeVisible();
  await expect(simple.getByText(/GPS unavailable. Keep shooting/)).toBeVisible();
  await simple.getByRole('button',{name:'Sony only / skip phone photo',exact:true}).click();
  await simple.getByRole('button',{name:'Skip lighting',exact:true}).click();
  await simple.getByRole('button',{name:'Finish stop',exact:true}).click();
  await expect(simple.getByRole('heading',{name:'Stop complete',exact:true})).toBeVisible();
  await simple.locator('#language').selectOption('zh');await simple.reload();
  await expect(simple.locator('#language')).toHaveValue('zh');
  await simple.locator('#nav [data-action=nav][data-id=settings]').click();await expect(simple.getByRole('heading',{name:'行程与设备',exact:true})).toBeVisible();
  await simple.locator('#nav [data-action=nav][data-id=transfer]').click();await expect(simple.getByRole('heading',{name:'汇总所有拍摄资料。',exact:true})).toBeVisible();

  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS '+engine.name()+': capture-to-handoff, offline reload, restored records, unsupported lighting UI, and controlled sensor capture/permission error/cancellation. No page errors.');
}finally{await browser?.close();server.kill();}
function assertNoOverflow(size){if(size.width>size.viewport+1)throw Error('Mobile horizontal overflow: '+JSON.stringify(size));}
