// Probe capabilities without starting a sensor or requesting permission.
export async function inspectLightCapability({android,native,bridge,readInfo,browserSensor,secure}) {
  if (android) {
    if (!bridge) return {available:false,mode:'android',title:'Built-in meter could not load',detail:'Close and reopen Scout. If this persists, update this Scout installation. Your saved records remain available.'};
    try {
      const info=await readInfo();
      return info.available
        ? {available:true,mode:'android',title:'Scout built-in light meter',detail:'Uses this phone’s light sensor. Readings are uncalibrated.',info}
        : {available:false,mode:'android',title:'No light sensor available',detail:'Scout could not find an exposed light sensor on this phone. You can still record a reading manually.'};
    } catch {
      return {available:false,mode:'android',title:'Built-in meter could not start',detail:'Close and reopen Scout, then try again. You can keep recording observations and manual readings.'};
    }
  }
  if (native) return {available:false,mode:'native',title:'Live lux is unavailable on this device',detail:'This Scout edition supports manual light readings.'};
  if (secure && browserSensor) return {available:true,mode:'browser',title:'Browser light sensor · experimental',detail:'Your browser exposes a light sensor. Access is checked when you capture; readings are uncalibrated.'};
  return {available:false,mode:'browser',title:'Live lux is unavailable in this browser',detail:'The built-in light meter is included in Scout for Android—the complete Scout app. A browser home-screen shortcut still has browser sensor limits.'};
}
