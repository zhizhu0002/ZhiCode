package top.niunaijun.blackbox.utils.compat;

import android.content.pm.PackageParser;
import android.content.pm.PackageParser.Package;
import android.os.Build;
import android.util.DisplayMetrics;

import java.io.File;

import black.android.content.pm.BRPackageParser;
import black.android.content.pm.BRPackageParserLollipop;
import black.android.content.pm.BRPackageParserLollipop22;
import black.android.content.pm.BRPackageParserMarshmallow;
import black.android.content.pm.BRPackageParserNougat;
import black.android.content.pm.BRPackageParserNougatParse;
import black.android.content.pm.BRPackageParserPie;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

public class PackageParserCompat {

    public static final int[] GIDS = new int[]{};
    private static final int API_LEVEL = Build.VERSION.SDK_INT;
    private static final int myUserId = 0;


    public static PackageParser createParser(File packageFile) {
        if (API_LEVEL >= M) {
            return BRPackageParserMarshmallow.get()._new();
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            return BRPackageParserLollipop22.get()._new();
        } else if (API_LEVEL >= LOLLIPOP) {
            return BRPackageParserLollipop.get()._new();
        }
        return null;
    }

    /**
     * 解析 APK 清单。
     *
     * <h3>为什么 API 24+ 要单独一条分支</h3>
     *
     * {@code PackageParser.parsePackage} 的签名在 Android 7.0 变过：
     *
     * <pre>
     *   API 23     : parsePackage(File, int)
     *   API 24 起  : parsePackage(File, int, boolean verify)
     * </pre>
     *
     * 原实现在 {@code API_LEVEL >= M} 时**一律**走 2 参数版本。而 BlackReflection 是按
     * "方法名 + 参数类型"去反射匹配的，参数个数不对就找不到方法、抛异常 ——
     * 那个异常被 {@code BPackageManagerService.parserApk} 的 `catch (Throwable)` 吞掉，
     * 上层最终只看到 `parser apk error.`。
     *
     * <p>结果是**任何 APK 都装不上**（与文件无关）。这条缺口必须补，
     * 否则沙箱的"导入 APK"完全不可用。
     *
     * <p>{@code verify} 传 false：调用方随后会自己调 {@link #collectCertificates}，
     * 这里再校验一遍是重复工作。
     */
    public static Package parsePackage(PackageParser parser, File packageFile, int flags) throws Throwable {
        if (API_LEVEL >= N) {
            return BRPackageParserNougatParse.getWithException(parser)
                    .parsePackage(packageFile, flags, false);
        } else if (API_LEVEL >= M) {
            return BRPackageParserMarshmallow.getWithException(parser).parsePackage(packageFile, flags);
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            return BRPackageParserLollipop22.getWithException(parser).parsePackage(packageFile, flags);
        } else if (API_LEVEL >= LOLLIPOP) {
            return BRPackageParserLollipop.getWithException(parser).parsePackage(packageFile, flags);
        } else {
            return BRPackageParser.getWithException(parser).parsePackage(packageFile, null,
                    new DisplayMetrics(), flags);
        }
    }

    public static void collectCertificates(PackageParser parser, Package p, int flags) throws Throwable {
        if (BuildCompat.isPie()) {
            BRPackageParserPie.getWithException().collectCertificates(p, true);
        } else if (API_LEVEL >= N) {
            BRPackageParserNougat.getWithException().collectCertificates(p, flags);
        } else if (API_LEVEL >= M) {
            BRPackageParserMarshmallow.getWithException(parser).collectCertificates(p, flags);
        } else if (API_LEVEL >= LOLLIPOP_MR1) {
            BRPackageParserLollipop22.getWithException(parser).collectCertificates(p, flags);
        } else if (API_LEVEL >= LOLLIPOP) {
            BRPackageParserLollipop.getWithException(parser).collectCertificates(p, flags);
        } else {
            BRPackageParser.get(parser).collectCertificates(p, flags);
        }
    }
}
