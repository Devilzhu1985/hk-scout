package com.hkscout.field;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Native TYPE_LIGHT readings. No camera-derived estimate or calibration claim. */
@CapacitorPlugin(name = "LightMeter")
public class LightMeterPlugin extends Plugin implements SensorEventListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager manager;
    private Sensor sensor;
    private PluginCall pending;
    private long started;
    private double sum, min, max;
    private int count;
    private boolean saturated;
    private final Runnable finish = () -> complete();

    @Override public void load() {
        manager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        sensor = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    @PluginMethod public void info(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", sensor != null);
        result.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        result.put("name", sensor == null ? "No exposed light sensor" : sensor.getName());
        if (sensor != null) {
            result.put("maximumRange", sensor.getMaximumRange());
            result.put("resolution", sensor.getResolution());
        }
        call.resolve(result);
    }

    @PluginMethod public void capture(PluginCall call) {
        handler.post(() -> {
            cancel("A new measurement replaced this one.");
            if (sensor == null) { call.reject("No light sensor is exposed. Use manual entry."); return; }
            pending = call;
            started = SystemClock.elapsedRealtime();
            sum = 0; min = Double.POSITIVE_INFINITY; max = 0; count = 0; saturated = false;
            if (!manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)) {
                cancel("Unable to register the light sensor."); return;
            }
            handler.postDelayed(finish, 4000);
        });
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (pending == null || event.sensor.getType() != Sensor.TYPE_LIGHT) return;
        // Reject events queued before this capture began.
        if (event.timestamp < started * 1000000L) return;
        float lux = event.values[0];
        if (!Float.isFinite(lux) || lux < 0) return;
        sum += lux; min = Math.min(min, lux); max = Math.max(max, lux); count++;
        saturated |= lux >= sensor.getMaximumRange();
        JSObject eventData = new JSObject(); eventData.put("lux", lux);
        notifyListeners("reading", eventData);
    }

    private void complete() {
        PluginCall call = pending;
        if (call == null) return;
        pending = null; unregister();
        if (count == 0) { call.reject("No fresh sensor event. Uncover the sensor and try again, or use manual entry."); return; }
        if (saturated) { call.reject("The light sensor reached its reported range. Use an external meter; no reading was saved."); return; }
        JSObject result = new JSObject();
        result.put("value", sum / count); result.put("min", min); result.put("max", max);
        result.put("count", count); result.put("durationMs", SystemClock.elapsedRealtime() - started);
        result.put("eventBased", true);
        call.resolve(result);
    }
    private void unregister() {
        handler.removeCallbacks(finish);
        if (manager != null) manager.unregisterListener(this);
    }
    private void cancel(String reason) {
        PluginCall call = pending; pending = null; unregister();
        if (call != null) call.reject(reason);
    }
    @PluginMethod public void stop(PluginCall call) {
        handler.post(() -> { cancel("Measurement interrupted. Capture again."); call.resolve(); });
    }
    @Override protected void handleOnPause() { handler.post(() -> cancel("App left the foreground. Capture again.")); }
    @Override protected void handleOnDestroy() { handler.post(() -> cancel("App closed.")); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* TYPE_LIGHT accuracy flags do not establish photometric calibration. */ }
}
