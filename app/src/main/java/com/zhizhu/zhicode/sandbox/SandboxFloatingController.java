package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhizhu.zhicode.compose.MainActivity;
import com.zhizhu.zhicode.sandbox.SandboxDashboardActivity;

/** Tiny host-owned overlay injected into every resumed virtual Activity. No SYSTEM_ALERT_WINDOW is required. */
public final class SandboxFloatingController {
    public static final Integer OVERLAY_TAG=0x49515342; // "IQSB"
    private SandboxFloatingController(){}

    public static void attach(Activity activity,String pkg){
        if(activity==null||activity.isFinishing())return;
        SandboxDebugLog.event("准备添加日志悬浮窗: "+pkg+" / "+activity.getClass().getName());
        activity.runOnUiThread(()->{
            View decor=activity.getWindow().getDecorView(); if(!(decor instanceof ViewGroup))return;
            ViewGroup root=(ViewGroup)decor; if(root.findViewWithTag(OVERLAY_TAG)!=null)return;
            boolean day="day".equals(activity.getSharedPreferences("iqge_ui_preferences",0).getString("ui_theme","classic"));
            int panelColor=day?0xF2F7F6F2:0xF222201D;
            int chipColor=day?0xFFEEEBE4:0xFF2A2723;
            int buttonText=day?0xFF282723:0xFFF0ECE5;
            int accent=day?0xFF37694B:0xFF7EB288;
            LinearLayout panel=new LinearLayout(activity); panel.setTag(OVERLAY_TAG); panel.setOrientation(LinearLayout.HORIZONTAL); panel.setGravity(Gravity.CENTER_VERTICAL);
            panel.setPadding(dp(activity,5),dp(activity,4),dp(activity,5),dp(activity,4)); panel.setBackground(bg(panelColor,14)); panel.setElevation(dp(activity,4));
            TextView bubble=button(activity,"IQ",day?0xFFFFFFFF:0xFF12110F); bubble.setBackground(ripple(accent,bg(accent,18))); bubble.setContentDescription("IQ 沙箱悬浮控制"); panel.addView(bubble,new LinearLayout.LayoutParams(dp(activity,38),dp(activity,36)));
            LinearLayout actions=new LinearLayout(activity); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setVisibility(View.GONE);
            View sep=new View(activity); sep.setBackgroundColor(day?0x14000000:0x1AFFFFFF); LinearLayout.LayoutParams sepLp=new LinearLayout.LayoutParams(dp(activity,1),dp(activity,20)); sepLp.setMargins(dp(activity,5),0,dp(activity,5),0); actions.addView(sep,sepLp);
            TextView log=button(activity,"日志",buttonText); log.setBackground(ripple(day?0x22000000:0x33FFFFFF,bg(chipColor,14))); addChip(activity,actions,log,dp(activity,50));
            TextView back=button(activity,"返回",buttonText); back.setBackground(ripple(day?0x22000000:0x33FFFFFF,bg(chipColor,14))); addChip(activity,actions,back,dp(activity,50));
            TextView stop=button(activity,"停止",day?0xFFC2414B:0xFFFF9AA8); stop.setBackground(ripple(day?0x22000000:0x33FFFFFF,bg(chipColor,14))); addChip(activity,actions,stop,dp(activity,50));
            panel.addView(actions,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(activity,36)));
            final float[] drag={0,0,0,0,0,0}; final Runnable[] frame={null};
            bubble.setOnTouchListener((v,e)->{switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    drag[0]=e.getRawX();drag[1]=e.getRawY();drag[2]=panel.getTranslationX();drag[3]=panel.getTranslationY();drag[4]=drag[0];drag[5]=drag[1];
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    drag[4]=e.getRawX();drag[5]=e.getRawY();
                    if(frame[0]==null){frame[0]=()->{frame[0]=null;panel.setTranslationX(drag[2]+drag[4]-drag[0]);panel.setTranslationY(drag[3]+drag[5]-drag[1]);};panel.postOnAnimation(frame[0]);}
                    return true;
                case MotionEvent.ACTION_UP:
                    if(frame[0]!=null){panel.removeCallbacks(frame[0]);frame[0]=null;}
                    panel.setTranslationX(drag[2]+drag[4]-drag[0]);panel.setTranslationY(drag[3]+drag[5]-drag[1]);
                    panel.setLayerType(View.LAYER_TYPE_NONE,null);
                    if(Math.abs(drag[4]-drag[0])<10&&Math.abs(drag[5]-drag[1])<10){boolean expand=actions.getVisibility()!=View.VISIBLE;if(expand)SandboxMotion.fadeSlideIn(actions,dp(activity,-8),0f,150L);else actions.setVisibility(View.GONE);}
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if(frame[0]!=null){panel.removeCallbacks(frame[0]);frame[0]=null;}
                    panel.setLayerType(View.LAYER_TYPE_NONE,null);
                    return true;
            }return false;});
            log.setOnClickListener(v->showLog(activity)); back.setOnClickListener(v->openIQ(activity,false));
            stop.setOnClickListener(v->{new Thread(()->{try{ZhiSandboxEngine.stop(pkg);SandboxGuardService.stop(activity);}catch(Throwable ignored){} activity.runOnUiThread(()->openIQ(activity,false));},"iq-sandbox-stop").start();});
            activity.addContentView(panel,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.setX(dp(activity,10)); panel.setY(dp(activity,36));
            panel.setAlpha(0f); panel.setScaleX(.92f); panel.setScaleY(.92f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(SandboxMotion.durationFor(activity,190L))
                    .setInterpolator(SandboxMotion.emphasized()).start();
        });
    }

