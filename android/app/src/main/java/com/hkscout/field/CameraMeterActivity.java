package com.hkscout.field;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;

/** Preview-only Camera2 reflected EV estimate. Never captures or stores an image. */
public class CameraMeterActivity extends Activity implements TextureView.SurfaceTextureListener {
    private TextureView texture;private CameraMeterView meterView;
    private HandlerThread thread;private Handler worker;private final Handler ui=new Handler(Looper.getMainLooper());
    private CameraDevice camera;private CameraCaptureSession session;private Surface surface;
    private CameraCharacteristics characteristics;private Size size;private String cameraId;private int orientation;
    private boolean chinese,centerRequested;private volatile boolean closing;private Rect region;
    private static final class Measurement {
        final CameraMeterFeedback.Frame frame;final JSONObject candidate;final String exposure;
        Measurement(CameraMeterFeedback.Frame frame,JSONObject candidate,String exposure){this.frame=frame;this.candidate=candidate;this.exposure=exposure;}
    }
    private volatile Measurement measurement;
    private final CameraExposure.Window window=new CameraExposure.Window();
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){transform();}};
    private String tr(String en,String zh){return chinese?zh:en;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);chinese="zh".equals(getIntent().getStringExtra("language"));getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        thread=new HandlerThread("ScoutCameraEV");thread.start();worker=new Handler(thread.getLooper());
        meterView=new CameraMeterView(this,chinese,this::finish,this::saveReading);texture=meterView.texture;texture.setSurfaceTextureListener(this);
        setContentView(meterView);ui.post(refresh);
    }
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();((DisplayManager)getSystemService(DISPLAY_SERVICE)).registerDisplayListener(displayListener,ui);}
    @Override public void onDetachedFromWindow(){((DisplayManager)getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);super.onDetachedFromWindow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){worker.post(this::open);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){measurement=null;return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
    private static boolean has(int[] a,int v){if(a!=null)for(int x:a)if(x==v)return true;return false;}
    private void open(){try{
        CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);double best=-Double.MAX_VALUE;
        for(String id:manager.getCameraIdList()){
            CameraCharacteristics c=manager.getCameraCharacteristics(id);if(!Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK))continue;
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null||map.getOutputSizes(SurfaceTexture.class)==null)continue;
            List<CaptureResult.Key<?>> keys=c.getAvailableCaptureResultKeys();if(!keys.contains(CaptureResult.SENSOR_EXPOSURE_TIME)||!keys.contains(CaptureResult.SENSOR_SENSITIVITY)||!keys.contains(CaptureResult.LENS_APERTURE))continue;
            if(!has(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),CaptureRequest.CONTROL_AE_MODE_ON))continue;
            double score=0;float[] focal=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);SizeF physical=c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if(focal!=null&&focal.length>0&&physical!=null&&physical.getWidth()>0)score=-Math.abs(Math.log((focal[0]*36/physical.getWidth())/26));
            if(Build.VERSION.SDK_INT>=28&&has(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA))score+=10;
            if(score>best){best=score;cameraId=id;characteristics=c;}
        }
        if(cameraId==null)throw new Exception(tr("This phone does not expose the exposure metadata needed for EV. Use the lux sensor or manual EV.","此手机未开放 EV 所需的曝光数据，请使用照度传感器或手动输入 EV。"));
        Rect active=characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);if(active==null)throw new Exception("Camera sensor geometry unavailable");
        Size[] sizes=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
        size=sizes[CameraPreview.chooseSize(Arrays.stream(sizes).map(x->new int[]{x.getWidth(),x.getHeight()}).toArray(int[][]::new),active.width(),active.height())];
        orientation=Optional.ofNullable(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).orElse(90);
        centerRequested=Optional.ofNullable(characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).orElse(0)>0;
        int rw=Math.max(1,active.width()/5),rh=Math.max(1,active.height()/5);region=new Rect(active.centerX()-rw/2,active.centerY()-rh/2,active.centerX()+rw/2,active.centerY()+rh/2);
        if(closing)return;
        manager.openCamera(cameraId,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(closing){c.close();return;}camera=c;startPreview();}public void onDisconnected(CameraDevice c){c.close();fail(tr("Camera disconnected","相机连接已中断"));}public void onError(CameraDevice c,int error){c.close();fail(tr("Camera unavailable: ","相机不可用：")+error);}},worker);
    }catch(Exception e){fail(e.getMessage());}}
    private void startPreview(){try{
        if(closing||texture.getSurfaceTexture()==null)return;texture.getSurfaceTexture().setDefaultBufferSize(size.getWidth(),size.getHeight());surface=new Surface(texture.getSurfaceTexture());
        camera.createCaptureSession(Collections.singletonList(surface),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession s){if(closing){s.close();return;}session=s;try{
                CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(surface);b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,0);b.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
                if(Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)))b.set(CaptureRequest.CONTROL_AE_LOCK,false);
                if(has(characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                if(centerRequested)b.set(CaptureRequest.CONTROL_AE_REGIONS,new MeteringRectangle[]{new MeteringRectangle(region,1000)});
                if(Build.VERSION.SDK_INT>=30){Range<Float> z=characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);if(z!=null&&z.contains(1f))b.set(CaptureRequest.CONTROL_ZOOM_RATIO,1f);}
                if(Build.VERSION.SDK_INT>=31&&has(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES),CaptureRequest.SCALER_ROTATE_AND_CROP_NONE))b.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
                session.setRepeatingRequest(b.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){readResult(result);}public void onCaptureFailed(CameraCaptureSession session,CaptureRequest request,CaptureFailure failure){measurement=null;window.reset();}},worker);runOnUiThread(()->{meterView.setMetering(centerRequested);transform();});
            }catch(Exception e){fail(e.getMessage());}}
            public void onConfigureFailed(CameraCaptureSession s){fail(tr("Camera preview unavailable. No reading saved.","相机预览不可用，未保存读数。"));}
        },worker);
    }catch(Exception e){fail(e.getMessage());}}
    private void unavailable(){
        window.reset();measurement=new Measurement(new CameraMeterFeedback.Frame(Double.NaN,SystemClock.elapsedRealtime(),0,0,false,false,0,0,Double.NaN),null,"");
    }
    private void readResult(TotalCaptureResult r){
        if(closing)return;
        try{
            Float aperture=r.get(CaptureResult.LENS_APERTURE);Long exposure=r.get(CaptureResult.SENSOR_EXPOSURE_TIME),frame=r.get(CaptureResult.SENSOR_TIMESTAMP);Integer iso=r.get(CaptureResult.SENSOR_SENSITIVITY),ae=r.get(CaptureResult.CONTROL_AE_STATE),comp=r.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION),boost=r.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
            boolean boostSupported=characteristics.getAvailableCaptureResultKeys().contains(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
            if(aperture==null||exposure==null||iso==null||frame==null||ae==null||comp==null||(boostSupported&&boost==null)){unavailable();return;}
            int gain=boost==null?100:boost;double ev=CameraExposure.ev100(aperture,exposure,iso,gain);long at=SystemClock.elapsedRealtime();
            Range<Long> times=characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);Range<Integer> sensitivities=characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            boolean limited=times!=null&&sensitivities!=null&&((exposure<=times.getLower()*1.02&&iso<=sensitivities.getLower())||(exposure>=times.getUpper()*0.98&&iso>=sensitivities.getUpper()));
            boolean settled=window.add(at,frame,ev,ae,comp,limited);
            CameraMeterFeedback.Frame feedback=new CameraMeterFeedback.Frame(ev,at,ae,comp,settled,limited,window.count(),window.duration(),window.max()-window.min());
            JSONObject j=null;
            if(settled){
                j=new JSONObject();j.put("source","android_camera2_ae");j.put("cameraId",cameraId);j.put("device",Build.MANUFACTURER+" "+Build.MODEL);j.put("aperture",aperture.doubleValue());j.put("exposureNs",exposure);j.put("iso",iso);j.put("postRawBoost",gain);j.put("aeState",ae);j.put("compensationSteps",comp);j.put("metering",centerRequested?"center_requested":"whole_frame");j.put("frameTimestampNs",Long.toString(frame));j.put("measuredAt",java.time.Instant.now().toString());j.put("ev100",ev);
                JSONObject samples=new JSONObject();samples.put("count",window.count());samples.put("windowMs",window.duration());samples.put("minEv",window.min());samples.put("maxEv",window.max());j.put("samples",samples);
            }
            String shutter=exposure>=1000000000L?String.format(Locale.US,"%.2f s",exposure/1e9):String.format(Locale.US,"1/%.0f s",1e9/exposure);
            String exposureText=String.format(Locale.US,"f/%.1f · %s · ISO %d · ×%.2f",aperture,shutter,iso,gain/100.0);
            measurement=new Measurement(feedback,j,exposureText);
        }catch(Exception e){unavailable();}
    }
    private double clippedFraction(){
        if(!texture.isAvailable())return Double.NaN;Bitmap bitmap=null;
        try{bitmap=texture.getBitmap(64,64);if(bitmap==null)return Double.NaN;int clipped=0,total=0;for(int y=26;y<38;y++)for(int x=26;x<38;x++){int c=bitmap.getPixel(x,y);if(Math.max(Color.red(c),Math.max(Color.green(c),Color.blue(c)))>=250)clipped++;total++;}return clipped/(double)total;}
        catch(RuntimeException e){return Double.NaN;}finally{if(bitmap!=null)bitmap.recycle();}
    }
    private final Runnable refresh=new Runnable(){public void run(){
        if(closing)return;
        Measurement m=measurement;long at=SystemClock.elapsedRealtime();
        CameraMeterFeedback.Phase phase=m==null?CameraMeterFeedback.Phase.WAITING:m.frame.phase(at,clippedFraction());
        meterView.render(m==null?null:m.frame,phase,at,m==null||!Double.isFinite(m.frame.displayEV(at))?"":m.exposure);
        ui.postDelayed(this,250);
    }};
    private void saveReading(){try{
        Measurement m=measurement;
        if(closing||m==null||m.candidate==null)return;
        double clipped=clippedFraction();long at=SystemClock.elapsedRealtime();
        if(m.frame.phase(at,clipped)!=CameraMeterFeedback.Phase.READY){meterView.render(m.frame,m.frame.phase(at,clipped),at,m.exposure);return;}
        JSONObject copy=new JSONObject(m.candidate.toString());copy.put("previewClippedFraction",clipped);
        measurement=null;setResult(RESULT_OK,new Intent().putExtra("meter",copy.toString()));finish();
    }catch(Exception e){fail(e.getMessage());}}
    private void transform(){if(size==null||texture.getWidth()==0||texture.getHeight()==0)return;int display=texture.getDisplay()==null?0:texture.getDisplay().getRotation()*90;Matrix m=new Matrix();m.setValues(CameraPreview.transform(texture.getWidth(),texture.getHeight(),size.getWidth(),size.getHeight(),orientation,display));texture.setTransform(m);boolean swap=Math.floorMod(orientation-display,180)!=0;float w=swap?size.getHeight():size.getWidth(),h=swap?size.getWidth():size.getHeight(),scale=Math.min(texture.getWidth()/w,texture.getHeight()/h);meterView.setFrame(w*scale,h*scale);}
    private void fail(String message){if(closing)return;runOnUiThread(()->{setResult(RESULT_CANCELED,new Intent().putExtra("error",message));finish();});}
    @Override protected void onPause(){super.onPause();closing=true;measurement=null;ui.removeCallbacks(refresh);if(worker!=null)worker.post(()->{if(session!=null)session.close();if(camera!=null)camera.close();if(surface!=null)surface.release();thread.quitSafely();});if(!isFinishing())finish();}
}
