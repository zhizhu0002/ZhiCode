package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import top.niunaijun.blackbox.app.BActivityThread;

/**
 * Cross-process control plane used by Sandbox and Debug tools.
 *
 * UI actions are handled only by the process owning the resumed guest Activity.
 * proc_* actions are handled by the explicitly selected guest PID, which allows the Agent
 * to inspect modules/base addresses, threads, memory and load a debug .so without ptracing
 * unrelated Android processes.
 */
public final class SandboxAgentBridge {
    public static final String ACTION_CONTROL="com.zhizhu.zhicode.sandbox.AGENT_CONTROL";
    private static final long MAX_CAPTURE_PIXELS=1_000_000L;
    private static final long MAX_SCREENSHOT_BYTES=5L*1024L*1024L;
    private static final long SCREENSHOT_MAX_AGE_MS=60L*60L*1000L;
    private static final int MAX_RETAINED_SCREENSHOTS=8;
    private static final long REQUEST_ARTIFACT_MAX_AGE_MS=2L*60L*1000L;
    private static final ExecutorService CAPTURE_IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"iq-sandbox-capture-io");t.setDaemon(true);return t;});
    private static final AtomicReference<String> ACTIVE_CAPTURE=new AtomicReference<>();
    private static volatile boolean registered;
    private SandboxAgentBridge(){}

    public static synchronized void register(Context context){
        if(registered)return; registered=true;
        IntentFilter f=new IntentFilter(ACTION_CONTROL);
        BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){handle(c,i);}};
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,f);
    }

    public static JSONObject request(Context context,String action,String target,JSONObject payload,int timeoutMs)throws Exception{
        int pid=payload==null?-1:payload.optInt("pid",-1);
        return request(context,action,target,pid,payload,timeoutMs);
    }

    public static JSONObject request(Context context,String action,String target,int targetPid,JSONObject payload,int timeoutMs)throws Exception{
        File dir=resultDir(context);pruneRequestArtifacts(dir);String id=Long.toHexString(System.nanoTime())+"-"+Process.myPid();
        File result=new File(dir,id+".json"),claim=new File(dir,id+".claim");if(result.exists())result.delete();
        long end=SystemClock.uptimeMillis()+Math.max(500,timeoutMs);
        // inspect 也产出截图，所以重发与超时文案要跟 screenshot 同一套。
        boolean screenshot="screenshot".equals(action)||"inspect".equals(action);
        Intent intent=new Intent(ACTION_CONTROL).setPackage(context.getPackageName());
        intent.putExtra("request_id",id).putExtra("action",action).putExtra("target_package",target==null?"":target.trim())
            .putExtra("target_pid",targetPid).putExtra("deadline_uptime_ms",end).putExtra("payload",payload==null?"{}":payload.toString());
        context.sendBroadcast(intent);
        long nextSend=SystemClock.uptimeMillis()+250L;
        while(SystemClock.uptimeMillis()<end){
            if(result.isFile()){
                String text=readFile(result);result.delete();return new JSONObject(text);
            }
            long now=SystemClock.uptimeMillis();
            if(screenshot&&now>=nextSend&&!claim.isFile()){context.sendBroadcast(intent);nextSend=now+250L;}
            Thread.sleep(35);
        }
        String kind=action!=null&&action.startsWith("proc_")?"目标沙箱进程":screenshot?"截图桥（Guest Activity 尚未进入前台或截图超时）":"前台沙箱 Activity";
        return new JSONObject().put("ok",false).put("error",kind+"未响应；确认 Guest 已启动且 package 仍然有效");
    }

    private static void handle(Context context,Intent intent){
        String action=intent.getStringExtra("action");
        String id=intent.getStringExtra("request_id");
        String target=intent.getStringExtra("target_package");target=target==null?"":target.trim();
        int targetPid=intent.getIntExtra("target_pid",-1);
        long deadline=intent.getLongExtra("deadline_uptime_ms",SystemClock.uptimeMillis()+5000L);
        final String guestPkg=safeVirtualPackage();
        final int currentPid=Process.myPid();
        if(targetPid>0&&targetPid!=currentPid)return;
        if(!target.isEmpty()&&!target.equals(guestPkg))return;
        JSONObject payload; try{payload=new JSONObject(intent.getStringExtra("payload"));}catch(Throwable e){payload=new JSONObject();}
        final JSONObject p=payload;

        if(action!=null&&action.startsWith("proc_")){
            // Ignore the host/main process. Raw process debugging is only valid inside a bound guest.
            if(guestPkg.isEmpty()||guestPkg.equals(context.getPackageName()))return;
            new Thread(()->{
                JSONObject out;
                try{out=SandboxProcessDebug.dispatch(context,action,p);}
                catch(Throwable e){out=error(e);}
                writeResult(context,id,out);
            },"iq-sandbox-proc-debug").start();
            return;
        }

        final Activity a=ZhiSandboxEngine.resumedActivity();final String pkg=ZhiSandboxEngine.resumedPackage();
        if(a==null||pkg==null||pkg.isEmpty())return;
        if(!target.isEmpty()&&!target.equals(pkg))return;
        final boolean inspect="inspect".equals(action);
        if("screenshot".equals(action)||inspect){
            final boolean wantUi=inspect||p.optBoolean("with_ui",false);
            final boolean annotate=inspect||p.optBoolean("annotate",false);
            final boolean fullTree=p.optBoolean("full_tree",false);
            try{a.runOnUiThread(()->{
                if(!screenshotActivityReady(a,pkg)||SystemClock.uptimeMillis()>=deadline)return;
                if(!claimScreenshot(context,id,deadline))return;
                if(!ACTIVE_CAPTURE.compareAndSet(null,id)){JSONObject busy=error(new IllegalStateException("截图桥正在处理另一个请求"),"busy","等上一个截图返回再试；同一时刻只允许一个截图请求");CAPTURE_IO.execute(()->writeResult(context,id,busy));return;}
                /*
                 * UI 快照必须在**截图之前**、在 UI 线程上取一次。
                 * 事后补取没有意义：截图与 dump 之间界面可能已经变了，
                 * 而模型会把两件事当成同一时刻的状态去推理。
                 */
                JSONArray ui=null;
                if(wantUi){try{ui=dumpUi(a,fullTree);}catch(Throwable e){SandboxDebugLog.event("inspect UI 快照失败: "+id+" / "+e);}}
                new CaptureSession(a,context,id,pkg,deadline,annotate,ui,action).start();
            });}catch(Throwable e){SandboxDebugLog.event("截图桥调度失败: "+id+" / "+e);}
            return;
        }
        a.runOnUiThread(()->{
            JSONObject out=new JSONObject();
            try{
                out.put("ok",true).put("package",pkg).put("activity",a.getClass().getName()).put("pid",Process.myPid());
                switch(action==null?"":action){
                    case "dump_ui": out.put("ui",dumpUi(a,p.optBoolean("full_tree",false))); break;
                    case "find_node": out.put("matches",findNodes(a,p)); break;
                    case "click_node": findByPath(a,p.optString("node","")).performClick(); break;
                    case "long_click_node": findByPath(a,p.optString("node","")).performLongClick(); break;
                    case "set_text": setText(findByPath(a,p.optString("node","")),p.optString("text","")); break;
                    case "tap": tap(a,(float)p.optDouble("x",0d),(float)p.optDouble("y",0d)); break;
                    case "swipe": swipe(a,(float)p.optDouble("x1",0d),(float)p.optDouble("y1",0d),(float)p.optDouble("x2",0d),(float)p.optDouble("y2",0d),Math.max(80,p.optInt("duration_ms",320))); break;
                    case "input_text": inputText(a,p.optString("text","")); break;
                    case "back": a.onBackPressed(); break;
                    case "finish": a.finish(); break;
                    default: out.put("ok",false).put("error","未知控制动作: "+action);
                }
            }catch(Throwable e){out=error(e);}
            writeResult(context,id,out);
        });
    }

    private static JSONObject error(Throwable e){return error(e,null,null);}

    /**
     * 结构化失败。
     *
     * <p>只回一句 {@code error} 时，模型无法区分「没启动」「超时」「节点失效」，
     * 只能换个参数重试 —— 那是把一次可诊断的失败变成三次盲试。
     * {@code stage} 说明卡在哪一步，{@code hint} 说明下一步该做什么；两者都只用于展示，
     * 不参与任何判断。
     */
    private static JSONObject error(Throwable e,String stage,String hint){
        JSONObject out=new JSONObject();
        try{
            out.put("ok",false).put("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));
            if(stage!=null&&!stage.isEmpty())out.put("stage",stage);
            if(hint!=null&&!hint.isEmpty())out.put("hint",hint);
        }catch(Exception ignored){}
        return out;
    }
    private static String safeVirtualPackage(){try{String p=BActivityThread.getAppPackageName();return p==null?"":p;}catch(Throwable ignored){return "";}}

    /**
     * UI 树快照。
     *
     * @param fullTree false 时只保留「值得看」的节点 —— 有文本、有 contentDescription、
     *                 可点击，或本身接受输入。这是给识图模型用的形态：它要的是
     *                 「屏幕上这些东西分别是什么、点哪个」，不是 1800 个布局容器。
     */
    private static JSONArray dumpUi(Activity a,boolean fullTree)throws Exception{
        JSONArray out=new JSONArray(); View root=a.getWindow().getDecorView(); walk(root,"0",out,0,fullTree,new int[]{0}); return out;
    }

    /**
     * 递归收集节点。
     *
     * <p>{@code counter[0]} 是**可交互节点的序号**，从 1 开始 —— 与
     * {@code annotate} 画在截图上的编号是同一个计数器，所以模型看到「3 号」
     * 就能在 {@code ui} 里找到 {@code ref:3} 那个节点。两个编号来源必须同源，
     * 否则标注就成了误导。
     */
    private static void walk(View v,String path,JSONArray out,int depth,boolean fullTree,int[] counter)throws Exception{
        if(v==null||depth>40||out.length()>1800)return;
        int[] xy=new int[2]; v.getLocationOnScreen(xy);
        CharSequence desc=v.getContentDescription();
        CharSequence text=v instanceof TextView?((TextView)v).getText():null;
        boolean interesting=v.isClickable()||(desc!=null&&desc.length()>0)||(text!=null&&text.length()>0)
                ||v instanceof android.widget.EditText||v.isFocusable();
        if(fullTree||interesting){
            JSONObject o=new JSONObject();
            o.put("node",path).put("class",v.getClass().getName()).put("bounds",xy[0]+","+xy[1]+","+(xy[0]+v.getWidth())+","+(xy[1]+v.getHeight()))
                .put("visible",v.getVisibility()==View.VISIBLE).put("enabled",v.isEnabled()).put("clickable",v.isClickable()).put("focusable",v.isFocusable());
            if(desc!=null&&desc.length()>0)o.put("content_desc",trim(desc.toString(),240));
            if(text!=null&&text.length()>0)o.put("text",trim(text.toString(),400));
            if(interesting&&v.getWidth()>0&&v.getHeight()>0){counter[0]++;o.put("ref",counter[0]);}
            out.put(o);
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)walk(g.getChildAt(i),path+"/"+i,out,depth+1,fullTree,counter);}
    }

    /**
     * 按文本 / contentDescription / 类名子串搜节点。
     *
     * <p>为什么不是让模型自己翻 {@code dump_ui}：一份真机界面的树动辄几百个节点，
     * 进了上下文以后模型对「第 137 个节点的 bounds 是什么」这类问题并不可靠。
     * 把它变成一次查询，答案就只有一条。
     *
     * <p>{@code match} 默认 {@code text}；{@code text} 参数是**要搜的关键字**
     * （不是要填的文本）。匹配忽略大小写。
     */
    private static JSONArray findNodes(Activity a,JSONObject p)throws Exception{
        String keyword=p.optString("text","").trim();
        if(keyword.isEmpty())throw new IllegalArgumentException("find_node 需要 text（要搜索的关键字）");
        String match=p.optString("match","text").trim().toLowerCase();
        String needle=keyword.toLowerCase(java.util.Locale.ROOT);
        JSONArray all=dumpUi(a,p.optBoolean("full_tree",false));
        JSONArray hits=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject o=all.optJSONObject(i); if(o==null)continue;
            String hay;
            switch(match){
                case "content_desc": hay=o.optString("content_desc",""); break;
                case "class": hay=o.optString("class",""); break;
                default: hay=o.optString("text",""); break;
            }
            if(hay.toLowerCase(java.util.Locale.ROOT).contains(needle))hits.put(o);
        }
        return hits;
    }
    private static View findByPath(Activity a,String path){
        if(path==null||path.trim().isEmpty())throw new IllegalArgumentException("node 不能为空");
        String[] parts=path.split("/"); View current=a.getWindow().getDecorView();
        for(int i=1;i<parts.length;i++){if(!(current instanceof ViewGroup))throw new IllegalArgumentException("node 路径不是容器: "+path);int idx=Integer.parseInt(parts[i]);ViewGroup g=(ViewGroup)current;if(idx<0||idx>=g.getChildCount())throw new IllegalArgumentException("node 已失效: "+path);current=g.getChildAt(idx);}return current;
    }
    private static void setText(View v,String text){
        if(v instanceof TextView){((TextView)v).setText(text);return;}
        v.requestFocus(); EditorInfo info=new EditorInfo(); InputConnection ic=v.onCreateInputConnection(info); if(ic==null)throw new IllegalStateException("目标 View 不接受文本输入"); ic.commitText(text,1);
    }
    private static void tap(Activity a,float x,float y){View root=a.getWindow().getDecorView();long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);MotionEvent up=MotionEvent.obtain(now,now+45,MotionEvent.ACTION_UP,x,y,0);try{root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}}
    private static void swipe(Activity a,float x1,float y1,float x2,float y2,int duration){View root=a.getWindow().getDecorView();long start=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x1,y1,0);root.dispatchTouchEvent(down);down.recycle();int steps=12;for(int i=1;i<=steps;i++){float f=i/(float)steps;long t=start+(long)(duration*f);MotionEvent m=MotionEvent.obtain(start,t,i==steps?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x1+(x2-x1)*f,y1+(y2-y1)*f,0);root.dispatchTouchEvent(m);m.recycle();}}
    private static void inputText(Activity a,String text){View v=a.getCurrentFocus();if(v==null)throw new IllegalStateException("当前没有获得焦点的输入控件");EditorInfo info=new EditorInfo();InputConnection ic=v.onCreateInputConnection(info);if(ic==null)throw new IllegalStateException("当前焦点不接受文本输入");ic.commitText(text,1);}
    private static boolean screenshotActivityReady(Activity activity,String pkg){
        if(activity==null||activity.isFinishing()||(Build.VERSION.SDK_INT>=17&&activity.isDestroyed())||activity.getWindow()==null)return false;
        View root=activity.getWindow().getDecorView();return activity==ZhiSandboxEngine.resumedActivity()&&pkg.equals(ZhiSandboxEngine.resumedPackage())&&root.isAttachedToWindow()&&root.getWidth()>0&&root.getHeight()>0;
    }

    /** Capture the composed Guest window. PixelCopy includes SurfaceView/GL/Vulkan layers that View.draw() misses. */
    private static final class CaptureSession {
        private final Activity activity;private final Context context;private final String id,pkg;private final long deadline;
        private final Handler main=new Handler(Looper.getMainLooper());private final AtomicBoolean completed=new AtomicBoolean(),overlayRestored=new AtomicBoolean();
        private View root;private HandlerThread pixelThread;
        private final Runnable timeout=()->fail(new IllegalStateException("截图内部超时；Guest 窗口可能已暂停或失效"),"timeout","确认 guest 已启动并在前台（Sandbox status → launch → inspect）");

        /** 截图前取的 UI 快照；null 表示调用方没要。 */
        private final JSONArray uiSnapshot;
        /** 是否在图上叠编号框。 */
        private final boolean annotate;
        /** 原始动作名（screenshot / inspect），回写在结果里。 */
        private final String actionName;

        CaptureSession(Activity activity,Context context,String id,String pkg,long deadline,
                       boolean annotate,JSONArray uiSnapshot,String actionName){
            this.activity=activity;this.context=context;this.id=id;this.pkg=pkg;this.deadline=deadline;
            this.annotate=annotate;this.uiSnapshot=uiSnapshot;this.actionName=actionName;
        }

        void start(){
            try{
                if(!activityUsable())throw new IllegalStateException("Guest Activity 已失效或不在前台");
                root=activity.getWindow().getDecorView();int w=root.getWidth(),h=root.getHeight();
                if(w<=0||h<=0)throw new IllegalStateException("窗口尚未完成布局");
                long remaining=deadline-SystemClock.uptimeMillis();if(remaining<=250L)throw new IllegalStateException("截图请求已过期");
                main.postDelayed(timeout,Math.max(100L,remaining-200L));
                /*
                 * 悬浮条**不在**这棵树里：它是独立的系统级窗口
                 * （`TYPE_APPLICATION_OVERLAY`），PixelCopy 拍的是本 Activity 的
                 * 窗口，天然拍不到它 —— 所以这里不再需要"临时隐藏"那一步。
                 * 早先它挂在 DecoView 上时确实要藏，见那版的历史注释。
                 */
                root.postOnAnimation(this::captureFrame);
            }catch(Throwable e){fail(e,"start");}
        }

        private void captureFrame(){
            if(completed.get())return;
            try{
                if(!activityUsable()||root==null||!root.isAttachedToWindow())throw new IllegalStateException("截图前 Guest 窗口已离开前台");
                int sourceW=root.getWidth(),sourceH=root.getHeight();int[] size=captureSize(sourceW,sourceH);
                if(Build.VERSION.SDK_INT<26){captureFallback("api<26",sourceW,sourceH,size[0],size[1]);return;}
                Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                pixelThread=new HandlerThread("iq-sandbox-pixelcopy");pixelThread.start();
                try{
                    PixelCopy.request(activity.getWindow(),bitmap,result->{
                        stopPixelThread();
                        if(completed.get()){recycle(bitmap);return;}
                        if(expired()){recycle(bitmap);fail(new IllegalStateException("PixelCopy 在截止时间后返回"),"expired-pixelcopy");return;}
                        if(result==PixelCopy.SUCCESS){restoreOverlay();encode(bitmap,sourceW,sourceH,size[0],size[1],"pixelcopy",null);}
                        else{recycle(bitmap);main.post(()->captureFallback("PixelCopy error "+result,sourceW,sourceH,size[0],size[1]));}
                    },new Handler(pixelThread.getLooper()));
                }catch(Throwable e){recycle(bitmap);stopPixelThread();captureFallback(e.getClass().getSimpleName()+": "+e.getMessage(),sourceW,sourceH,size[0],size[1]);}
            }catch(Throwable e){fail(e,"capture");}
        }

        private void captureFallback(String reason,int sourceW,int sourceH,int outW,int outH){
            if(completed.get())return;
            if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->captureFallback(reason,sourceW,sourceH,outW,outH));return;}
            Bitmap bitmap=null;boolean handedOff=false;
            try{
                if(expired())throw new IllegalStateException("fallback 已超过截图截止时间");
                if(!activityUsable()||root==null||!root.isAttachedToWindow())throw new IllegalStateException("fallback 时 Guest 窗口已失效");
                bitmap=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
                canvas.scale(outW/(float)sourceW,outH/(float)sourceH);root.draw(canvas);restoreOverlay();
                SandboxDebugLog.event("截图桥 fallback: "+id+" / "+reason);
                encode(bitmap,sourceW,sourceH,outW,outH,"view_draw_fallback",reason);handedOff=true;
            }catch(Throwable e){fail(e,"fallback");}
            finally{if(!handedOff)recycle(bitmap);}
        }

        private void encode(Bitmap bitmap,int sourceW,int sourceH,int outW,int outH,String method,String reason){
            CAPTURE_IO.execute(()->{
                if(completed.get()){recycle(bitmap);return;}
                if(expired()){recycle(bitmap);fail(new IllegalStateException("截图编码开始前已过期"),"expired-encode");return;}
                File file=null;
                try{
                    /*
                     * 标注必须在 saveBitmap **之前**做：那个方法在 finally 里 recycle
                     * 位图，之后再 draw 会抛 "Can't call up to a recycled bitmap"。
                     */
                    boolean annotated=false;
                    if(annotate&&uiSnapshot!=null){
                        try{annotated=drawAnnotations(bitmap,uiSnapshot,sourceW,sourceH,outW,outH);}
                        catch(Throwable e){SandboxDebugLog.event("截图标注失败（不影响截图本身）: "+id+" / "+e);}
                    }
                    file=saveBitmap(context,id,bitmap);
                    if(expired()){file.delete();fail(new IllegalStateException("截图编码完成时已过期"),"expired-encode","放慢节奏：一次只发一个截图请求");return;}
                    JSONObject out=new JSONObject().put("ok",true).put("package",pkg).put("activity",activity.getClass().getName()).put("pid",Process.myPid())
                        .put("path",file.getAbsolutePath()).put("width",outW).put("height",outH).put("source_width",sourceW).put("source_height",sourceH).put("capture_method",method);
                    if(actionName!=null&&!actionName.isEmpty())out.put("action",actionName);
                    if(reason!=null)out.put("fallback_reason",reason);
                    if(uiSnapshot!=null){
                        out.put("ui",uiSnapshot).put("ui_count",uiSnapshot.length());
                    }
                    if(annotate)out.put("annotated",annotated);
                    if(!finish(out,"success")&&file.isFile())file.delete();
                }catch(Throwable e){if(file!=null)file.delete();fail(e,"encode","截图已生成但写盘/编码失败；检查私有目录剩余空间");}
            });
        }

        private boolean activityUsable(){return screenshotActivityReady(activity,pkg);}
        private boolean expired(){return SystemClock.uptimeMillis()>=deadline;}
        private void restoreOverlay(){
            // 悬浮条已改成独立的系统级窗口，不在 guest 的视图树里；
            // PixelCopy 拍的又是本 Activity 的窗口 —— 没有需要恢复的东西了。
        }
        private void stopPixelThread(){HandlerThread thread=pixelThread;pixelThread=null;if(thread!=null)thread.quitSafely();}
        private void fail(Throwable error,String stage){fail(error,stage,null);}
        private void fail(Throwable error,String stage,String hint){finish(SandboxAgentBridge.error(error,stage,hint),stage);}
        private boolean finish(JSONObject out,String stage){
            if(!completed.compareAndSet(false,true))return false;
            main.removeCallbacks(timeout);stopPixelThread();restoreOverlay();
            CAPTURE_IO.execute(()->{writeResult(context,id,out);ACTIVE_CAPTURE.compareAndSet(id,null);SandboxDebugLog.event("截图桥 "+stage+": "+id+" / "+out.optString("error","ok"));});
            return true;
        }
    }

    private static int[] captureSize(int width,int height){
        if(width<=0||height<=0)return new int[]{1,1};long pixels=(long)width*(long)height;
        if(pixels<=MAX_CAPTURE_PIXELS)return new int[]{width,height};double scale=Math.sqrt(MAX_CAPTURE_PIXELS/(double)pixels);
        return new int[]{Math.max(1,(int)Math.floor(width*scale)),Math.max(1,(int)Math.floor(height*scale))};
    }

    /**
     * 在截图上给每个可交互节点叠一个编号框。
     *
     * <h3>为什么这件事值得做</h3>
     * 识图模型能从像素看出「这里有个按钮」，但它给不出这个按钮的
     * {@code node} 路径 —— 而 {@code click_node} 需要的正是路径。
     * 于是它只能退化成猜坐标去 {@code tap}，误差几个 dp 就点空。
     * 叠上编号之后，模型可以说「点 3 号」，而 3 号到路径的映射是**确定的**
     * （结果里的 {@code ui} 数组带同一个 {@code ref}）。
     *
     * <h3>编号来源必须与 UI 树同源</h3>
     * {@code ref} 由 {@link #walk} 在填 UI 树时一次性分配（可交互节点从 1 递增）。
     * 这里只是**照着画**，不重新编号 —— 两个编号来源一旦分叉，
     * 标注就变成了主动误导。
     *
     * @return 是否真的画了至少一个框
     */
    private static boolean drawAnnotations(Bitmap bitmap,JSONArray ui,int sourceW,int sourceH,int outW,int outH){
        if(bitmap==null||bitmap.isRecycled()||ui==null||sourceW<=0||sourceH<=0)return false;
        float sx=outW/(float)sourceW, sy=outH/(float)sourceH;
        Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(Math.max(2f,Math.min(outW,outH)/240f));
        box.setColor(0xFFFF2D95); // 亮品红：深色与浅色界面上都能看清
        Paint badge=new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setStyle(Paint.Style.FILL);
        badge.setColor(0xE6000000);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        Canvas canvas=new Canvas(bitmap);
        int drawn=0;
        for(int i=0;i<ui.length();i++){
            JSONObject o=ui.optJSONObject(i); if(o==null)continue;
            if(!o.has("ref"))continue;
            if(!o.optBoolean("visible",true))continue;
            String bounds=o.optString("bounds","");
            String[] parts=bounds.split(",");
            if(parts.length!=4)continue;
            int x1,y1,x2,y2;
            try{
                x1=Integer.parseInt(parts[0].trim());y1=Integer.parseInt(parts[1].trim());
                x2=Integer.parseInt(parts[2].trim());y2=Integer.parseInt(parts[3].trim());
            }catch(NumberFormatException e){continue;}
            float l=x1*sx,t=y1*sy,r=x2*sx,b=y2*sy;
            if(r<=l||b<=t)continue;
            // 完全在图外的节点不画：画了也只会浪费模型的注意力
            if(r<0||b<0||l>outW||t>outH)continue;
            canvas.drawRect(l,t,r,b,box);
            String text=Integer.toString(o.optInt("ref"));
            float textSize=Math.max(18f,Math.min(outW,outH)/38f);
            label.setTextSize(textSize);
            float radius=textSize*0.78f;
            float cx=l+radius+2f, cy=t+radius+2f;
            canvas.drawCircle(cx,cy,radius,badge);
            // 基线校正：drawText 的 y 是基线，不是中心
            Paint.FontMetrics fm=label.getFontMetrics();
            canvas.drawText(text,cx,cy-(fm.ascent+fm.descent)/2f,label);
            drawn++;
        }
        return drawn>0;
    }
    private static void recycle(Bitmap bitmap){if(bitmap!=null&&!bitmap.isRecycled())try{bitmap.recycle();}catch(Throwable ignored){}}
    private static File saveBitmap(Context context,String id,Bitmap bitmap)throws Exception{
        File dir=screenshotDir(context);pruneScreenshots(dir);File tmp=new File(dir,id+".png.tmp"),dst=new File(dir,id+".png");if(tmp.exists())tmp.delete();
        try{
            try(FileOutputStream out=new FileOutputStream(tmp)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG 编码失败");out.flush();out.getFD().sync();}
        }catch(Throwable e){tmp.delete();if(e instanceof Exception)throw (Exception)e;throw (Error)e;}
        finally{recycle(bitmap);}
        if(tmp.length()<=0||tmp.length()>MAX_SCREENSHOT_BYTES){long size=tmp.length();tmp.delete();throw new IllegalStateException("截图 PNG 大小异常: "+size+" bytes");}
        if(dst.exists()&&!dst.delete()){tmp.delete();throw new IllegalStateException("旧截图文件清理失败: "+dst);}
        if(!tmp.renameTo(dst)){tmp.delete();throw new IllegalStateException("截图文件原子发布失败: "+dst);}
        return dst;
    }
    private static File screenshotDir(Context c){Context host=ZhiSandboxEngine.hostContext();if(host==null)host=c;File dir=new File(host.getFilesDir(),"sandbox/screenshots");ensureDirectory(dir);return dir;}
    private static void pruneScreenshots(File dir){
        File[] files=dir.listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-SCREENSHOT_MAX_AGE_MS;int retained=0;
        for(File file:files){String name=file.getName();if(name.endsWith(".png.tmp")){if(file.lastModified()<cutoff)file.delete();continue;}if(!name.endsWith(".png"))continue;if(file.lastModified()<cutoff)file.delete();else retained++;}
        while(retained>=MAX_RETAINED_SCREENSHOTS){File oldest=null;files=dir.listFiles();if(files==null)break;for(File file:files)if(file.getName().endsWith(".png")&&(oldest==null||file.lastModified()<oldest.lastModified()))oldest=file;if(oldest==null||!oldest.delete())break;retained--;}
    }
    private static boolean claimScreenshot(Context c,String id,long deadline){
        if(id==null||id.isEmpty()||SystemClock.uptimeMillis()>=deadline)return false;
        try{File claim=new File(resultDir(c),id+".claim");boolean won=claim.createNewFile();if(won)claim.setLastModified(System.currentTimeMillis());return won;}
        catch(Throwable e){SandboxDebugLog.event("截图桥 claim 失败: "+id+" / "+e);JSONObject out=error(e);CAPTURE_IO.execute(()->writeResult(c,id,out));return false;}
    }
    private static File resultDir(Context c){Context host=ZhiSandboxEngine.hostContext();if(host==null)host=c;File dir=new File(host.getFilesDir(),"sandbox/agent-results");ensureDirectory(dir);return dir;}
    private static void ensureDirectory(File dir){if(!dir.isDirectory()&&!dir.mkdirs()&&!dir.isDirectory())throw new IllegalStateException("无法创建目录: "+dir);}
    private static void pruneRequestArtifacts(File dir){
        File[] files=dir.listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-REQUEST_ARTIFACT_MAX_AGE_MS;
        for(File file:files){String name=file.getName();if((name.endsWith(".claim")||name.endsWith(".tmp")||name.endsWith(".json"))&&file.lastModified()<cutoff)file.delete();}
    }
    private static String readFile(File f)throws Exception{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static void writeResult(Context c,String id,JSONObject out){
        if(id==null||id.isEmpty())return;File tmp=null;
        try{File dir=resultDir(c);tmp=new File(dir,id+".tmp");File dst=new File(dir,id+".json");byte[] data=out.toString().getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream stream=new FileOutputStream(tmp)){stream.write(data);stream.flush();stream.getFD().sync();}
            if(dst.exists()&&!dst.delete())throw new IllegalStateException("旧结果清理失败: "+dst);
            if(!tmp.renameTo(dst))throw new IllegalStateException("结果原子发布失败: "+dst);
        }catch(Throwable e){if(tmp!=null)tmp.delete();SandboxDebugLog.event("桥接结果写入失败: "+id+" / "+e);}
    }
    private static String trim(String s,int max){return s.length()<=max?s:s.substring(0,max)+"…";}
}
