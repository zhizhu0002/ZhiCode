import java.nio.file.*;
public final class SandboxMainProcessRoleStructureTest {
  private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
  public static void main(String[] a)throws Exception{
    String core=Files.readString(Path.of("Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java"));
    String provider=Files.readString(Path.of("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxRpcService.java"));
    String role=Files.readString(Path.of("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxProcess.java"));
    require(core.contains("SandboxContract.CONTROLLER_PROCESS_SUFFIX"),":zhisandbox must be an explicit BlackBox main process");
    require(core.contains("processName.equals(sandboxMainProcess)"),"BlackBox process classifier must accept sandbox main");
    require(!provider.substring(provider.indexOf("onCreate()"), provider.indexOf("public Bundle call")).contains("ZhiSandbox.create()"),"provider must not run doCreate before Application.onCreate");
    require(role.contains("SandboxContract.CONTROLLER_PROCESS_SUFFIX")&&role.contains("SandboxContract.SERVER_PROCESS_SUFFIX"),"attach role must be allowlisted");
    require(role.contains("SandboxContract.GUEST_PROCESS_COUNT"),"only p0..p49 should be guest proxy processes");
    System.out.println("SandboxMainProcessRoleStructureTest PASS");
  }
}
