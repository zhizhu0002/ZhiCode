package black.android.content.pm;

import android.content.pm.PackageParser;
import android.content.pm.PackageParser.Package;

import java.io.File;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;

/**
 * Android 7.0+ 的 {@code PackageParser.parsePackage} 三参数重载。
 *
 * <h3>为什么必须有这个接口</h3>
 *
 * AOSP 的签名随版本变过：
 *
 * <pre>
 *   API 23 (M)     : Package parsePackage(File, int)
 *   API 24 (N) 起  : Package parsePackage(File, int, boolean verify)
 * </pre>
 *
 * 而 {@code PackageParserCompat.parsePackage} 原先在 {@code API_LEVEL >= M} 时
 * **一律**调用 {@link PackageParserMarshmallow} 那个 2 参数版本 —— 在 Android 7+
 * 上反射按"名字 + 参数类型"匹配，找不到就抛异常，异常又被
 * {@code BPackageManagerService.parserApk} 的 `catch (Throwable)` 吞掉，
 * 最终上层只看到一句 `parser apk error.`。
 *
 * <p>症状是**任何 APK 都装不上**，与文件本身无关 —— 这一点很容易被误判成
 * "这个 APK 有问题"，从而在错误的方向上排查很久。
 *
 * <p>BlackBox 上游的 {@code PackageParserPie} 只补了 {@code collectCertificates}，
 * 没补 {@code parsePackage}，所以这条缺口一直留着。
 */
@BClassName("android.content.pm.PackageParser")
public interface PackageParserNougatParse {

    /**
     * API 24+ 的三参数重载。
     *
     * @param verify 是否校验签名。装第三方包时传 {@code false} ——
     *               我们随后会单独调 {@code collectCertificates}，
     *               这里再校验一遍是重复工作，而且在部分 ROM 上会因
     *               "certificates already collected" 状态冲突而失败。
     */
    @BMethod
    Package parsePackage(File file, int flags, boolean verify);
}
