package com.hkscout.field;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class OriginalCopyTest {
    @Test public void preservesEveryOriginalByteAcrossBuffers()throws Exception {
        byte[] original=new byte[150003];new java.util.Random(5).nextBytes(original);
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] hash=OriginalCopy.copy(new ByteArrayInputStream(original),output,original.length);
        assertArrayEquals(original,output.toByteArray());OriginalCopy.verify(new ByteArrayInputStream(output.toByteArray()),hash);
    }
    @Test public void rejectsTruncatedOriginal()throws Exception {
        try{OriginalCopy.copy(new ByteArrayInputStream(new byte[2]),new ByteArrayOutputStream(),3);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Incomplete"));}
    }
    @Test public void rejectsChangedOriginalLength()throws Exception {
        try{OriginalCopy.copy(new ByteArrayInputStream(new byte[3]),new ByteArrayOutputStream(),2);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("changed"));}
    }
    @Test public void rejectsCorruptGalleryReadback()throws Exception {
        byte[] hash=OriginalCopy.copy(new ByteArrayInputStream(new byte[]{1,2,3}),new ByteArrayOutputStream(),3);
        try{OriginalCopy.verify(new ByteArrayInputStream(new byte[]{1,9,3}),hash);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("verification"));}
    }
    @Test public void propagatesStorageFailure()throws Exception {
        try{OriginalCopy.copy(new ByteArrayInputStream(new byte[20]),new OutputStream(){public void write(int b)throws IOException{throw new IOException("Full");}},20);fail();}catch(IOException expected){assertEquals("Full",expected.getMessage());}
    }
}
