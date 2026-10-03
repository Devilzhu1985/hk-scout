package com.hkscout.field;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

@CapacitorPlugin(name="ReferenceCamera", permissions={@Permission(alias="camera", strings={Manifest.permission.CAMERA}),@Permission(alias="gallery",strings={Manifest.permission.WRITE_EXTERNAL_STORAGE})})
public class ReferenceCameraPlugin extends Plugin {
    @PluginMethod public void capture(PluginCall call) {
        if(getPermissionState("camera")!=PermissionState.GRANTED){requestPermissionForAlias("camera",call,"cameraPermission");return;}
        if(android.os.Build.VERSION.SDK_INT<29&&getPermissionState("gallery")!=PermissionState.GRANTED){requestPermissionForAlias("gallery",call,"galleryPermission");return;}
        launchCamera(call);
    }
    private void launchCamera(PluginCall call){
        Intent intent=new Intent(getActivity(),ReferenceCameraActivity.class);
        intent.putExtra("language",call.getString("language","en"));
        intent.putExtra("setId",call.getString("setId",""));
        startActivityForResult(call,intent,"captured");
    }
    @PermissionCallback private void galleryPermission(PluginCall call){launchCamera(call);}
    @PermissionCallback private void cameraPermission(PluginCall call){
        if(getPermissionState("camera")==PermissionState.GRANTED)capture(call);
        else call.reject("Camera permission denied. Allow camera access in system settings or choose existing images.");
    }
    @ActivityCallback private void captured(PluginCall call,ActivityResult result){
        if(call==null)return;
        if(result.getResultCode()!=Activity.RESULT_OK){
            String error=result.getData()==null?null:result.getData().getStringExtra("error");
            if(error!=null)call.reject(error);else{JSObject out=new JSObject();out.put("cancelled",true);call.resolve(out);}return;
        }
        try{call.resolve(read(result.getData().getStringExtra("id")));}catch(Exception e){call.reject("Capture retained for recovery: "+e.getMessage());}
    }
    private File directory(){return new File(getContext().getFilesDir(),"reference-captures");}
    private JSObject read(String id)throws Exception{
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw new Exception("Invalid capture ID");
        JSONObject json=new JSONObject(new String(new android.util.AtomicFile(new File(directory(),id+".json")).readFully(),java.nio.charset.StandardCharsets.UTF_8));
        return JSObject.fromJSONObject(json);
    }
    @PluginMethod public void pending(PluginCall call){
        try{JSArray items=new JSArray();File[] files=directory().listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))items.put(read(f.getName().replace(".json","")));JSObject out=new JSObject();out.put("captures",items);call.resolve(out);}catch(Exception e){call.reject("Could not read pending captures: "+e.getMessage());}
    }
    @PluginMethod public void publish(PluginCall call){
        if(android.os.Build.VERSION.SDK_INT<29&&getPermissionState("gallery")!=PermissionState.GRANTED){requestPermissionForAlias("gallery",call,"publishPermission");return;}
        publishOriginals(call);
    }
    @PermissionCallback private void publishPermission(PluginCall call){publishOriginals(call);}
    private void publishOriginals(PluginCall call){new Thread(()->{try{call.resolve(JSObject.fromJSONObject(GalleryWriter.publish(getContext(),read(call.getString("id","")))));}catch(Exception e){call.reject("Gallery copy retained for retry: "+e.getMessage());}},"ScoutGallery").start();}
    // Only remove staged files after the web notebook has committed their bytes.
    @PluginMethod public void acknowledge(PluginCall call){
        try{String id=call.getString("id","");JSONObject capture=read(id);if(capture.optJSONObject("gallery")==null||!"saved".equals(capture.getJSONObject("gallery").optString("status")))throw new Exception("Gallery copy is pending; staged originals retained");for(String ext:new String[]{".jpg",".dng"})Files.deleteIfExists(new File(directory(),id+ext).toPath());new android.util.AtomicFile(new File(directory(),id+".json")).delete();call.resolve();}catch(Exception e){call.reject(e.getMessage());}
    }
    @PluginMethod public void shareStaged(PluginCall call){
        new Thread(()->{try{
            String id=call.getString("id","");JSObject capture=read(id);java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();
            org.json.JSONArray entries=capture.getJSONArray("files");
            for(int i=0;i<entries.length();i++){
                org.json.JSONObject entry=entries.getJSONObject(i);File source=new File(entry.getString("path"));
                if(!source.getCanonicalFile().getParentFile().equals(directory().getCanonicalFile()))throw new Exception("Invalid staged image path");
                File copy=new File(getContext().getCacheDir(),"Scout-"+source.getName());Files.copy(source.toPath(),copy.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                uris.add(FileProvider.getUriForFile(getContext(),getContext().getPackageName()+".fileprovider",copy));
            }
            getActivity().runOnUiThread(()->{try{Intent intent=new Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/octet-stream").putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(!uris.isEmpty()){android.content.ClipData clip=android.content.ClipData.newRawUri("Scout originals",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new android.content.ClipData.Item(uris.get(i)));intent.setClipData(clip);}getActivity().startActivity(Intent.createChooser(intent,"Save Scout originals"));call.resolve();}catch(Exception e){call.reject(e.getMessage());}});
        }catch(Exception e){call.reject(e.getMessage());}},"ScoutCaptureRecovery").start();
    }
}
