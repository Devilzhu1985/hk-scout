package com.hkscout.field;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.json.*;

/** Camera2 capture, never an undocumented intent into an OEM camera's Pro mode.
 * RAW is sensor data; JPEG is a processed companion. Kelvin is exposed only when
 * the camera advertises the API 36 CCT request, result and supported range. */
public class ReferenceCameraActivity extends Activity implements TextureView.SurfaceTextureListener {
    private TextureView texture;
    private TextView status;
    private Button shutter;
    private CheckBox rawToggle;
    private Spinner whiteBalance;
    private EditText kelvin;
    private boolean chinese,rawEnabled,cctAvailable;
    private volatile boolean closing,busy;
    private final ArrayList<Integer> wbModes=new ArrayList<>();
    private HandlerThread thread;
    private Handler worker;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CameraCharacteristics characteristics;
    private ImageReader jpegReader,rawReader;
    private Surface previewSurface;
    private Size jpegSize,rawSize,previewSize;
    private Range<Integer> kelvinRange;
    private String cameraId;
    private int requestedWb=CaptureRequest.CONTROL_AWB_MODE_AUTO,requestedKelvin=5500;
    private TotalCaptureResult captureResult;
    private Image rawImage;
    private byte[] jpegBytes;
    private long jpegTimestamp;
    private String captureId;
    private int orientation;
    private final Runnable timeout=()->fail("Camera capture timed out. Please try again.","拍摄超时，请重试。");

