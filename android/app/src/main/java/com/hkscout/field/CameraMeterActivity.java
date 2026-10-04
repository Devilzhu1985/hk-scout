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
    private TextureView texture;private Reticle reticle;private TextView value,status,details;private Button save;
    private HandlerThread thread;private Handler worker;private final Handler ui=new Handler(Looper.getMainLooper());
    private CameraDevice camera;private CameraCaptureSession session;private Surface surface;
    private CameraCharacteristics characteristics;private Size size;private String cameraId;private int orientation;
    private boolean chinese,centerRequested;private volatile boolean closing;private volatile JSONObject latest;
    private volatile long latestAt;private volatile String problem="";private Rect region;
    private final CameraExposure.Window window=new CameraExposure.Window();
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){transform();}};
    private String tr(String en,String zh){return chinese?zh:en;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);chinese="zh".equals(getIntent().getStringExtra("language"));getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button cancel=new Button(this);cancel.setText("×");cancel.setTextSize(26);cancel.setContentDescription(tr("Cancel camera measurement","取消相机测光"));cancel.setOnClickListener(v->finish());top.addView(cancel,new LinearLayout.LayoutParams(dp(52),dp(48)));
        TextView title=new TextView(this);title.setText(tr("REAR CAMERA · EV100","后置相机 · EV100"));title.setTextColor(Color.WHITE);title.setTextSize(15);top.addView(title);root.addView(top);
        FrameLayout viewfinder=new FrameLayout(this);texture=new TextureView(this);texture.setOpaque(false);texture.setSurfaceTextureListener(this);viewfinder.addView(texture,new FrameLayout.LayoutParams(-1,-1));reticle=new Reticle(this);viewfinder.addView(reticle,new FrameLayout.LayoutParams(-1,-1));root.addView(viewfinder,new LinearLayout.LayoutParams(-1,0,1));
        value=text(root,"—",38);details=text(root,tr("Checking exposure metadata…","正在读取曝光数据…"),11);status=text(root,tr("Aim at a lit surface, then hold still.","对准受光表面，然后保持稳定。"),12);
        text(root,tr("Reflected-light estimate, not lux. Bright lamps or the sun can exceed the camera's range.","反射光曝光估算，不是照度 lux。灯泡或太阳可能超出相机测量范围。"),11);
        save=new Button(this);save.setText(tr("Save EV to slate","保存 EV 到识别板"));save.setEnabled(false);LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(-1,dp(52));saveParams.setMargins(dp(16),dp(6),dp(16),dp(12));root.addView(save,saveParams);save.setOnClickListener(v->saveReading());
        setContentView(root);thread=new HandlerThread("ScoutCameraEV");thread.start();worker=new Handler(thread.getLooper());ui.post(refresh);
    }
    private TextView text(LinearLayout parent,String content,int sp){TextView t=new TextView(this);t.setText(content);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(4),dp(14),dp(4));parent.addView(t);return t;}
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();((DisplayManager)getSystemService(DISPLAY_SERVICE)).registerDisplayListener(displayListener,ui);}
    @Override public void onDetachedFromWindow(){((DisplayManager)getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);super.onDetachedFromWindow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){worker.post(this::open);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){latest=null;return true;}
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
                session.setRepeatingRequest(b.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){readResult(result);}public void onCaptureFailed(CameraCaptureSession session,CaptureRequest request,CaptureFailure failure){latest=null;window.reset();}},worker);runOnUiThread(()->{reticle.center=centerRequested;transform();});
            }catch(Exception e){fail(e.getMessage());}}
            public void onConfigureFailed(CameraCaptureSession s){fail(tr("Camera preview unavailable. No reading saved.","相机预览不可用，未保存读数。"));}
        },worker);
    }catch(Exception e){fail(e.getMessage());}}
    private void readResult(TotalCaptureResult r){
        if(closing)return;
        try{
            Float aperture=r.get(CaptureResult.LENS_APERTURE);Long exposure=r.get(CaptureResult.SENSOR_EXPOSURE_TIME),frame=r.get(CaptureResult.SENSOR_TIMESTAMP);Integer iso=r.get(CaptureResult.SENSOR_SENSITIVITY),ae=r.get(CaptureResult.CONTROL_AE_STATE),comp=r.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION),boost=r.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
            boolean boostSupported=characteristics.getAvailableCaptureResultKeys().contains(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
            if(aperture==null||exposure==null||iso==null||frame==null||ae==null||comp==null||(boostSupported&&boost==null)){latest=null;window.reset();problem=tr("Exposure metadata unavailable; no EV saved.","缺少曝光数据，无法保存 EV。");return;}
            int gain=boost==null?100:boost;double ev=CameraExposure.ev100(aperture,exposure,iso,gain);long at=SystemClock.elapsedRealtime();
            Range<Long> times=characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);Range<Integer> sensitivities=characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            boolean limited=times!=null&&sensitivities!=null&&((exposure<=times.getLower()*1.02&&iso<=sensitivities.getLower())||(exposure>=times.getUpper()*0.98&&iso>=sensitivities.getUpper()));
            if(!window.add(at,frame,ev,ae,comp,limited)){latest=null;problem=limited?tr("Beyond the exposure range. Aim at a lit surface.","已到曝光极限，请对准受光表面。"):ae!=2?tr("Exposure is not settled. Hold still or choose another surface.","曝光尚未稳定，请保持不动或换一个受光表面。"):tr("Hold still while the reading settles…","请保持稳定，等待读数收敛…");return;}
            JSONObject j=new JSONObject();j.put("source","android_camera2_ae");j.put("cameraId",cameraId);j.put("device",Build.MANUFACTURER+" "+Build.MODEL);j.put("aperture",aperture.doubleValue());j.put("exposureNs",exposure);j.put("iso",iso);j.put("postRawBoost",gain);j.put("aeState",ae);j.put("compensationSteps",comp);j.put("metering",centerRequested?"center_requested":"whole_frame");j.put("frameTimestampNs",Long.toString(frame));j.put("measuredAt",java.time.Instant.now().toString());j.put("ev100",ev);
            JSONObject samples=new JSONObject();samples.put("count",window.count());samples.put("windowMs",window.duration());samples.put("minEv",window.min());samples.put("maxEv",window.max());j.put("samples",samples);latestAt=at;latest=j;
        }catch(Exception e){latest=null;window.reset();problem=tr("Exposure metadata unavailable; no EV saved.","缺少曝光数据，无法保存 EV。");}
    }
    private double clippedFraction(){
        if(!texture.isAvailable())return Double.NaN;Bitmap bitmap=null;
        try{bitmap=texture.getBitmap(64,64);if(bitmap==null)return Double.NaN;int clipped=0,total=0;for(int y=26;y<38;y++)for(int x=26;x<38;x++){int c=bitmap.getPixel(x,y);if(Math.max(Color.red(c),Math.max(Color.green(c),Color.blue(c)))>=250)clipped++;total++;}return clipped/(double)total;}
        catch(RuntimeException e){return Double.NaN;}finally{if(bitmap!=null)bitmap.recycle();}
    }
    private final Runnable refresh=new Runnable(){public void run(){if(closing)return;JSONObject j=latest;boolean fresh=j!=null&&SystemClock.elapsedRealtime()-latestAt<=750;
        if(fresh){double clipped=clippedFraction();value.setText(String.format(Locale.US,"%.2f EV100",j.optDouble("ev100")));details.setText(String.format(Locale.US,"f/%.1f · %.6f s · ISO %d · ×%.2f",j.optDouble("aperture"),j.optLong("exposureNs")/1e9,j.optInt("iso"),j.optInt("postRawBoost")/100.0));save.setEnabled(Double.isFinite(clipped)&&clipped<=0.2);status.setText(!Double.isFinite(clipped)?tr("Waiting for the camera preview…","等待相机预览…"):clipped>0.2?tr("Preview color is near clipping. Aim at a lit surface, away from the lamp.","预览颜色接近饱和，请对准受光表面，避开灯泡本身。"):tr(centerRequested?"Stable · center region requested · camera algorithm":"Stable · whole-frame AE; center control unavailable",centerRequested?"读数稳定 · 已请求中央区域测光 · 相机算法":"读数稳定 · 全画面测光；不支持中央区域控制"));}
        else{value.setText("—");save.setEnabled(false);status.setText(problem.isEmpty()?tr("Waiting for a stable exposure…","等待曝光稳定…"):problem);}
        ui.postDelayed(this,250);
    }};
    private void saveReading(){try{JSONObject j=latest;if(j==null||SystemClock.elapsedRealtime()-latestAt>750){save.setEnabled(false);return;}double clipped=clippedFraction();if(!Double.isFinite(clipped)||clipped>0.2){save.setEnabled(false);return;}JSONObject copy=new JSONObject(j.toString());copy.put("previewClippedFraction",clipped);save.setEnabled(false);setResult(RESULT_OK,new Intent().putExtra("meter",copy.toString()));finish();}catch(Exception e){fail(e.getMessage());}}
    private void transform(){if(size==null||texture.getWidth()==0||texture.getHeight()==0)return;int display=texture.getDisplay()==null?0:texture.getDisplay().getRotation()*90;Matrix m=new Matrix();m.setValues(CameraPreview.transform(texture.getWidth(),texture.getHeight(),size.getWidth(),size.getHeight(),orientation,display));texture.setTransform(m);boolean swap=Math.floorMod(orientation-display,180)!=0;float w=swap?size.getHeight():size.getWidth(),h=swap?size.getWidth():size.getHeight(),scale=Math.min(texture.getWidth()/w,texture.getHeight()/h);reticle.frameWidth=w*scale;reticle.frameHeight=h*scale;reticle.invalidate();}
    private void fail(String message){if(closing)return;runOnUiThread(()->{setResult(RESULT_CANCELED,new Intent().putExtra("error",message));finish();});}
    @Override protected void onPause(){super.onPause();closing=true;latest=null;ui.removeCallbacks(refresh);if(worker!=null)worker.post(()->{if(session!=null)session.close();if(camera!=null)camera.close();if(surface!=null)surface.release();thread.quitSafely();});if(!isFinishing())finish();}
    private final class Reticle extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float frameWidth,frameHeight;boolean center;
        Reticle(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);if(center)c.drawRect(cx-frameWidth*.1f,cy-frameHeight*.1f,cx+frameWidth*.1f,cy+frameHeight*.1f,paint);c.drawLine(cx-dp(7),cy,cx+dp(7),cy,paint);c.drawLine(cx,cy-dp(7),cx,cy+dp(7),paint);}
    }
}
