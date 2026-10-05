package com.hkscout.field;

/** Transient viewfinder feedback. It never creates a saved measurement. */
final class CameraMeterFeedback {
    enum Phase { WAITING, UNAVAILABLE, STALE, PREVIEW, SEARCHING, SETTLING, LIMITED, CLIPPED, READY }

    static final class Frame {
        final double ev, spread;
        final long at, duration;
        final int ae, compensation, count;
        final boolean settled, limited;

        Frame(double ev, long at, int ae, int compensation, boolean settled, boolean limited,
              int count, long duration, double spread) {
            this.ev=ev;this.at=at;this.ae=ae;this.compensation=compensation;
            this.settled=settled;this.limited=limited;this.count=count;
            this.duration=duration;this.spread=spread;
        }

        double displayEV(long now) {
            return now>=at&&now-at<=750&&Double.isFinite(ev)&&compensation==0?ev:Double.NaN;
        }

        Phase phase(long now, double clipped) {
            if(now<at||now-at>750)return Phase.STALE;
            if(!Double.isFinite(ev)||compensation!=0)return Phase.UNAVAILABLE;
            if(limited)return Phase.LIMITED;
            if(!Double.isFinite(clipped)||clipped<0)return Phase.PREVIEW;
            if(clipped>0.2)return Phase.CLIPPED;
            if(ae!=2)return Phase.SEARCHING;
            return settled?Phase.READY:Phase.SETTLING;
        }

        int progress() {
            if(ae!=2||compensation!=0||limited||!Double.isFinite(ev)||
               !Double.isFinite(spread)||spread>0.25||count<1||duration<0)return 0;
            double fraction=Math.min(count/6.0,duration/600.0);
            return settled?100:(int)Math.max(0,Math.min(99,100*fraction));
        }
    }

    private CameraMeterFeedback() {}
}
