package com.hkscout.field;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
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
    private Button shutter,wbButton,wideButton;
    private volatile float requestedZoom=1f;
    private float wideZoom=1f;
    private String cameraHelp="";
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
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){if(texture!=null&&texture.getDisplay()!=null&&texture.getDisplay().getDisplayId()==id)transform();}
    };
    private final Runnable timeout=()->fail("Camera capture timed out. Please try again.","拍摄超时，请重试。");

    private String tr(String en,String zh){return chinese?zh:en;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);chinese="zh".equals(getIntent().getStringExtra("language"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.BLACK);
        layout.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),0,dp(8),0);
        Button cancel=new Button(this);cancel.setText("×");cancel.setTextSize(26);cancel.setContentDescription(tr("Close camera","关闭相机"));cancel.setOnClickListener(v->finish());top.addView(cancel,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=new TextView(this);title.setText("SCOUT");title.setTextColor(Color.WHITE);title.setTextSize(16);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        rawToggle=new CheckBox(this);rawToggle.setText("RAW + JPEG");rawToggle.setTextColor(Color.WHITE);rawToggle.setTextSize(12);rawToggle.setEnabled(false);top.addView(rawToggle,new LinearLayout.LayoutParams(-2,dp(48)));layout.addView(top);
        FrameLayout viewfinder=new FrameLayout(this);viewfinder.setBackgroundColor(Color.BLACK);
        texture=new TextureView(this);texture.setOpaque(false);texture.setSurfaceTextureListener(this);viewfinder.addView(texture,new FrameLayout.LayoutParams(-1,-1));layout.addView(viewfinder,new LinearLayout.LayoutParams(-1,0,1));
        wideButton=new Button(this);wideButton.setVisibility(View.GONE);wideButton.setTextColor(Color.WHITE);wideButton.setBackgroundColor(Color.rgb(35,45,37));wideButton.setOnClickListener(v->{if(busy)return;requestedZoom=requestedZoom<1f?1f:wideZoom;if(requestedZoom<1f){rawToggle.setChecked(false);Toast.makeText(this,tr("Wide framing saves JPEG. Select Main for RAW.","广角画面保存为 JPEG；拍摄 RAW 请切换主摄。"),Toast.LENGTH_LONG).show();}rawToggle.setEnabled(rawSize!=null&&requestedZoom==1f);wideButton.setText(requestedZoom<1f?tr("Wide · ","广角 · ")+String.format(Locale.US,"%.2f×",requestedZoom)+tr("  → Main","  → 主摄"):tr("Main · 1×  → Wide","主摄 · 1×  → 广角"));worker.post(this::preview);});layout.addView(wideButton,new LinearLayout.LayoutParams(-1,dp(44)));
        status=new TextView(this);status.setText(tr("Checking camera…","正在检查相机…"));status.setTextColor(Color.WHITE);status.setTextSize(12);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(8),dp(12),dp(8));layout.addView(status);
        whiteBalance=new Spinner(this);kelvin=new EditText(this);kelvin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);kelvin.setText("5500");kelvin.setHint(tr("White balance K","白平衡 K"));kelvin.setEnabled(false);
        LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(12),dp(4),dp(12),dp(4));controls.setGravity(Gravity.CENTER);
        wbButton=new Button(this);wbButton.setText("WB\nAUTO");wbButton.setTextSize(12);wbButton.setEnabled(false);wbButton.setOnClickListener(v->whiteBalanceDialog());controls.addView(wbButton,new LinearLayout.LayoutParams(0,dp(60),1));
        FrameLayout shutterArea=new FrameLayout(this);controls.addView(shutterArea,new LinearLayout.LayoutParams(0,dp(88),1));
        shutter=new Button(this);shutter.setText("");shutter.setContentDescription(tr("Take reference photo","拍摄参考照片"));shutter.setEnabled(false);
        android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(Color.WHITE);circle.setStroke(dp(5),Color.rgb(160,174,143));shutter.setBackground(circle);shutter.setBackgroundTintList(null);
        shutterArea.addView(shutter,new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.CENTER));shutter.setOnClickListener(v->capture());
        Button info=new Button(this);info.setText(tr("INFO","说明"));info.setTextSize(12);info.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(tr("Camera & originals","相机与原片")).setMessage(cameraHelp).setPositiveButton(tr("Close","关闭"),null).show());controls.addView(info,new LinearLayout.LayoutParams(0,dp(60),1));layout.addView(controls);
        TextView savedTo=new TextView(this);savedTo.setText(tr("Originals → DCIM/Camera + Scout","原片 → DCIM/Camera + Scout"));savedTo.setTextColor(Color.LTGRAY);savedTo.setTextSize(11);savedTo.setGravity(Gravity.CENTER);savedTo.setPadding(0,0,0,dp(10));layout.addView(savedTo);setContentView(layout);
        thread=new HandlerThread("ScoutCamera");thread.start();worker=new Handler(thread.getLooper());
    }
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();((DisplayManager)getSystemService(DISPLAY_SERVICE)).registerDisplayListener(displayListener,new Handler(Looper.getMainLooper()));}
    @Override public void onDetachedFromWindow(){((DisplayManager)getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);super.onDetachedFromWindow();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void whiteBalanceDialog(){if(busy||wbModes.isEmpty())return;
        if(whiteBalance.getParent()!=null)((ViewGroup)whiteBalance.getParent()).removeView(whiteBalance);
        if(kelvin.getParent()!=null)((ViewGroup)kelvin.getParent()).removeView(kelvin);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(8),dp(20),dp(8));panel.addView(whiteBalance);panel.addView(kelvin);
        TextView help=new TextView(this);help.setText(tr("Kelvin sets white balance; it does not measure the light's color temperature.","K 设置白平衡，并非测量现场光源色温。"));help.setPadding(0,dp(10),0,0);panel.addView(help);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(tr("White balance","白平衡")).setView(panel).setPositiveButton(tr("Apply","应用"),null).setNegativeButton(tr("Cancel","取消"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(readWhiteBalance()){worker.post(this::preview);updateWbLabel();dialog.dismiss();}}));
        dialog.setOnDismissListener(d->{int index=wbModes.indexOf(requestedWb);if(index>=0)whiteBalance.setSelection(index);kelvin.setText(""+requestedKelvin);});dialog.show();
    }
    private void updateWbLabel(){wbButton.setText(requestedWb==-1?"WB\n"+requestedKelvin+" K":"WB\n"+(requestedWb==1?"AUTO":whiteBalance.getSelectedItem().toString()));}
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
        if(Build.VERSION.SDK_INT>=30){Range<Float> zoom=characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);if(zoom!=null&&zoom.getLower()>0f&&zoom.getLower()<1f&&characteristics.getAvailableCaptureRequestKeys().contains(CaptureRequest.CONTROL_ZOOM_RATIO))wideZoom=zoom.getLower();}
        StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        jpegSize=largest(map.getOutputSizes(ImageFormat.JPEG),24000000);
        rawSize=contains(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)?largest(map.getOutputSizes(ImageFormat.RAW_SENSOR),24000000):null;
        Size[] previewSizes=map.getOutputSizes(SurfaceTexture.class);
        if(previewSizes==null||previewSizes.length==0)throw new Exception("No preview stream available");
        int[][] dimensions=Arrays.stream(previewSizes).map(s->new int[]{s.getWidth(),s.getHeight()}).toArray(int[][]::new);
        previewSize=previewSizes[CameraPreview.chooseSize(dimensions,jpegSize.getWidth(),jpegSize.getHeight())];
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
        if(wideZoom<1f){wideButton.setVisibility(View.VISIBLE);wideButton.setText(tr("Main · 1×  → Wide","主摄 · 1×  → 广角"));}
        rawToggle.setText(rawSize==null?"JPEG":"RAW + JPEG");
        ArrayList<String> labels=new ArrayList<>();int[] available=characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        int[] candidates={1,2,3,4,5,6,7,8};String[] en={"Auto white balance","Incandescent","Fluorescent","Warm fluorescent","Daylight","Cloudy","Twilight","Shade"};String[] zh={"自动白平衡","白炽灯","荧光灯","暖荧光灯","日光","阴天","暮光","阴影"};
        for(int i=0;i<candidates.length;i++)if(contains(available,candidates[i])){wbModes.add(candidates[i]);labels.add(chinese?zh[i]:en[i]);}
        if(cctAvailable){wbModes.add(-1);labels.add(tr("Manual Kelvin","手动色温 K"));requestedKelvin=kelvinRange.clamp(5500);kelvin.setText(""+requestedKelvin);}
        whiteBalance.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        whiteBalance.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){kelvin.setEnabled(wbModes.get(i)==-1);kelvin.setVisibility(wbModes.get(i)==-1?View.VISIBLE:View.GONE);}public void onNothingSelected(AdapterView<?> p){}});
        cameraHelp=tr("JPEG is processed. RAW keeps sensor data; WB remains editable. ","JPEG 已经过处理；RAW 保留传感器数据，白平衡可后期调整。 ")+(cctAvailable?tr("Manual K range: ","手动 K 范围：")+kelvinRange:tr("This camera does not expose manual Kelvin. Use a supported WB preset, or the phone camera’s Pro mode and import its files.","此相机未开放手动 K。可选白平衡预设，或在系统相机专业模式拍摄后导入。"));
        cameraHelp+="\n\n"+tr("Wide framing is offered only when this camera exposes zoom below 1×. It saves JPEG; RAW keeps the sensor framing. Other lenses may require the system camera.","仅当此相机开放低于 1× 的变焦时提供广角构图，并保存 JPEG；RAW 保留传感器画幅。其他镜头可能需要使用系统相机。 ");
        cameraHelp+="\n\nJPEG "+jpegSize+(rawSize!=null?" · DNG "+rawSize:"")+"\n\n"+tr("Originals are copied unchanged to DCIM/Camera and linked to this Scout capture. Gallery apps may display only the JPEG preview of a RAW pair.","原片原样保存至 DCIM/Camera，并关联此次 Scout 记录。部分相册只显示 RAW 配对中的 JPEG 预览。");
        status.setText(tr("Auto exposure · autofocus · rear camera","自动曝光 · 自动对焦 · 后置相机"));wbButton.setEnabled(!wbModes.isEmpty());
        transform();
    }
    private void transform(){if(previewSize==null||texture.getWidth()==0||texture.getHeight()==0)return;
        // TextureView already rotates the sensor buffer. Rotating it again turns
        // the scene sideways and uses the wrong axes to undo its default stretch.
        int displayDegrees=texture.getDisplay()==null?0:texture.getDisplay().getRotation()*90;
        Matrix matrix=new Matrix();matrix.setValues(CameraPreview.transform(texture.getWidth(),texture.getHeight(),previewSize.getWidth(),previewSize.getHeight(),orientation,displayDegrees));
        texture.setTransform(matrix);
    }
    private void createSession(){try{
        texture.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());previewSurface=new Surface(texture.getSurfaceTexture());
        ArrayList<Surface> outputs=new ArrayList<>(Arrays.asList(previewSurface,jpegReader.getSurface()));if(rawReader!=null)outputs.add(rawReader.getSurface());
        camera.createCaptureSession(outputs,new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(closing){s.close();return;}session=s;preview();runOnUiThread(()->shutter.setEnabled(true));}public void onConfigureFailed(CameraCaptureSession s){fail("Camera stream combination unavailable. Use the phone camera and import its files.","此相机不支持所需输出组合。请使用系统相机拍摄后导入。");}},worker);
    }catch(Exception e){fail(e.getMessage(),"相机配置失败："+e.getMessage());}}
    private void configure(CaptureRequest.Builder builder){
        builder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        if(Build.VERSION.SDK_INT>=30&&wideZoom<1f)builder.set(CaptureRequest.CONTROL_ZOOM_RATIO,requestedZoom);
        int[] focus=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);if(contains(focus,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AWB_MODE,requestedWb==-1?CaptureRequest.CONTROL_AWB_MODE_OFF:requestedWb);
        if(requestedWb==-1&&cctAvailable&&Build.VERSION.SDK_INT>=36){builder.set(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_CCT);builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE,requestedKelvin);builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT,0);}
        builder.set(CaptureRequest.CONTROL_EFFECT_MODE,CaptureRequest.CONTROL_EFFECT_MODE_OFF);
        builder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
        // We fit the whole preview ourselves; do not let compatibility mode also
        // rotate/crop it on displays that override the portrait orientation request.
        if(Build.VERSION.SDK_INT>=31&&contains(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES),CaptureRequest.SCALER_ROTATE_AND_CROP_NONE))builder.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
    }
    private void preview(){try{if(closing||session==null)return;CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(previewSurface);configure(b);session.setRepeatingRequest(b.build(),null,worker);}catch(Exception e){fail(e.getMessage(),"预览失败："+e.getMessage());}}
    private boolean readWhiteBalance(){try{
        if(wbModes.isEmpty())return false;int mode=wbModes.get(whiteBalance.getSelectedItemPosition());int k=requestedKelvin;
        if(mode==-1){k=Integer.parseInt(kelvin.getText().toString());if(!kelvinRange.contains(k))throw new IllegalArgumentException();}
        requestedWb=mode;requestedKelvin=k;return true;
    }catch(Exception e){Toast.makeText(this,tr("Enter Kelvin within ","请输入范围内的色温 K：")+kelvinRange,Toast.LENGTH_LONG).show();return false;}}
    private void applyWhiteBalance(){if(!busy&&readWhiteBalance())worker.post(this::preview);}
    private void capture(){if(busy||closing||!readWhiteBalance())return;rawEnabled=rawToggle.isChecked()&&rawReader!=null&&requestedZoom==1f;busy=true;wideButton.setEnabled(false);shutter.setEnabled(false);wbButton.setEnabled(false);status.setText(tr("Saving originals…","正在保存原片…"));rawToggle.setEnabled(false);whiteBalance.setEnabled(false);kelvin.setEnabled(false);worker.post(()->{try{
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
        JSONObject result=new JSONObject();result.put("id",captureId);result.put("setId",getIntent().getStringExtra("setId"));result.put("files",files);result.put("capturedAt",java.time.Instant.now().toString());result.put("device",Build.MANUFACTURER+" "+Build.MODEL);result.put("cameraId",cameraId);result.put("raw",rawEnabled);result.put("requestedZoomRatio",requestedZoom);if(Build.VERSION.SDK_INT>=30)result.put("actualZoomRatio",captureResult.get(CaptureResult.CONTROL_ZOOM_RATIO));result.put("requestedWbMode",requestedWb);result.put("actualAwbMode",captureResult.get(CaptureResult.CONTROL_AWB_MODE));result.put("iso",captureResult.get(CaptureResult.SENSOR_SENSITIVITY));result.put("exposureNs",captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME));
        if(requestedWb==-1){result.put("requestedKelvin",requestedKelvin);if(Build.VERSION.SDK_INT>=36){result.put("actualKelvin",captureResult.get(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE));result.put("actualColorMode",captureResult.get(CaptureResult.COLOR_CORRECTION_MODE));}}
        GalleryWriter.writeManifest(this,result);
        // Album failure is recoverable and must not discard the staged capture.
        GalleryWriter.publish(this,result);
        busy=false;runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("id",captureId));finish();});
    }catch(Exception e){fail("Capture could not be completed: "+e.getMessage(),"未能完成拍摄："+e.getMessage());}}
    private JSONObject fileInfo(File file,String mime)throws Exception {if(file.length()>52L*1024*1024)throw new IOException("Image exceeds notebook size limit");JSONObject f=new JSONObject();f.put("path",file.getAbsolutePath());f.put("name","Scout-"+captureId+(mime.equals("image/jpeg")?".jpg":".dng"));f.put("mime",mime);return f;}
    private void fail(String en,String zh){if(closing)return;runOnUiThread(()->{setResult(RESULT_CANCELED,new Intent().putExtra("error",tr(en,zh)));finish();});}
    @Override protected void onPause(){super.onPause();closing=true;if(worker!=null)worker.post(()->{worker.removeCallbacks(timeout);if(session!=null)session.close();if(camera!=null)camera.close();if(rawImage!=null){rawImage.close();rawImage=null;}if(jpegReader!=null)jpegReader.close();if(rawReader!=null)rawReader.close();if(previewSurface!=null)previewSurface.release();thread.quitSafely();});if(!isFinishing())finish();}
}
