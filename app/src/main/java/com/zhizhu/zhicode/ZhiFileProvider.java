package com.zhizhu.zhicode;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

/**
 * 只读的内容提供者，用于把本地文件交给 Android（最典型的用法是把自己构建出来的
 * APK 交给系统安装器）。
 *
 * <h3>三处刻意收紧的地方</h3>
 * <ol>
 *   <li>{@link #authority} 由 applicationId 派生，不写死。同一个设备上如果还存在
 *       另一份用同样 authority 的构建，第二个包会在**安装阶段**就被拒绝
 *       （{@code INSTALL_FAILED_CONFLICTING_PROVIDER}），连我们的代码都不会执行。</li>
 *   <li>{@link #openFile} 只接受 {@code r} / {@code rt}，其它模式一律拒绝。</li>
 *   <li>{@link #resolve} 用**根目录白名单 + 规范化后判越界**。这是本
 *       provider 真正的访问控制手段（见 {@link #openTypedAssetFile} 的说明）。</li>
 * </ol>
 *
 * <h3>允许分享的三个根</h3>
 * 手机共享存储、ZhiCode HOME、以及应用缓存目录。缓存也在其中是因为本应用会把中间产物
 * 写在那里，而给用户看/给别的应用用的东西常常就在那儿。
 */
public final class ZhiFileProvider extends ContentProvider {

    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final String MIME_FALLBACK = "application/octet-stream";
    private static final String FALLBACK_NAME = "file";

    private static final String SEGMENT_STORAGE = "storage";
    private static final String SEGMENT_SHARED = "shared";
    private static final String SEGMENT_HOME = "home";
    private static final String SEGMENT_CACHE = "cache";

    private static final String MODE_READ = "r";
    private static final String MODE_READ_TEXT = "rt";

    private static final String ERROR_READ_ONLY = "ZhiCode FileProvider is read-only";
    private static final String ERROR_NOT_FOUND = "文件不存在：";

    /**
     * 本 provider 的 authority。
     *
     * <p>清单里声明的是 {@code "${applicationId}.fileprovider"}，两边都从
     * applicationId 派生，所以无论这个构建用什么包名，两边必然一致。
     */
    public static String authority(Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    /** 把一个受支持的本地路径变成可授权的 content URI。 */
    public static Uri uriForFile(Context context, File requested) throws Exception {
        if (context == null || requested == null) throw new IllegalArgumentException("file is required");
        File file = requested.getCanonicalFile();
        if (!file.isFile()) throw new FileNotFoundException(ERROR_NOT_FOUND + file);

        File external = Environment.getExternalStorageDirectory().getCanonicalFile();
        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH).getCanonicalFile();
        File cache = context.getCacheDir().getCanonicalFile();
        Uri.Builder out = new Uri.Builder().scheme("content").authority(authority(context));

        if (isUnder(file, external)) {
            out.appendPath(SEGMENT_STORAGE).appendPath(SEGMENT_SHARED);
            appendRelative(out, external, file);
        } else if (isUnder(file, home)) {
            out.appendPath(SEGMENT_HOME);
            appendRelative(out, home, file);
        } else if (isUnder(file, cache)) {
            out.appendPath(SEGMENT_CACHE);
            appendRelative(out, cache, file);
        } else {
            throw new SecurityException("只允许分享手机共享存储、ZhiCode HOME 或缓存中的文件：" + file);
        }
        return out.build();
    }

    @Override public boolean onCreate() { return true; }

