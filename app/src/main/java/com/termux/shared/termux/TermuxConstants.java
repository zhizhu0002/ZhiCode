package com.termux.shared.termux;

import android.content.Context;

import com.zhizhu.zhicode.compose.BuildConfig;

import java.io.File;

/**
 * 内置 Termux 运行环境的路径单一来源。
 *
 * <p>原版（IQ-Code-Android）把 {@code com.iqge} 的绝对路径写成了 {@code static final} 字符串常量。
 * 那种写法有两个致命问题，本工程必须避开：
 *
 * <ol>
 *   <li><b>会被 javac 内联。</b>{@code static final String X = "字面量";} 是「编译期常量」，
 *       javac 会把值直接内联进全部调用点（本工程有 35 处）。一旦内联，运行期改这个字段毫无作用。
 *       所以这里刻意**不加 final**，让调用点编译成真实的 {@code getstatic} 字段读取。</li>
 *   <li><b>绑死了包名。</b>本工程是独立软件，包名是 {@code com.zhizhu.zhicode.compose}，
 *       数据目录是它自己的私有目录，绝不能再用 {@code /data/user/0/com.iqge}。</li>
 * </ol>
 *
 * <p>用法：在 {@code Application.attachBaseContext()} 里第一时间调用 {@link #configure}，
 * 之后所有调用点读到的就是本应用自己的路径，无需任何改动。
 */
public final class TermuxConstants {

    /**
     * 兜底默认值：仅在 {@link #configure} 之前被读到才会用到（例如单元测试）。
     *
     * <p>**故意不写死字符串**：直接引用 BuildConfig.APPLICATION_ID，也就是
     * app/build.gradle 里那一个 applicationId。全部路径都由它派生，
     * 换包名时只改 build.gradle 一行，代码侧零改动、也不会出现两处不一致。
     */
    private static final String DEFAULT_PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    private static final String DEFAULT_FILES_DIR_PATH = "/data/user/0/" + DEFAULT_PACKAGE_NAME + "/files";

    /**
     * 品牌短标识，**整份代码里只在这里定义一次**。
     *
     * <p>用来给 prefix 内部那些自有文件命名（{@code libexec/<slug>/}、
     * {@code <slug>-main.list}、{@code .<slug>-ok}、{@code dpkg.<slug>-real} …），
     * 以及各类 SharedPreferences 名、环境变量前缀、日志标记。
     *
     * <p>刻意集中成一个常量而不是到处写字符串字面量：这些名字分散在
     * Java、Kotlin、assets 里的 shell 脚本三方，任何一处漏改都会造成
     * "apt 钩子装了但脚本找不到"或"标记文件写在 A、检查在 B"这类**静默失效**。
     * shell 脚本侧通过 {@code @SLUG@} 占位符注入（见 RuntimeInstaller.writeTemplate）。
     */
    public static final String BRAND_SLUG = "zhicode";

    // ---------------------------------------------------------------- 可配置值
    // 注意：以下 4 个字段**故意不加 final**。加了就会被 javac 内联，运行期配置失效。

    /** 宿主包名。 */
    public static String TERMUX_PACKAGE_NAME = DEFAULT_PACKAGE_NAME;

    /** 数据根目录，Termux 约定为 {@code <data-dir>/files}。 */
    public static String TERMUX_FILES_DIR_PATH = DEFAULT_FILES_DIR_PATH;

    /** 数据目录，即 {@link #TERMUX_FILES_DIR_PATH} 的父目录（{@code /data/user/0/<pkg>}）。 */
    public static String TERMUX_DATA_DIR_PATH = "/data/user/0/" + DEFAULT_PACKAGE_NAME;

    /** {@code $HOME}。 */
    public static String TERMUX_HOME_DIR_PATH = DEFAULT_FILES_DIR_PATH + "/home";

    /** {@code $PREFIX}。 */
    public static String TERMUX_PREFIX_DIR_PATH = DEFAULT_FILES_DIR_PATH + "/usr";

    /** {@code $PREFIX/bin}。 */
    public static String TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin";

    /** {@code $PREFIX/bin/bash}，内置 shell。 */
    public static String TERMUX_BASH_PATH = TERMUX_BIN_PREFIX_DIR_PATH + "/bash";

    // ------------------------------------------------------------------ 便捷对象
    // 同样不加 final，{@link #configure} 会一并重建，保持与上面一致。

    public static File TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);
    public static File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);

    /** 是否已调用过 {@link #configure}。用独立布尔而不是比较路径，因为真实路径有可能恰好等于兜底默认值。 */
    private static boolean configured;

    private TermuxConstants() {}

    /**
     * 用当前应用自己的标识与私有目录初始化全部路径。
     *
     * <p>应在 {@code Application.attachBaseContext()} 中、任何其他类被加载之前调用一次。
     * 重复调用是幂等的。
     *
     * @param context 任意 Context（内部会取 {@code getApplicationContext()} 语义的字段）
     */
    public static void configure(Context context) {
        if (context == null) return;
        String pkg = context.getPackageName();
        File files = context.getFilesDir();
        if (pkg == null || pkg.isEmpty() || files == null) return;
        configure(pkg, files.getAbsolutePath());
    }

    /**
     * 同上，但显式给值。便于设置里覆盖成自定义目录，或在没有 Context 的场景下测试。
     *
     * @param packageName    宿主包名
     * @param filesDirPath   {@code <data-dir>/files} 的绝对路径
     */
    public static void configure(String packageName, String filesDirPath) {
        if (packageName == null || packageName.isEmpty()) return;
        if (filesDirPath == null || filesDirPath.isEmpty()) return;

        TERMUX_PACKAGE_NAME = packageName;
        TERMUX_FILES_DIR_PATH = filesDirPath;
        File parent = new File(filesDirPath).getParentFile();
        TERMUX_DATA_DIR_PATH = parent == null ? filesDirPath : parent.getAbsolutePath();
        TERMUX_HOME_DIR_PATH = filesDirPath + "/home";
        TERMUX_PREFIX_DIR_PATH = filesDirPath + "/usr";
        TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin";
        TERMUX_BASH_PATH = TERMUX_BIN_PREFIX_DIR_PATH + "/bash";

        TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);
        TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);

        configured = true;
    }

    /** 路径是否已按真实应用配置过（未被配置说明 {@link #configure} 还没跑到）。 */
    public static boolean isConfigured() {
        return configured;
    }
}
