import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,readFile,copyFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import {exiftool} from 'exiftool-vendored';
import {catalogue,hashFile,wallTime} from '../scripts/catalogue.mjs';
import {unpack} from '../src/packets.js';
import {strFromU8} from 'fflate';
test('desktop import preserves originals, resumes, detects byte duplicates and accounts for damaged files',async()=>{
  const temp=await mkdtemp(path.join(os.tmpdir(),'scout-import-')),input=path.join(temp,'originals'),output=path.join(temp,'review');
  await mkdir(input);await mkdir(path.join(input,'other-card'));
  const file=path.join(input,'DSC00001.jpg');
  await sharp({create:{width:80,height:40,channels:3,background:'#7f7f7f'}}).jpeg().toFile(file);
  try{
    await exiftool.write(file,{Make:'SONY',Model:'ILCE-7CR',DateTimeOriginal:'2026:10:03 12:03:04',FNumber:4,ISO:400,ExposureTime:0.01},['-overwrite_original']);
    const before=await hashFile(file);
    await copyFile(file,path.join(input,'other-card','DSC00001-copy.jpg'));
    await writeFile(path.join(input,'damaged.arw'),'not a valid RAW file');
    const result=await catalogue(input,output,{log:()=>{}});
    assert.equal(result.assets.length,2);
    const a=result.assets.find(a=>a.fileName==='DSC00001.jpg');
    assert.equal(a.captureWall,'2026-10-03T12:03:04');assert.equal(a.camera,'ILCE-7CR');
    assert.equal(a.duplicatePaths.length,1);assert(a.previewPath);
    assert(result.assets.find(a=>a.fileName==='damaged.arw').warnings.length>0);
    assert.equal(await hashFile(file),before);
    const entries=unpack(new Uint8Array(await readFile(path.join(output,result.batches[0]))));
    const packet=JSON.parse(strFromU8(entries['catalogue.json']));assert.equal(packet.assets.length,2);
    const again=await catalogue(input,output,{log:()=>{}});assert.equal(again.assets.length,2);assert.equal(await hashFile(file),before);
    assert.equal(again.assets.find(a=>a.sha256===before).previewSha256,a.previewSha256);
    await assert.rejects(catalogue(input,path.join(input,'review'),{log:()=>{}}),/non-nested/);
    assert.equal(wallTime(null),null);
  }finally{await exiftool.end();}
});
