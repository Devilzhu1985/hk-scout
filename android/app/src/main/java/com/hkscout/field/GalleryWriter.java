package com.hkscout.field;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Public, original-byte copies. The staged manifest is a recovery journal;
 * acknowledgement is allowed only after both gallery and notebook save. */
final class GalleryWriter {
    static void writeManifest(Context context,JSONObject capture)throws Exception {
        String id=capture.getString("id");if(!id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid capture ID");
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"reference-captures/"+id+".json"));
        FileOutputStream output=null;
        try{output=file.startWrite();output.write(capture.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(output);}
        catch(Exception e){if(output!=null)file.failWrite(output);throw e;}
    }
    static synchronized JSONObject publish(Context context,JSONObject capture)throws Exception {
        JSONObject state=new JSONObject().put("status","pending").put("folder","DCIM/Camera");capture.put("gallery",state);
        try {
            if(Build.VERSION.SDK_INT<29&&context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)throw new IOException("Allow photo storage permission, then retry gallery save.");
            JSONArray files=capture.getJSONArray("files");
            for(int i=0;i<files.length();i++){
                JSONObject entry=files.getJSONObject(i);if(entry.optBoolean("gallerySaved"))continue;
                File source=new File(entry.getString("path"));
                if(!source.getCanonicalFile().getParentFile().equals(new File(context.getFilesDir(),"reference-captures").getCanonicalFile())||!source.isFile())throw new IOException("Staged original is unavailable");
                String name=entry.getString("name");if(!name.matches("Scout-[a-f0-9-]{36}\\.(jpg|dng)"))throw new IOException("Invalid original filename");
                if(Build.VERSION.SDK_INT>=29)publishModern(context,capture,entry,source);
                else publishLegacy(context,capture,entry,source);
                entry.put("gallerySaved",true);writeManifest(context,capture);
            }
            state.put("status","saved");
        }catch(Exception error){state.put("error",error.getMessage()==null?"Gallery storage is unavailable":error.getMessage());}
        writeManifest(context,capture);return capture;
    }
    private static void publishModern(Context context,JSONObject capture,JSONObject entry,File source)throws Exception {
        ContentResolver resolver=context.getContentResolver();Uri uri=null;boolean published=false;
        if(entry.has("galleryUri")){
            Uri saved=Uri.parse(entry.getString("galleryUri"));
            if(!"content".equals(saved.getScheme())||!"media".equals(saved.getAuthority()))throw new IOException("Invalid gallery destination");
            try(Cursor row=resolver.query(saved,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.IS_PENDING},null,null,null)){if(row!=null&&row.moveToFirst()){uri=saved;published=row.getInt(1)==0;}}
        }
        if(uri==null){
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,entry.getString("name"));values.put(MediaStore.Images.Media.MIME_TYPE,entry.getString("mime"));
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_DCIM+"/Camera");values.put(MediaStore.Images.Media.IS_PENDING,1);values.put(MediaStore.Images.Media.DATE_TAKEN,java.time.Instant.parse(capture.getString("capturedAt")).toEpochMilli());
            uri=resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);if(uri==null)throw new IOException("Could not create gallery entry");
            entry.put("galleryUri",uri.toString());
            try{writeManifest(context,capture);}catch(Exception e){resolver.delete(uri,null,null);entry.remove("galleryUri");throw e;}
        }
        byte[] hash;
        if(published){
            // A crash may happen after publication but before journalling success.
            // Verify this copy; do not overwrite a user's subsequently edited file.
            try(InputStream input=new FileInputStream(source)){hash=OriginalCopy.copy(input,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},source.length());}
        }else{
            ParcelFileDescriptor descriptor=resolver.openFileDescriptor(uri,"rwt");if(descriptor==null)throw new IOException("Could not open gallery file");
            try(ParcelFileDescriptor.AutoCloseOutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);InputStream input=new FileInputStream(source)){hash=OriginalCopy.copy(input,output,source.length());output.getFD().sync();}
        }
        try(InputStream input=resolver.openInputStream(uri)){if(input==null)throw new IOException("Could not verify gallery file");OriginalCopy.verify(input,hash);}
        ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);if(resolver.update(uri,ready,null,null)!=1)throw new IOException("Could not publish gallery file");
    }
    @SuppressWarnings("deprecation") private static void publishLegacy(Context context,JSONObject capture,JSONObject entry,File source)throws Exception {
        File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),"Camera");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create Camera folder");
        File target=new File(folder,entry.getString("name"));
        // Never replace an existing public original. An interrupted private .part
        // can be retried, but an existing destination must match the source bytes.
        byte[] hash;
        if(target.exists()){
            try(InputStream input=new FileInputStream(source)){hash=OriginalCopy.copy(input,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},source.length());}
        }else{
            File partial=new File(folder,"."+entry.getString("name")+".part");
            try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(partial)){hash=OriginalCopy.copy(input,output,source.length());output.getFD().sync();}
            if(!partial.renameTo(target))throw new IOException("Could not publish Camera file");
        }
        try(InputStream input=new FileInputStream(target)){OriginalCopy.verify(input,hash);}
        ContentResolver resolver=context.getContentResolver();
        try(Cursor rows=resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID},MediaStore.Images.Media.DATA+"=?",new String[]{target.getAbsolutePath()},null)){
            if(rows!=null&&rows.moveToFirst()){entry.put("galleryUri",ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,rows.getLong(0)).toString());return;}
        }
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DATA,target.getAbsolutePath());values.put(MediaStore.Images.Media.DISPLAY_NAME,entry.getString("name"));values.put(MediaStore.Images.Media.MIME_TYPE,entry.getString("mime"));values.put(MediaStore.Images.Media.DATE_TAKEN,java.time.Instant.parse(capture.getString("capturedAt")).toEpochMilli());
        Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Could not index Camera file");entry.put("galleryUri",uri.toString());
    }
}
