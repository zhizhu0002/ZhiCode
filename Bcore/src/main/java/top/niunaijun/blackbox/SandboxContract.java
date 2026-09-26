package top.niunaijun.blackbox;

/**
 * 宿主层与引擎共享的沙箱契约常量。
 *
 * <p>为什么单独放一个类，而不是挂在 {@link BlackBoxCore} 上：
 * 宿主层要在**每个**进程的 {@code Application.attachBaseContext} 里判定进程角色，
 * 包括必须保持干净的主进程。若那些常量定义在 {@code BlackBoxCore} 上，读取它就会触发
 * 该类的静态初始化（{@code new BlackBoxCore()} 加 {@code resolveHostUserId()}），
 * 于是主进程又被拉回 BlackBox 里，破坏「主进程完全不 attach 引擎」这条约束。
 *
 * <p>{@code static final String} 是编译期常量，javac 会把它内联到使用点，
 * 因此引用本类**不会加载任何类**。
 *
 * <p>见 docs/sandbox-host.md。
 */
public final class SandboxContract {

    /** 沙箱控制器进程名后缀（不含宿主包名）。控制器 hookless，只编排 Binder/包服务。 */
    public static final String CONTROLLER_PROCESS_SUFFIX = ":zhisandbox";

    /** 引擎服务进程名后缀，承载 BlackBox 运行时与 native hook。 */
    public static final String SERVER_PROCESS_SUFFIX = ":black";

    /** Guest 代理进程名前缀。 */
    public static final String GUEST_PROCESS_PREFIX = ":p";

    /** Guest 代理进程池大小：{@code :p0} 到 {@code :p49}。 */
    public static final int GUEST_PROCESS_COUNT = 50;

    /** 沙箱内使用的用户 id。 */
    public static final int USER_ID = 0;

    private SandboxContract() {}
}
