package com.hkscout.field;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.hkscout.field.CameraMeterFeedback.Phase.*;

public class CameraMeterFeedbackTest {
    private CameraMeterFeedback.Frame frame(int ae,boolean ready,boolean limited,int count,long duration,double spread){
        return new CameraMeterFeedback.Frame(9,1000,ae,0,ready,limited,count,duration,spread);
    }
    @Test public void liveSearchingValueDoesNotEnableSave(){
        CameraMeterFeedback.Frame f=frame(1,false,false,0,0,Double.NaN);
        assertEquals(9,f.displayEV(1100),0);assertEquals(SEARCHING,f.phase(1100,0));assertEquals(0,f.progress());
    }
    @Test public void collectingSamplesIsNotACompletedMeasurement(){
        CameraMeterFeedback.Frame f=frame(2,false,false,4,300,.1);
        assertEquals(SETTLING,f.phase(1100,0));assertEquals(50,f.progress());
        assertEquals(99,frame(2,false,false,10,900,.1).progress());
        assertEquals(0,frame(2,false,false,10,900,1.5).progress());
    }
    @Test public void settledReadingNeedsFreshPreviewWithoutClipping(){
        CameraMeterFeedback.Frame f=frame(2,true,false,8,700,.1);
        assertEquals(READY,f.phase(1100,.2));assertEquals(100,f.progress());
        assertEquals(CLIPPED,f.phase(1100,.21));assertEquals(PREVIEW,f.phase(1100,Double.NaN));
    }
    @Test public void staleOrFutureSamplesHideTheValueAndDisableSave(){
        CameraMeterFeedback.Frame f=frame(2,true,false,8,700,0);
        assertEquals(STALE,f.phase(1751,0));assertTrue(Double.isNaN(f.displayEV(1751)));
        assertEquals(STALE,f.phase(999,0));assertTrue(Double.isNaN(f.displayEV(999)));
    }
    @Test public void limitsAndMissingOrCompensatedMetadataCannotLookReady(){
        assertEquals(LIMITED,frame(2,false,true,0,0,Double.NaN).phase(1100,0));
        CameraMeterFeedback.Frame absent=new CameraMeterFeedback.Frame(Double.NaN,1000,2,0,false,false,0,0,Double.NaN);
        assertEquals(UNAVAILABLE,absent.phase(1100,0));assertEquals(0,absent.progress());
        CameraMeterFeedback.Frame compensated=new CameraMeterFeedback.Frame(9,1000,2,1,true,false,8,700,0);
        assertEquals(UNAVAILABLE,compensated.phase(1100,0));assertTrue(Double.isNaN(compensated.displayEV(1100)));
    }
    @Test public void brightSearchingPreviewHasAnActionableWarning(){
        assertEquals(CLIPPED,frame(1,false,false,0,0,Double.NaN).phase(1100,.8));
    }
}
