package com.hkscout.field;

import java.util.ArrayDeque;

/** Reflected AE estimate and freshness/stability gate, independent of Android. */
final class CameraExposure {
    static double ev100(double aperture,long exposureNs,int iso,int postRawBoost) {
        if(!Double.isFinite(aperture)||aperture<=0||exposureNs<=0||iso<=0||postRawBoost<=0)return Double.NaN;
        return Math.log(aperture*aperture/(exposureNs/1e9))/Math.log(2)-Math.log(iso*(double)postRawBoost/10000)/Math.log(2);
    }
    static final class Sample {
        final long at;final double ev;
        Sample(long at,double ev){this.at=at;this.ev=ev;}
    }
    static final class Window {
        private final ArrayDeque<Sample> samples=new ArrayDeque<>();
        private long lastFrame=-1,lastSample=-1;
        void reset(){samples.clear();lastSample=-1;}
        boolean add(long at,long frame,double ev,int aeState,int compensation,boolean limited){
            if(frame<=lastFrame||!Double.isFinite(ev)||aeState!=2||compensation!=0||limited){reset();return false;}
            lastFrame=frame;
            if(lastSample<0||at-lastSample>=80){samples.addLast(new Sample(at,ev));lastSample=at;}
            while(!samples.isEmpty()&&at-samples.peekFirst().at>1200)samples.removeFirst();
            return ready(at)&&ev>=min()-0.00001&&ev<=max()+0.00001;
        }
        boolean ready(long at){return samples.size()>=6&&at-samples.peekLast().at<=750&&duration()>=600&&max()-min()<=0.25;}
        int count(){return samples.size();}
        long duration(){return samples.isEmpty()?0:samples.peekLast().at-samples.peekFirst().at;}
        double min(){return samples.stream().mapToDouble(s->s.ev).min().orElse(Double.NaN);}
        double max(){return samples.stream().mapToDouble(s->s.ev).max().orElse(Double.NaN);}
    }
}
