package com.hkscout.field;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Native aiming UI, shared by every Camera2 EV state. No simulated readings. */
final class CameraMeterView extends LinearLayout {
    final TextureView texture;
    private final boolean chinese;
    private final Target target;
    private final TextView mode, value, valueLabel, status, instruction, sampling, details, detailToggle;
    private final TextView[] steps=new TextView[3];
    private final ProgressBar progress;
    private final Button save;
    private CameraMeterFeedback.Phase previous;
    private boolean center, expanded;
    private int feedbackHeight;
    private static final int WHITE=0xfff4f7f5, MUTED=0xffaab7b1, GREEN=0xffa8efbd,
        AMBER=0xffffce83, WARNING=0xffffa38c, PANEL=0xff151c18;

    CameraMeterView(Context context, boolean chinese, Runnable cancel, Runnable saveReading) {
        super(context);this.chinese=chinese;
        setOrientation(VERTICAL);setBackgroundColor(Color.BLACK);
        setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});

        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView close=label("×",28,WHITE);close.setGravity(Gravity.CENTER);
        close.setContentDescription(tr("Cancel camera measurement","取消相机测光"));
        close.setBackground(round(PANEL,12));close.setOnClickListener(v->cancel.run());
        LayoutParams closeSize=new LayoutParams(dp(48),dp(48));closeSize.setMargins(dp(8),dp(4),dp(12),dp(4));header.addView(close,closeSize);
        TextView title=label(tr("Aim & meter","瞄准测光"),18,WHITE);title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LayoutParams(0,-2,1));
        TextView lens=label(tr("REAR","后置相机"),11,MUTED);lens.setPadding(0,0,dp(16),0);header.addView(lens);
        addView(header);

        FrameLayout finder=new FrameLayout(context);
        texture=new TextureView(context);texture.setOpaque(false);finder.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        target=new Target(context);finder.addView(target,new FrameLayout.LayoutParams(-1,-1));
        mode=label(tr("Opening camera…","正在打开相机…"),12,WHITE);mode.setGravity(Gravity.CENTER);
        mode.setPadding(dp(12),dp(7),dp(12),dp(7));mode.setBackground(round(0xd91b211e,12));
        FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);badge.setMargins(dp(10),dp(10),dp(10),0);finder.addView(mode,badge);
        addView(finder,new LayoutParams(-1,0,1));

        LinearLayout bottom=new LinearLayout(context);bottom.setOrientation(VERTICAL);bottom.setBackground(round(PANEL,20));
        ScrollView feedbackScroll=new ScrollView(context){
            @Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(height),feedbackHeight),MeasureSpec.AT_MOST));}
        };
        LinearLayout sheet=new LinearLayout(context);sheet.setOrientation(VERTICAL);sheet.setPadding(dp(16),dp(10),dp(16),0);
        LinearLayout values=new LinearLayout(context);values.setGravity(Gravity.CENTER_VERTICAL);
        value=label("—",38,WHITE);value.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);value.setIncludeFontPadding(false);
        values.addView(value,new LayoutParams(0,-2,1));
        valueLabel=label(tr("EV100 · estimate\nLive · not saved","EV100 · 估算\n实时 · 尚未保存"),11,MUTED);valueLabel.setGravity(Gravity.END);values.addView(valueLabel);sheet.addView(values);

        LinearLayout flow=new LinearLayout(context);flow.setPadding(0,dp(5),0,dp(7));
        String[] labels={tr("1  Aim","1  瞄准"),tr("2  Hold steady","2  稳定"),tr("3  Save","3  保存")};
        for(int i=0;i<3;i++){steps[i]=label(labels[i],12,MUTED);steps[i].setGravity(Gravity.CENTER);steps[i].setPadding(dp(3),dp(4),dp(3),dp(4));flow.addView(steps[i],new LayoutParams(0,-2,1));}
        sheet.addView(flow);

        status=label("",15,AMBER);status.setTypeface(null,Typeface.BOLD);status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);sheet.addView(status);
        instruction=label("",12,WHITE);instruction.setPadding(0,dp(3),0,dp(5));sheet.addView(instruction);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setIndeterminate(false);progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff354139));progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        sheet.addView(progress,new LayoutParams(-1,dp(4)));
        sampling=label("",10,MUTED);sampling.setPadding(0,dp(3),0,0);sheet.addView(sampling);

        LinearLayout detailRow=new LinearLayout(context);detailRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView estimate=label(tr("Reflected estimate · not lux","反射光估算 · 非 lux"),10,MUTED);detailRow.addView(estimate,new LayoutParams(0,-2,1));
        detailToggle=label(tr("Exposure details  ▾","曝光详情  ▾"),12,WHITE);detailToggle.setGravity(Gravity.CENTER_VERTICAL);detailToggle.setMinHeight(dp(44));detailToggle.setPadding(dp(10),0,0,0);
        detailRow.addView(detailToggle);sheet.addView(detailRow);
        details=label(tr("Waiting for exposure metadata…","等待曝光数据…"),11,MUTED);details.setPadding(0,0,0,dp(8));details.setVisibility(GONE);sheet.addView(details);
        detailToggle.setOnClickListener(v->{expanded=!expanded;details.setVisibility(expanded?VISIBLE:GONE);detailToggle.setText(expanded?tr("Hide details  ▴","收起详情  ▴"):tr("Exposure details  ▾","曝光详情  ▾"));});
        feedbackScroll.addView(sheet);bottom.addView(feedbackScroll,new LayoutParams(-1,-2));
        save=new Button(context);save.setAllCaps(false);save.setTextSize(15);save.setTypeface(null,Typeface.BOLD);save.setBackgroundTintList(null);save.setOnClickListener(v->saveReading.run());
        LayoutParams saveSize=new LayoutParams(-1,dp(52));saveSize.setMargins(dp(16),dp(6),dp(16),dp(10));bottom.addView(save,saveSize);
        addView(bottom);
        render(null,CameraMeterFeedback.Phase.WAITING,System.nanoTime()/1000000L,"");
    }

    private String tr(String en,String zh){return chinese?zh:en;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int sp,int color){TextView t=new TextView(getContext());t.setText(text);t.setTextSize(sp);t.setTextColor(color);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}

    @Override protected void onMeasure(int width,int height){
        feedbackHeight=Math.max(0,(MeasureSpec.getSize(height)-getPaddingTop()-getPaddingBottom())/2);
        super.onMeasure(width,height);
    }

    void setMetering(boolean center){
        this.center=center;target.center=center;target.known=true;
        mode.setText(center?tr("CENTER REGION · CAMERA AE","中央区域 · 相机自动测光"):tr("WHOLE FRAME · CAMERA AE","全画面 · 相机自动测光"));
        target.invalidate();
    }
    void setFrame(float width,float height){target.frameWidth=width;target.frameHeight=height;target.invalidate();}

    void render(CameraMeterFeedback.Frame frame,CameraMeterFeedback.Phase phase,long now,String exposure) {
        boolean ready=phase==CameraMeterFeedback.Phase.READY;
        boolean warning=phase==CameraMeterFeedback.Phase.CLIPPED||phase==CameraMeterFeedback.Phase.LIMITED||phase==CameraMeterFeedback.Phase.UNAVAILABLE;
        int accent=ready?GREEN:warning?WARNING:AMBER;
        double ev=frame==null?Double.NaN:frame.displayEV(now);
        value.setText(Double.isFinite(ev)?String.format(Locale.US,"%.2f",ev):"—");
        value.setTextColor(ready?GREEN:WHITE);
        valueLabel.setText(tr("EV100 · estimate\n","EV100 · 估算\n")+(ready?tr("Ready · not saved","可保存 · 尚未保存"):Double.isFinite(ev)?tr("Live · not saved","实时 · 尚未保存"):tr("Waiting for reading","等待读数")));
        if(!exposure.isEmpty())details.setText(exposure+"\n"+(center?tr("Central region requested; not a calibrated spot meter.","已请求中央区域，非校准点测光表。"):tr("Whole-frame AE. The crosshair is an aiming aid only.","全画面自动测光，十字仅辅助瞄准。")));
        else details.setText(tr("Waiting for fresh exposure metadata…","等待最新曝光数据…"));
        int percent=(phase==CameraMeterFeedback.Phase.SETTLING||ready)&&frame!=null?frame.progress():0;
        progress.setProgress(percent);progress.setProgressTintList(ColorStateList.valueOf(accent));
        if(frame!=null&&frame.count>0&&Double.isFinite(frame.spread)&&(phase==CameraMeterFeedback.Phase.SETTLING||ready)){
            sampling.setText(String.format(Locale.US,tr("%d samples · %.1f s · spread %.2f EV","%d 帧采样 · %.1f 秒 · 波动 %.2f EV"),frame.count,frame.duration/1000.0,frame.spread));
        }else sampling.setText(tr("A fresh, stable exposure is needed to save.","读数最新且稳定后，才可保存。"));
        target.accent=accent;target.ready=ready;target.invalidate();
        save.setEnabled(ready);
        if(phase==previous)return;
        previous=phase;
        String title,hint;
        switch(phase){
            case READY:title=tr("✓ Stable · ready to save","✓ 读数稳定，可以保存");hint=tr("Keep this framing. Save returns to the slate.","保持当前构图，保存后返回识别板。");break;
            case SETTLING:title=tr("Hold steady · collecting samples","保持不动 · 正在采样");hint=tr("Keep the same framing until the guide turns green.","保持同一构图，等测光框变绿。");break;
            case SEARCHING:title=tr("Adjusting exposure…","正在调整曝光…");hint=tr("Aim at a lit wall or floor, away from the lamp.","对准受光墙面或地面，避开灯泡本身。");break;
            case CLIPPED:title=tr("! Center preview is too bright","! 中央预览过亮");hint=tr("Move away from lamps or glare; aim at a lit surface.","移开灯泡或反光，换一个受光表面。");break;
            case LIMITED:title=tr("! Beyond the exposure range","! 超出曝光范围");hint=tr("Choose a less extreme surface brightness.","换一个亮度适中的受光表面。");break;
            case STALE:title=tr("Preview data has paused","画面数据暂未更新");hint=tr("Wait for fresh readings before saving.","等待读数恢复后再保存。");break;
            case UNAVAILABLE:title=tr("! Exposure data unavailable","! 曝光数据不可用");hint=tr("Go back to use the lux sensor or manual EV.","返回后可改用照度传感器或手动输入。");break;
            case PREVIEW:title=tr("Waiting for camera preview…","等待相机画面…");hint=tr("Highlight checking resumes with the preview.","画面恢复后会继续检测高光。");break;
            default:title=tr("1 · Aim at a lit surface","1 · 对准受光表面");hint=tr("Move a wall or floor into the viewfinder.","让墙面或地面进入取景画面。");
        }
        status.setText(title);status.setTextColor(accent);instruction.setText(hint);
        int step=ready?2:phase==CameraMeterFeedback.Phase.SETTLING?1:0;
        for(int i=0;i<steps.length;i++){steps[i].setTextColor(i==step?accent:MUTED);steps[i].setBackground(round(i==step?0xff29352d:Color.TRANSPARENT,8));}
        save.setText(ready?tr("Save EV to slate","保存 EV 到识别板"):tr("Waiting for a usable reading…","等待可用读数…"));
        save.setTextColor(ready?0xff122218:MUTED);save.setBackground(round(ready?GREEN:0xff303b33,12));
    }

    private final class Target extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float frameWidth,frameHeight;boolean center,known,ready;int accent=AMBER;
        Target(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(!known||frameWidth<=0||frameHeight<=0)return;
            float cx=getWidth()/2f,cy=getHeight()/2f;
            RectF full=new RectF(cx-frameWidth/2,cy-frameHeight/2,cx+frameWidth/2,cy+frameHeight/2);
            RectF box=center?new RectF(cx-frameWidth*.1f,cy-frameHeight*.1f,cx+frameWidth*.1f,cy+frameHeight*.1f):new RectF(full.left+dp(8),full.top+dp(8),full.right-dp(8),full.bottom-dp(8));
            if(center){paint.setStyle(Paint.Style.FILL);paint.setColor(0x65000000);canvas.drawRect(full.left,full.top,full.right,box.top,paint);canvas.drawRect(full.left,box.bottom,full.right,full.bottom,paint);canvas.drawRect(full.left,box.top,box.left,box.bottom,paint);canvas.drawRect(box.right,box.top,full.right,box.bottom,paint);}
            float length=Math.min(dp(19),Math.min(box.width(),box.height())*.3f);
            Path corners=new Path();
            corners.moveTo(box.left,box.top+length);corners.lineTo(box.left,box.top);corners.lineTo(box.left+length,box.top);
            corners.moveTo(box.right-length,box.top);corners.lineTo(box.right,box.top);corners.lineTo(box.right,box.top+length);
            corners.moveTo(box.right,box.bottom-length);corners.lineTo(box.right,box.bottom);corners.lineTo(box.right-length,box.bottom);
            corners.moveTo(box.left+length,box.bottom);corners.lineTo(box.left,box.bottom);corners.lineTo(box.left,box.bottom-length);
            Path cross=new Path();cross.moveTo(cx-dp(7),cy);cross.lineTo(cx+dp(7),cy);cross.moveTo(cx,cy-dp(7));cross.lineTo(cx,cy+dp(7));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(dp(6));paint.setColor(0xd9000000);canvas.drawPath(corners,paint);canvas.drawPath(cross,paint);
            paint.setStrokeWidth(dp(3));paint.setColor(accent);canvas.drawPath(corners,paint);paint.setStrokeWidth(dp(2));canvas.drawPath(cross,paint);
            String hint=center?(ready?tr("Ready to save","可以保存"):tr("Aim at this box","对准框内测光")):tr("Whole frame · crosshair is a guide","全画面测光 · 十字仅辅助瞄准");
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);paint.setStyle(Paint.Style.FILL);
            float half=Math.min(getWidth()/2f-dp(12),paint.measureText(hint)/2+dp(10));
            float y=center?Math.min(full.bottom-dp(26),box.bottom+dp(14)):full.bottom-dp(38);
            RectF tag=new RectF(cx-half,y,cx+half,y+dp(27));paint.setColor(0xeb101712);canvas.drawRoundRect(tag,dp(8),dp(8),paint);paint.setColor(accent);canvas.drawText(hint,cx,tag.centerY()-(paint.ascent()+paint.descent())/2,paint);
        }
    }
}
