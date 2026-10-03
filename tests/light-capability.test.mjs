import test from 'node:test';
import assert from 'node:assert/strict';
import {inspectLightCapability} from '../src/light-capability.js';

test('browser without a sensor explains the same Scout app before capture',async()=>{
  const result=await inspectLightCapability({native:false,browserSensor:false,secure:true});
  assert.equal(result.available,false);
  assert.equal(result.mode,'browser');
  assert.match(result.detail,/complete Scout app/);
  assert.match(result.detail,/home-screen shortcut/);
});
test('Android with registered bridge exposes the built-in meter and its real source',async()=>{
  const info={available:true,name:'Device sensor',device:'Test phone'};
  const result=await inspectLightCapability({android:true,native:true,bridge:true,readInfo:async()=>info});
  assert.equal(result.available,true);assert.equal(result.mode,'android');assert.deepEqual(result.info,info);
});
test('Android missing sensor or broken bridge stays in Android recovery, not an install loop',async()=>{
  for(const options of [
    {bridge:false},
    {bridge:true,readInfo:async()=>({available:false})},
    {bridge:true,readInfo:async()=>{throw Error('bridge error');}}
  ]){
    const result=await inspectLightCapability({android:true,native:true,...options});
    assert.equal(result.available,false);assert.equal(result.mode,'android');
    assert.doesNotMatch(result.detail,/complete Scout app|home-screen shortcut/);
  }
});
test('browser sensor is offered only in a secure context and is labelled experimental',async()=>{
  assert.equal((await inspectLightCapability({browserSensor:true,secure:false})).available,false);
  const result=await inspectLightCapability({browserSensor:true,secure:true});
  assert.equal(result.available,true);assert.match(result.title,/experimental/);
});
