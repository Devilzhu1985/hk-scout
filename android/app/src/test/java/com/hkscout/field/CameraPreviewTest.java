package com.hkscout.field;

import org.junit.Test;
import static org.junit.Assert.*;

public class CameraPreviewTest {
    private static final float EPS=0.001f;
    private static float[] map(float[] m,float x,float y){return new float[]{m[0]*x+m[1]*y+m[2],m[3]*x+m[4]*y+m[5]};}

    @Test public void portraitSensorIsNotRotatedTwice(){
        // Android has already made a 1440x1080 sensor stream into upright 3:4.
        // An exact 3:4 view needs no transform, for either rear sensor mounting.
        for(int sensor:new int[]{90,270}){
            float[] m=CameraPreview.transform(900,1200,1440,1080,sensor,0);
            assertArrayEquals(new float[]{1,0,0,0,1,0,0,0,1},m,EPS);
        }
    }
    @Test public void squareViewShowsWholePortraitFrameWithSideBars(){
        float[] m=CameraPreview.transform(1000,1000,1440,1080,90,0);
        assertArrayEquals(new float[]{125,0},map(m,0,0),EPS);
        assertArrayEquals(new float[]{875,1000},map(m,1000,1000),EPS);
        assertArrayEquals(new float[]{500,0},map(m,500,0),EPS); // Up stays up.
    }
    @Test public void wideViewFitsPortraitFrameWithoutCropping(){
        float[] m=CameraPreview.transform(1200,600,1440,1080,90,0);
        assertArrayEquals(new float[]{375,0},map(m,0,0),EPS);
        assertArrayEquals(new float[]{825,600},map(m,1200,600),EPS);
    }
    @Test public void displayCompensationHasCorrectDirection(){
        // The top of the natural-orientation buffer points left after -90,
        // down after -180, and right after -270 display compensation.
        int[] rotations={0,90,180,270};
        float[][] expected={{500,0},{0,500},{500,1000},{1000,500}};
        for(int i=0;i<rotations.length;i++){
            float[] m=CameraPreview.transform(1000,1000,1440,1080,90,rotations[i]);
            assertArrayEquals(expected[i],map(m,500,0),EPS);
        }
    }
    @Test public void allSensorAndDisplayRotationsPreserveShapeAndFrame(){
        for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270})
        for(int[] view:new int[][]{{1080,1450},{1080,700},{1800,800},{700,1500},{1000,1000}}){
            int w=view[0],h=view[1];float[] m=CameraPreview.transform(w,h,1440,1080,sensor,display);
            float[][] p={map(m,0,0),map(m,w,0),map(m,w,h),map(m,0,h)};
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
            for(float[] point:p){minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);minY=Math.min(minY,point[1]);maxY=Math.max(maxY,point[1]);}
            assertTrue(minX>=-EPS&&minY>=-EPS&&maxX<=w+EPS&&maxY<=h+EPS);
            assertTrue(Math.abs(maxX-minX-w)<EPS||Math.abs(maxY-minY-h)<EPS);
            assertArrayEquals(new float[]{w/2f,h/2f},map(m,w/2f,h/2f),EPS);
            double inputWidth=sensor%180==0?1440:1080,inputHeight=sensor%180==0?1080:1440;
            double pixelWidth=Math.hypot(p[1][0]-p[0][0],p[1][1]-p[0][1])/inputWidth;
            double pixelHeight=Math.hypot(p[3][0]-p[0][0],p[3][1]-p[0][1])/inputHeight;
            assertEquals(pixelWidth,pixelHeight,EPS); // A sensor square remains square.
        }
    }
    @Test public void choosesSharpMatchingPreviewRegardlessOfHalOrder(){
        int[][] sizes={{320,240},{1920,1080},{640,480},{1440,1080},{4000,3000}};
        assertEquals(3,CameraPreview.chooseSize(sizes,4000,3000));
        int[][] reverse={{4000,3000},{1440,1080},{640,480},{1920,1080},{320,240}};
        assertEquals(1,CameraPreview.chooseSize(reverse,4000,3000));
        assertEquals(1,CameraPreview.chooseSize(sizes,3840,2160));
    }
    @Test public void unusualSizesHaveDeterministicFallback(){
        assertEquals(1,CameraPreview.chooseSize(new int[][]{{4000,3000},{2000,1500},{3840,2160}},4000,3000));
        assertEquals(0,CameraPreview.chooseSize(new int[][]{{1080,1440},{240,320}},3000,4000));
    }
    @Test(expected=IllegalArgumentException.class) public void emptyPreviewSizesAreRejected(){CameraPreview.chooseSize(new int[][]{},4000,3000);}
}