    static void detach(Activity activity){
        if(activity==null)return;
        activity.runOnUiThread(()->{
            ViewGroup root=(ViewGroup)activity.getWindow().getDecorView();
            View panel=root.findViewWithTag(OVERLAY_TAG);
            if(panel!=null)root.removeView(panel);
        });
    }

    private static void showLog(Activity activity){
        String theme=activity.getSharedPreferences("iqge_ui_preferences",0).getString("ui_theme","classic");
        boolean day="day".equals(theme);
        int dialogBg=day?0xFFF7F6F2:0xFF1C1A18;
        int bodyColor=day?0xFF282723:0xFFEAE3D8;
        int accent=day?0xFF37694B:0xFF7EB288;
        TextView body=text(activity,"正在读取日志…");
        body.setTextColor(bodyColor); body.setTextSize(10);
        body.setTypeface(Typeface.MONOSPACE); body.setTextIsSelectable(true); body.setGravity(Gravity.TOP);
        ScrollView scroll=new ScrollView(activity); scroll.setPadding(dp(activity,14),dp(activity,8),dp(activity,14),dp(activity,8)); scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("IQ 沙箱日志")
                .setView(scroll).setNegativeButton("关闭",null).setNeutralButton("复制全部",null)
                .setPositiveButton("刷新",null).create();
        dialog.setOnShowListener(v->{
            Window window=dialog.getWindow(); if(window!=null)window.setBackgroundDrawable(bg(dialogBg,20));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{
                ClipboardManager cm=(ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("IQ Sandbox log",body.getText()));
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->loadLog(activity,body));
        });
        dialog.show(); loadLog(activity,body);
    }

    private static void loadLog(Activity activity,TextView body){
        new Thread(()->{String value;try{value=SandboxDebugLog.snapshot(activity.getApplicationContext());}catch(Throwable e){value="读取日志失败: "+e;}String result=value;new Handler(Looper.getMainLooper()).post(()->{if(body.getWindowToken()!=null)body.setText(result);});},"iq-sandbox-log").start();
    }

    private static TextView text(Activity activity,String value){TextView t=new TextView(activity);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(10);return t;}

    private static void openIQ(Activity a,boolean debug){
        Intent i=new Intent(a,debug?SandboxDashboardActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("iq_sandbox_return",true); a.startActivity(i);
    }
    private static TextView button(Activity a,String s,int color){TextView t=new TextView(a);t.setText(s);t.setTextColor(color);t.setTextSize(11);t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);return t;}
    private static void addChip(Activity a,LinearLayout parent,TextView chip,int widthPx){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(widthPx,dp(a,36));p.setMargins(dp(a,3),0,dp(a,3),0);parent.addView(chip,p);}
    private static Drawable ripple(int color,Drawable content){return new RippleDrawable(ColorStateList.valueOf(color),content,null);}
    private static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private static int dp(Activity a,float v){return (int)(v*a.getResources().getDisplayMetrics().density+0.5f);}
}
