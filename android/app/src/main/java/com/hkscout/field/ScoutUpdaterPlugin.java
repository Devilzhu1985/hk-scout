package com.hkscout.field;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

@CapacitorPlugin(name="ScoutUpdater")
public class ScoutUpdaterPlugin extends Plugin {
    private File apk;
    private volatile boolean busy;
    @PluginMethod public void install(PluginCall call){
        if(busy){call.reject("An update is already in progress.");return;}
        String url=call.getString("url",""),digest=call.getString("sha256","");
        if(!url.matches("https://github\\.com/Devilzhu1985/hk-scout/releases/download/[A-Za-z0-9._-]+/scout-[A-Za-z0-9._-]+\\.apk")||!digest.matches("[a-f0-9]{64}")){call.reject("Invalid Scout update source or checksum.");return;}
        busy=true;
        new Thread(()->{try{
            File target=new File(getContext().getCacheDir(),"scout-update.apk"),partial=new File(getContext().getCacheDir(),"scout-update.download");
            URL next=new URL(url);HttpURLConnection connection=null;
            for(int redirect=0;redirect<6;redirect++){
                String host=next.getHost();if(!next.getProtocol().equals("https")||!(host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Unexpected download destination");
                connection=(HttpURLConnection)next.openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
                int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();next=new URL(next,location);continue;}if(code!=200)throw new IOException("Download failed: HTTP "+code);break;
            }
            MessageDigest sha=MessageDigest.getInstance("SHA-256");long total=0;
            try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("Update exceeds size limit");sha.update(buf,0,n);out.write(buf,0,n);}out.getFD().sync();}finally{connection.disconnect();}
            StringBuilder actual=new StringBuilder();for(byte b:sha.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b));
            if(!actual.toString().equals(digest)){partial.delete();throw new IOException("Update checksum mismatch. Nothing installed.");}
            verifyPackage(partial);
            if(target.exists()&&!target.delete())throw new IOException("Could not replace old update download");if(!partial.renameTo(target))throw new IOException("Could not save update download");apk=target;
            getActivity().runOnUiThread(()->{
                if(Build.VERSION.SDK_INT>=26&&!getContext().getPackageManager().canRequestPackageInstalls())startActivityForResult(call,new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getContext().getPackageName())),"installPermission");
                else launchInstaller(call);
            });
        }catch(Exception e){busy=false;call.reject(e.getMessage());}},"ScoutUpdateDownload").start();
    }
    @SuppressWarnings("deprecation")
    private void verifyPackage(File file)throws Exception{
        PackageManager manager=getContext().getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=manager.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=manager.getPackageInfo(getContext().getPackageName(),flags);
        if(candidate==null||!installed.packageName.equals(candidate.packageName))throw new IOException("Downloaded file is not Scout.");
        long newVersion=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode,currentVersion=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(newVersion<=currentVersion)throw new IOException("Downloaded Scout version is not newer.");
        Signature[] next=Build.VERSION.SDK_INT>=28?candidate.signingInfo.getApkContentsSigners():candidate.signatures,previous=Build.VERSION.SDK_INT>=28?installed.signingInfo.getApkContentsSigners():installed.signatures;
        if(next==null||previous==null||next.length!=previous.length||next.length==0)throw new IOException("Scout signing certificate does not match.");
        for(Signature signer:next)if(!java.util.Arrays.asList(previous).contains(signer))throw new IOException("Scout signing certificate does not match.");
    }
    @ActivityCallback private void installPermission(PluginCall call,ActivityResult result){
        if(call==null){busy=false;return;}
        if(Build.VERSION.SDK_INT>=26&&!getContext().getPackageManager().canRequestPackageInstalls()){busy=false;call.reject("Installation permission was not granted. You can try again later.");return;}
        launchInstaller(call);
    }
    private void launchInstaller(PluginCall call){try{
        if(apk==null)apk=new File(getContext().getCacheDir(),"scout-update.apk");
        if(!apk.isFile())throw new IOException("Download the update again.");
        Uri uri=FileProvider.getUriForFile(getContext(),getContext().getPackageName()+".fileprovider",apk);
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getActivity().startActivity(intent);JSObject out=new JSObject();out.put("installerOpened",true);call.resolve(out);
    }catch(Exception e){call.reject(e.getMessage());}finally{busy=false;}}
}
