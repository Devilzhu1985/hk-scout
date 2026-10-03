package com.hkscout.field;

import java.io.*;
import java.security.MessageDigest;

/** Byte-preserving transfer used by the public camera-album writer. */
final class OriginalCopy {
    static byte[] copy(InputStream input,OutputStream output,long expected)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[65536];long count=0;int read;
        while((read=input.read(buffer))!=-1){if(read==0)continue;count+=read;if(count>expected)throw new IOException("Original size changed during copy");output.write(buffer,0,read);digest.update(buffer,0,read);}
        if(count!=expected)throw new IOException("Incomplete original copy");output.flush();return digest.digest();
    }
    static void verify(InputStream input,byte[] expected)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int read;
        while((read=input.read(buffer))!=-1)if(read>0)digest.update(buffer,0,read);
        if(!MessageDigest.isEqual(expected,digest.digest()))throw new IOException("Gallery copy verification failed");
    }
}