    private String tr(String en,String zh){return chinese?zh:en;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);chinese="zh".equals(getIntent().getStringExtra("language"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(18,18,18,18);layout.setBackgroundColor(Color.rgb(244,243,236));
        // Insets keep controls clear of Android 15/16 edge-to-edge system bars.
        layout.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(18,insets.getSystemWindowInsetTop()+12,18,insets.getSystemWindowInsetBottom()+12);return insets;});
        TextView title=new TextView(this);title.setText(tr("Scout reference camera","Scout 参考相机"));title.setTextSize(23);layout.addView(title);
        texture=new TextureView(this);texture.setSurfaceTextureListener(this);layout.addView(texture,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);LinearLayout controls=new LinearLayout(this);controls.setOrientation(1);scroll.addView(controls);layout.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(260*getResources().getDisplayMetrics().density)));
        status=new TextView(this);status.setText(tr("Checking camera…","正在检查相机…"));controls.addView(status);
        rawToggle=new CheckBox(this);rawToggle.setText("RAW DNG + JPEG");rawToggle.setEnabled(false);controls.addView(rawToggle);
        whiteBalance=new Spinner(this);controls.addView(whiteBalance);
        kelvin=new EditText(this);kelvin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);kelvin.setText("5500");kelvin.setHint(tr("White balance K (not measured CCT)","白平衡 K（非实测色温）"));kelvin.setEnabled(false);controls.addView(kelvin);
        Button apply=new Button(this);apply.setText(tr("Apply white balance","应用白平衡"));controls.addView(apply);apply.setOnClickListener(v->applyWhiteBalance());
        shutter=new Button(this);shutter.setText(tr("Take reference photo","拍摄参考照片"));shutter.setEnabled(false);controls.addView(shutter);shutter.setOnClickListener(v->capture());
        Button cancel=new Button(this);cancel.setText(tr("Cancel","取消"));controls.addView(cancel);cancel.setOnClickListener(v->finish());setContentView(layout);
        thread=new HandlerThread("ScoutCamera");thread.start();worker=new Handler(thread.getLooper());
    }
    private static boolean contains(int[] values,int target){if(values!=null)for(int v:values)if(v==target)return true;return false;}
    private static Size largest(Size[] sizes,long maxPixels){if(sizes==null)return null;return Arrays.stream(sizes).filter(s->(long)s.getWidth()*s.getHeight()<=maxPixels).max(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight())).orElse(null);}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){worker.post(this::openCamera);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
    private void openCamera(){try{
        CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        // Prefer a rear camera exposing RAW; all selection remains device-derived.
        int best=-1;
        for(String id:manager.getCameraIdList()){
            CameraCharacteristics c=manager.getCameraCharacteristics(id);
            if(!Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK))continue;
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null||largest(map.getOutputSizes(ImageFormat.JPEG),24000000)==null)continue;
            boolean raw=contains(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)&&largest(map.getOutputSizes(ImageFormat.RAW_SENSOR),24000000)!=null;
            int score=raw?2:1;if(score>best){best=score;cameraId=id;characteristics=c;}
        }
        if(cameraId==null)throw new Exception("No supported rear camera");
        StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        jpegSize=largest(map.getOutputSizes(ImageFormat.JPEG),24000000);
        rawSize=contains(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)?largest(map.getOutputSizes(ImageFormat.RAW_SENSOR),24000000):null;
        Size[] previewSizes=map.getOutputSizes(SurfaceTexture.class);
        previewSize=Arrays.stream(previewSizes).filter(s->s.getWidth()<=1920&&s.getHeight()<=1080).min(Comparator.comparingDouble(s->Math.abs((double)s.getWidth()/s.getHeight()-(double)jpegSize.getWidth()/jpegSize.getHeight()))).orElse(previewSizes[0]);
        orientation=Optional.ofNullable(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).orElse(90);
        if(Build.VERSION.SDK_INT>=36){
            kelvinRange=characteristics.get(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE);
            cctAvailable=kelvinRange!=null&&contains(characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES),CaptureRequest.COLOR_CORRECTION_MODE_CCT)&&contains(characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),CaptureRequest.CONTROL_AWB_MODE_OFF)&&characteristics.getAvailableCaptureRequestKeys().contains(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE)&&characteristics.getAvailableCaptureResultKeys().contains(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE);
        }
        jpegReader=ImageReader.newInstance(jpegSize.getWidth(),jpegSize.getHeight(),ImageFormat.JPEG,2);
        jpegReader.setOnImageAvailableListener(reader->{try(Image image=reader.acquireNextImage()){if(image==null||!busy)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();jpegBytes=new byte[buffer.remaining()];buffer.get(jpegBytes);jpegTimestamp=image.getTimestamp();finishCapture();}catch(Exception e){fail(e.getMessage(),"无法保存 JPEG："+e.getMessage());}},worker);
        if(rawSize!=null){rawReader=ImageReader.newInstance(rawSize.getWidth(),rawSize.getHeight(),ImageFormat.RAW_SENSOR,2);rawReader.setOnImageAvailableListener(reader->{Image image=reader.acquireNextImage();if(image==null)return;if(!busy){image.close();return;}if(rawImage!=null)rawImage.close();rawImage=image;finishCapture();},worker);}
        runOnUiThread(this::configureControls);
        if(closing)return;
        manager.openCamera(cameraId,new CameraDevice.StateCallback(){
            public void onOpened(CameraDevice device){if(closing){device.close();return;}camera=device;createSession();}
            public void onDisconnected(CameraDevice device){device.close();fail("Camera disconnected","相机连接中断");}
            public void onError(CameraDevice device,int error){device.close();fail("Camera error "+error,"相机错误 "+error);}
        },worker);
    }catch(Exception e){fail(e.getMessage(),"无法打开相机："+e.getMessage());}}
    private void configureControls(){
        rawToggle.setEnabled(rawSize!=null);rawToggle.setChecked(rawSize!=null);
        rawToggle.setText(rawSize==null?tr("RAW not exposed at a supported size (JPEG only)","此相机未提供支持尺寸的 RAW（仅 JPEG）"):"RAW DNG "+rawSize+" + JPEG "+jpegSize);
        ArrayList<String> labels=new ArrayList<>();int[] available=characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        int[] candidates={1,2,3,4,5,6,7,8};String[] en={"Auto white balance","Incandescent","Fluorescent","Warm fluorescent","Daylight","Cloudy","Twilight","Shade"};String[] zh={"自动白平衡","白炽灯","荧光灯","暖荧光灯","日光","阴天","暮光","阴影"};
        for(int i=0;i<candidates.length;i++)if(contains(available,candidates[i])){wbModes.add(candidates[i]);labels.add(chinese?zh[i]:en[i]);}
        if(cctAvailable){wbModes.add(-1);labels.add(tr("Manual Kelvin","手动色温 K"));requestedKelvin=kelvinRange.clamp(5500);kelvin.setText(""+requestedKelvin);}
        whiteBalance.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        whiteBalance.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){kelvin.setEnabled(wbModes.get(i)==-1);}public void onNothingSelected(AdapterView<?> p){}});
        status.setText(tr("JPEG is processed. RAW keeps sensor data; WB remains editable. ","JPEG 已经过处理；RAW 保留传感器数据，白平衡可后期调整。 ")+(cctAvailable?tr("Manual K range: ","手动 K 范围：")+kelvinRange:tr("This camera does not expose manual Kelvin. Use a supported WB preset, or the phone camera’s Pro mode and import its files.","此相机未开放手动 K。可选白平衡预设，或在系统相机专业模式拍摄后导入。")));
        transform();
    }
    private void transform(){if(previewSize==null||texture.getWidth()==0)return;
        // Keep the sensor aspect ratio and rotation instead of stretching preview.
        float w=texture.getWidth(),h=texture.getHeight();Matrix matrix=new Matrix();
        matrix.setScale(previewSize.getWidth()/w,previewSize.getHeight()/h);
        matrix.postRotate(orientation);RectF bounds=new RectF(0,0,w,h);matrix.mapRect(bounds);
        float scale=Math.min(w/bounds.width(),h/bounds.height());matrix.postScale(scale,scale);
        matrix.postTranslate(w/2-bounds.centerX()*scale,h/2-bounds.centerY()*scale);
        texture.setTransform(matrix);
    }
    private void createSession(){try{
        texture.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());previewSurface=new Surface(texture.getSurfaceTexture());
        ArrayList<Surface> outputs=new ArrayList<>(Arrays.asList(previewSurface,jpegReader.getSurface()));if(rawReader!=null)outputs.add(rawReader.getSurface());
        camera.createCaptureSession(outputs,new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(closing){s.close();return;}session=s;preview();runOnUiThread(()->shutter.setEnabled(true));}public void onConfigureFailed(CameraCaptureSession s){fail("Camera stream combination unavailable. Use the phone camera and import its files.","此相机不支持所需输出组合。请使用系统相机拍摄后导入。");}},worker);
    }catch(Exception e){fail(e.getMessage(),"相机配置失败："+e.getMessage());}}
    private void configure(CaptureRequest.Builder builder){
        builder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        int[] focus=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);if(contains(focus,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AWB_MODE,requestedWb==-1?CaptureRequest.CONTROL_AWB_MODE_OFF:requestedWb);
        if(requestedWb==-1&&cctAvailable&&Build.VERSION.SDK_INT>=36){builder.set(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_CCT);builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE,requestedKelvin);builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT,0);}
        builder.set(CaptureRequest.CONTROL_EFFECT_MODE,CaptureRequest.CONTROL_EFFECT_MODE_OFF);
        builder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
    }
    private void preview(){try{if(closing||session==null)return;CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(previewSurface);configure(b);session.setRepeatingRequest(b.build(),null,worker);}catch(Exception e){fail(e.getMessage(),"预览失败："+e.getMessage());}}
    private boolean readWhiteBalance(){try{
        if(wbModes.isEmpty())return false;int mode=wbModes.get(whiteBalance.getSelectedItemPosition());int k=requestedKelvin;
        if(mode==-1){k=Integer.parseInt(kelvin.getText().toString());if(!kelvinRange.contains(k))throw new IllegalArgumentException();}
        requestedWb=mode;requestedKelvin=k;return true;
    }catch(Exception e){Toast.makeText(this,tr("Enter Kelvin within ","请输入范围内的色温 K：")+kelvinRange,Toast.LENGTH_LONG).show();return false;}}
    private void applyWhiteBalance(){if(!busy&&readWhiteBalance())worker.post(this::preview);}
    private void capture(){if(busy||closing||!readWhiteBalance())return;rawEnabled=rawToggle.isChecked()&&rawReader!=null;busy=true;shutter.setEnabled(false);rawToggle.setEnabled(false);whiteBalance.setEnabled(false);kelvin.setEnabled(false);worker.post(()->{try{
        captureId=UUID.randomUUID().toString();jpegBytes=null;captureResult=null;session.stopRepeating();
        CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);configure(b);b.addTarget(jpegReader.getSurface());if(rawEnabled)b.addTarget(rawReader.getSurface());b.set(CaptureRequest.JPEG_ORIENTATION,orientation);b.set(CaptureRequest.JPEG_QUALITY,(byte)100);
        worker.postDelayed(timeout,20000);session.capture(b.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest request,TotalCaptureResult result){captureResult=result;finishCapture();}public void onCaptureFailed(CameraCaptureSession s,CaptureRequest request,CaptureFailure failure){fail("Capture failed; no reference saved","拍摄失败，未保存参考照片");}},worker);
    }catch(Exception e){fail(e.getMessage(),"拍摄失败："+e.getMessage());}});}
    private void finishCapture(){if(!busy||closing||captureResult==null||jpegBytes==null||(rawEnabled&&rawImage==null))return;try{
        Long time=captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
        if(time==null||time!=jpegTimestamp||(rawEnabled&&time!=rawImage.getTimestamp()))throw new Exception("Capture metadata and image timestamps do not match");
        worker.removeCallbacks(timeout);
        File dir=new File(getFilesDir(),"reference-captures");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create capture directory");
        File jpeg=new File(dir,captureId+".jpg");try(FileOutputStream out=new FileOutputStream(jpeg)){out.write(jpegBytes);out.getFD().sync();}
        JSONArray files=new JSONArray();files.put(fileInfo(jpeg,"image/jpeg"));
        if(rawEnabled){File raw=new File(dir,captureId+".dng");try(DngCreator creator=new DngCreator(characteristics,captureResult);FileOutputStream out=new FileOutputStream(raw)){creator.setOrientation(orientation==90?6:orientation==270?8:orientation==180?3:1);creator.writeImage(out,rawImage);out.getFD().sync();}finally{rawImage.close();rawImage=null;}files.put(fileInfo(raw,"image/x-adobe-dng"));}
        JSONObject result=new JSONObject();result.put("id",captureId);result.put("setId",getIntent().getStringExtra("setId"));result.put("files",files);result.put("capturedAt",java.time.Instant.now().toString());result.put("device",Build.MANUFACTURER+" "+Build.MODEL);result.put("cameraId",cameraId);result.put("raw",rawEnabled);result.put("requestedWbMode",requestedWb);result.put("actualAwbMode",captureResult.get(CaptureResult.CONTROL_AWB_MODE));result.put("iso",captureResult.get(CaptureResult.SENSOR_SENSITIVITY));result.put("exposureNs",captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME));
        if(requestedWb==-1){result.put("requestedKelvin",requestedKelvin);if(Build.VERSION.SDK_INT>=36){result.put("actualKelvin",captureResult.get(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE));result.put("actualColorMode",captureResult.get(CaptureResult.COLOR_CORRECTION_MODE));}}
        File partial=new File(dir,captureId+".tmp"),manifest=new File(dir,captureId+".json");try(FileOutputStream out=new FileOutputStream(partial)){out.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}if(!partial.renameTo(manifest))throw new IOException("Could not commit capture manifest");
        busy=false;runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("id",captureId));finish();});
    }catch(Exception e){fail("Capture could not be completed: "+e.getMessage(),"未能完成拍摄："+e.getMessage());}}
    private JSONObject fileInfo(File file,String mime)throws Exception {if(file.length()>52L*1024*1024)throw new IOException("Image exceeds notebook size limit");JSONObject f=new JSONObject();f.put("path",file.getAbsolutePath());f.put("name","Scout-"+captureId+(mime.equals("image/jpeg")?".jpg":".dng"));f.put("mime",mime);return f;}
    private void fail(String en,String zh){if(closing)return;runOnUiThread(()->{setResult(RESULT_CANCELED,new Intent().putExtra("error",tr(en,zh)));finish();});}
    @Override protected void onPause(){super.onPause();closing=true;if(worker!=null)worker.post(()->{worker.removeCallbacks(timeout);if(session!=null)session.close();if(camera!=null)camera.close();if(rawImage!=null){rawImage.close();rawImage=null;}if(jpegReader!=null)jpegReader.close();if(rawReader!=null)rawReader.close();if(previewSurface!=null)previewSurface.release();thread.quitSafely();});if(!isFinishing())finish();}
}