    /**
     * 猜 MIME 类型。
     *
     * <p>APK 单独判一次：{@link MimeTypeMap} 对 {@code apk} 的映射在部分设备上缺失，
     * 而类型不对会让安装器直接拒绝这个 URI。
     *
     * <p>文件解析不出来时退回用 URI 的最后一段 —— 至少还能按扩展名给出类型，
     * 而不是给一个万能的 {@code octet-stream}。
     */
    @Override public String getType(Uri uri) {
        File file = resolve(uri, false);
        String name = file == null ? lastSegment(uri) : file.getName();
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".apk")) return MIME_APK;
        int dot = lower.lastIndexOf('.');
        String extension = dot < 0 ? "" : lower.substring(dot + 1);
        String detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return detected == null ? MIME_FALLBACK : detected;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = resolve(uri, true);
        String[] columns = projection == null || projection.length == 0
            ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            // 其它列给 null，而不是猜一个值：宁可是空的，也不要给错的。
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!MODE_READ.equals(mode) && !MODE_READ_TEXT.equals(mode)) {
            throw new FileNotFoundException(ERROR_READ_ONLY);
        }
        return ParcelFileDescriptor.open(resolve(uri, true), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * 带类型地打开文件。
     *
     * <h3>为什么必须重写</h3>
     * 不重写会走父类的默认实现，而它在类型不匹配时直接抛
     * {@code FileNotFoundException("Can't open ... as type ...")}。
     * 问题在于 {@code ContentResolver.openAssetFileDescriptor(uri, "r")} 传下来的
     * 过滤器**是调用方的包名**，不是 MIME 类型；父类拿它和 {@link #getType}
     * （这里是 APK 类型）比较，必然不匹配。
     *
     * <p>后果是安装 APK 前那句「先试着打开一次，以便给出可读错误」的预校验
     * 会对**完全正常的 APK** 报「无法读取」，把一次成功的安装判成失败。
     *
     * <h3>安全性没有放松</h3>
     * 类型过滤从来不是本 provider 的访问控制手段 —— 真正的边界是 {@link #resolve}
     * 里的根目录白名单与越界检查，它在这里照常执行。我们只是声明「这个 URI 指向的
     * 文件我可以按任意类型提供读取」，这对一个只读、且已限定目录的 provider 是合理的；
     * 何况调用方要拿到的类型本来就由 {@link #getType} 决定。
     */
    @Override public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        return openAssetFile(uri, MODE_READ);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException(ERROR_READ_ONLY);
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException(ERROR_READ_ONLY);
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException(ERROR_READ_ONLY);
    }

    /**
     * URI → 文件，并执行越界检查。
     *
     * <h3>为什么必须先规范化再比较</h3>
     * {@code ..} 与符号链接都能让拼接出来的路径指到白名单之外。
     * 先 {@code getCanonicalFile()} 再判断，才能让「越界」这个结论成立。
     *
     * <p>路径首段决定用哪个根：{@code storage/shared} 是两段（历史原因，
     * 与 Android 的 {@code external} 路径形态对齐），另两个是一段。
     *
     * @param requireFile 是否要求目标是一个普通文件
     */
    private File resolve(Uri uri, boolean requireFile) {
        Context context = getContext();
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme()) || context == null
            || !authority(context).equals(uri.getAuthority())) {
            throw new SecurityException("无效的 ZhiCode content URI");
        }
        try {
            List<String> segments = uri.getPathSegments();
            if (segments == null || segments.isEmpty()) throw new FileNotFoundException("该 URI 里没有路径");

            File root = rootFor(segments, context);
            int firstRelative = isSharedStorage(segments) ? 2 : 1;

            File canonicalRoot = root.getCanonicalFile();
            File candidate = canonicalRoot;
            for (int i = firstRelative; i < segments.size(); i++) {
                candidate = new File(candidate, segments.get(i));
            }
            candidate = candidate.getCanonicalFile();
            if (!isUnder(candidate, canonicalRoot)) throw new SecurityException("路径越界");
            if (requireFile && !candidate.isFile()) {
                throw new FileNotFoundException(ERROR_NOT_FOUND + candidate);
            }
            return candidate;
        } catch (RuntimeException alreadyTyped) {
            // SecurityException / FileNotFoundException 是给调用方的最终结论，原样抛出。
            throw alreadyTyped;
        } catch (Exception failure) {
            // 其余（IO、URI 解析）归成参数错误 —— 调用方给的 URI 有问题。
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
    }

    private static boolean isSharedStorage(List<String> segments) {
        return segments.size() >= 2
            && SEGMENT_STORAGE.equals(segments.get(0))
            && SEGMENT_SHARED.equals(segments.get(1));
    }

    /** 白名单：首段（或首两段）决定根目录，其余一律拒绝。 */
    private static File rootFor(List<String> segments, Context context) {
        if (isSharedStorage(segments)) return Environment.getExternalStorageDirectory();
        if (SEGMENT_HOME.equals(segments.get(0))) return new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        if (SEGMENT_CACHE.equals(segments.get(0))) return context.getCacheDir();
        throw new SecurityException("不允许的共享根目录");
    }

    /**
     * file 是否在 root 之下（含 root 本身）。
     *
     * <p>比较时补上分隔符：只比前缀的话 {@code /data/foobar} 会被判成
     * 在 {@code /data/foo} 之下。
     */
    private static boolean isUnder(File file, File root) {
        String base = root.getPath();
        String path = file.getPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    /** 把相对路径逐段 append 进 URI builder。空段跳过（连续斜杠会产生空段）。 */
    private static void appendRelative(Uri.Builder out, File root, File file) {
        String relative = file.getPath().substring(root.getPath().length());
        while (relative.startsWith(File.separator)) relative = relative.substring(1);
        if (relative.isEmpty()) return;
        for (String part : relative.split("/")) {
            if (!part.isEmpty()) out.appendPath(part);
        }
    }

    private static String lastSegment(Uri uri) {
        String value = uri == null ? null : uri.getLastPathSegment();
        return value == null ? FALLBACK_NAME : value;
    }
}
