package com.hkscout.field;

/** Geometry for a rear Camera2 TextureView; independent of Android for JVM tests. */
final class CameraPreview {
    private CameraPreview() {}

    /** Android has already applied sensor rotation and stretched the buffer to the view.
     * Undo that stretch, fit the entire frame, and compensate only display rotation.
     * https://developer.android.com/media/camera/camera2/camera-preview#textureview
     * Values use android.graphics.Matrix's row-major order. */
    static float[] transform(int viewWidth,int viewHeight,int bufferWidth,int bufferHeight,
                             int sensorDegrees,int displayDegrees) {
        if(viewWidth<=0||viewHeight<=0||bufferWidth<=0||bufferHeight<=0)
            throw new IllegalArgumentException("Preview dimensions must be positive");
        if(sensorDegrees%90!=0||displayDegrees%90!=0)
            throw new IllegalArgumentException("Camera rotations must be quarter turns");
        boolean sensorSwapped=Math.floorMod(sensorDegrees,180)!=0;
        float uprightWidth=sensorSwapped?bufferHeight:bufferWidth;
        float uprightHeight=sensorSwapped?bufferWidth:bufferHeight;
        int display=Math.floorMod(displayDegrees,360);
        boolean displaySwapped=display%180!=0;
        float fit=Math.min(viewWidth/(displaySwapped?uprightHeight:uprightWidth),
                           viewHeight/(displaySwapped?uprightWidth:uprightHeight));
        float sx=uprightWidth*fit/viewWidth,sy=uprightHeight*fit/viewHeight;
        int cos=display==0?1:display==180?-1:0;
        int sin=display==90?-1:display==270?1:0;
        float a=cos*sx,b=-sin*sy,d=sin*sx,e=cos*sy;
        float cx=viewWidth/2f,cy=viewHeight/2f;
        return new float[]{a,b,cx-a*cx-b*cy,d,e,cy-d*cx-e*cy,0,0,1};
    }

    /** Match the still frame's aspect ratio, then prefer the sharpest bounded preview.
     * Camera HAL ordering must not accidentally select a tiny equally-shaped stream. */
    static int chooseSize(int[][] sizes,int stillWidth,int stillHeight) {
        if(sizes.length==0)throw new IllegalArgumentException("No preview sizes available");
        boolean bounded=false;
        for(int[] s:sizes)if(withinPreviewLimit(s))bounded=true;
        int best=-1;double bestError=Double.POSITIVE_INFINITY;long bestArea=0;
        double aspect=(double)stillWidth/stillHeight;
        for(int i=0;i<sizes.length;i++){
            int[] s=sizes[i];if(bounded&&!withinPreviewLimit(s))continue;
            double error=Math.abs((double)s[0]/s[1]-aspect);
            long area=(long)s[0]*s[1];
            if(best<0||error<bestError-1e-6||Math.abs(error-bestError)<=1e-6&&
                    (bounded?area>bestArea:area<bestArea)){
                best=i;bestError=error;bestArea=area;
            }
        }
        return best;
    }
    private static boolean withinPreviewLimit(int[] size){
        return Math.max(size[0],size[1])<=1920&&Math.min(size[0],size[1])<=1080;
    }
}
