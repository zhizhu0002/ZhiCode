import java.nio.file.*;
public final class SandboxControllerStartupRaceTest {
  static void req(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
  public static void main(String[] a)throws Exception{
    Path r=Path.of(a.length==0?".":a[0]);
    String engine=Files.readString(r.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/ZhiSandbox.java"));
    String provider=Files.readString(r.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxRpcService.java"));
    // 界面已从手写 View 改成 Compose（SandboxBoard.java → SandboxBoard.kt），
    // 断言的语义没变（启动重试必须在），只是读取的文件后缀跟着改。
    String ui=Files.readString(r.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.kt"));
    String core=Files.readString(r.resolve("Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java"));
    req(engine.contains("CountDownLatch READY_LATCH") && engine.contains("awaitReady(long timeoutMs)"),"controller readiness gate missing");
    req(engine.contains("ensureCreateScheduled()") && engine.contains("new Handler(Looper.getMainLooper())"),"main-loop engine creation scheduling missing");
    // 断言「provider 会等引擎就绪，且等待上限是 12 秒」，而不是 awaitReady(12000)
    // 这一种书写形态——把 12000 提成命名常量（READY_TIMEOUT_MS）属于重写范围内的正常改动。
    String providerFlat=provider.replaceAll("\\s+","");
    req(providerFlat.contains("ZhiSandbox.ensureCreateScheduled()")
        && provider.contains("READY_TIMEOUT_MS = 12000")
        && providerFlat.contains("ZhiSandbox.awaitReady(READY_TIMEOUT_MS)"),"provider can still race Application.onCreate");
    req(ui.contains("retryOrShow") && ui.contains("沙箱后端启动中"),"dashboard startup retry missing");
    req(core.contains("attach:controller-ready") && core.contains("return;"),"dedicated controller does not exit attach before runtime hooks");
    req(core.contains("throw new IllegalStateException(\"ZhiSandbox controller initialization failed\""),"controller create failure can still be reported as ready");
    System.out.println("SandboxControllerStartupRaceTest PASS");
  }
}
