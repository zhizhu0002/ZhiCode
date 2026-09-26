package com.zhizhu.zhicode.sandbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.zhizhu.zhicode.compose.MainActivity;

/** Keeps the IQ Code main process foreground while a virtual guest is being debugged. */
public final class SandboxKeeper extends Service {
    private static final String CHANNEL="iq_sandbox_guard";
    private static final int ID=19221;
    private static final String ACTION_STOP="com.zhizhu.zhicode.sandbox.GUARD_STOP";

    public static void start(Context c){
        Intent i=new Intent(c,SandboxKeeper.class);
        try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}catch(Throwable e){SandboxConsole.event("SandboxGuard 启动失败: "+e);}
    }
    public static void stop(Context c){try{c.stopService(new Intent(c,SandboxKeeper.class));}catch(Throwable ignored){}}

    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"IQ 沙箱调试",NotificationManager.IMPORTANCE_LOW);ch.setDescription("沙箱应用运行时保持蜘蛛 Agent 执行链存活");NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(m!=null)m.createNotificationChannel(ch);}startForeground(ID,notification());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopForeground(true);stopSelf();return START_NOT_STICKY;}return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}

    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        PendingIntent content=PendingIntent.getActivity(this,0,open,piFlags);
        Intent stop=new Intent(this,SandboxKeeper.class).setAction(ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(this,1,stop,piFlags);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle("IQ 沙箱正在运行").setContentText("蜘蛛 Agent 保持在线 · 点击返回蜘蛛").setOngoing(true).setContentIntent(content)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"结束沙箱保活",stopPi).build()).build();
    }
}
