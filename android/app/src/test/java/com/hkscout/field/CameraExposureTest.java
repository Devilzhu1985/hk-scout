package com.hkscout.field;
import org.junit.Test;
import static org.junit.Assert.*;
public class CameraExposureTest {
    private CameraExposure.Window settled(){CameraExposure.Window w=new CameraExposure.Window();for(int i=0;i<=7;i++)w.add(i*100,i+1,9,2,0,false);return w;}
    @Test public void normalizesExposureAndDigitalGain(){assertEquals(9,CameraExposure.ev100(2,7812500,100,100),1e-9);assertEquals(8,CameraExposure.ev100(2,7812500,100,200),1e-9);assertEquals(-1,CameraExposure.ev100(1,2000000000L,100,100),1e-9);}
    @Test public void missingMetadataIsNotZeroEv(){assertTrue(Double.isNaN(CameraExposure.ev100(0,1,100,100)));assertTrue(Double.isNaN(CameraExposure.ev100(2,0,100,100)));assertTrue(Double.isNaN(CameraExposure.ev100(2,1,0,100)));}
    @Test public void requiresSettledWindow(){CameraExposure.Window w=new CameraExposure.Window();for(int i=0;i<6;i++)assertFalse(w.add(i*100,i+1,9,2,0,false));assertTrue(w.add(600,7,9,2,0,false));assertEquals(600,w.duration());}
    @Test public void searchingOrClampedExposureInvalidatesReading(){CameraExposure.Window w=settled();assertTrue(w.ready(700));assertFalse(w.add(800,9,9,1,0,false));assertFalse(w.ready(800));w=settled();assertFalse(w.add(800,9,9,2,0,true));}
    @Test public void staleFramesAndDuplicateCallbacksCannotSave(){CameraExposure.Window w=settled();assertFalse(w.ready(1500));assertFalse(w.add(800,8,9,2,0,false));assertEquals(0,w.count());}
    @Test public void movingBetweenLightLevelsWaitsForNewWindow(){CameraExposure.Window w=settled();assertFalse(w.add(800,9,12,2,0,false));for(int i=9;i<=20;i++)w.add(i*100,i+1,12,2,0,false);assertTrue(w.ready(2000));assertEquals(12,w.min(),0);}
    @Test public void compensationOrInvalidNumbersReset(){CameraExposure.Window w=settled();assertFalse(w.add(800,9,9,2,1,false));w=settled();assertFalse(w.add(800,9,Double.NaN,2,0,false));}
}
