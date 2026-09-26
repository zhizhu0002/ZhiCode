import java.nio.file.*;
public final class SandboxHooklessControllerStructureTest {
  private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
  public static void main(String[] a)throws Exception{
    Path root=Path.of(a[0]);
    String core=Files.readString(root.resolve("Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java"));
    String manifest=Files.readString(root.resolve("app/src/main/AndroidManifest.xml"));
    String ui=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.java"));
    require(core.contains("attach:controller-hookless"),"controller hookless attach marker missing");
    require(core.contains("if (!dedicatedSandboxMain)"),"controller must skip native essential hooks");
    require(core.contains("if (isMainProcess())")&&core.contains("create:controller-ready"),"hookless controller create branch missing");
    require(core.contains("Hookless sandbox controller initialized"),"hookless controller marker missing");
    String activity=manifest.substring(manifest.indexOf("SandboxBoard"), manifest.indexOf("SandboxBoard")+300);
    require(!activity.contains("android:process=\":zhisandbox\""),"dashboard must stay in stable 蜘蛛 main process");
    require(ui.contains("SandboxRpc.call"),"dashboard must use IPC rather than direct BlackBox engine");
    require(ui.contains("startup-stage.txt"),"dashboard must expose persisted backend startup stage");
    System.out.println("SandboxHooklessControllerStructureTest PASS");
  }
}
