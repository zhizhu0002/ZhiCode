import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * 验收自建的 bootstrap：确认里面**不再有** /data/data/com.termux 路径。
 *
 * 用法：
 *   javac -d . VerifyBootstrap.java
 *   java -cp . VerifyBootstrap bootstrap-aarch64.zip com.zhizhu.code
 *
 * 退出码：0 = 通过，1 = 未通过。
 *
 * 判定标准是「不存在旧前缀**路径**」，而不是「不存在 com.termux 字样」——
 * 因为 fork 里若仍引用 Termux:API / termux-am 等插件，会留下
 * `com.termux.api`、`com.termux.termuxam` 这类**命名空间**字符串，那是正常的。
 */
public final class VerifyBootstrap {

    private static final String OLD_PREFIX = "/data/data/com.termux";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: java VerifyBootstrap <bootstrap.zip> <期望包名>");
            System.exit(64);
        }
        File zip = new File(args[0]);
        String pkg = args[1];
        if (!zip.isFile()) { System.err.println("[!] 找不到 " + zip); System.exit(66); }

        byte[] oldB = OLD_PREFIX.getBytes(StandardCharsets.UTF_8);
        byte[] newB = ("/data/data/" + pkg).getBytes(StandardCharsets.UTF_8);

        int files = 0, elfFiles = 0, newPrefixFiles = 0, execScripts = 0;
        long oldHits = 0, newHits = 0;
        int symlinkLines = 0;
        boolean sawSymlinks = false;
        List<String> offenders = new ArrayList<>();
        Map<String, Integer> oldBySection = new TreeMap<>();

        try (ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zip), 1 << 20))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) { drain(zin); continue; }
                byte[] d = readAll(zin);
                String name = e.getName();
                files++;

                if (name.equals("SYMLINKS.txt")) {
                    sawSymlinks = true;
                    String txt = new String(d, StandardCharsets.UTF_8);
                    for (String line : txt.split("\n")) if (!line.trim().isEmpty()) symlinkLines++;
                }

                int oh = count(d, oldB);
                if (oh > 0) {
                    oldHits += oh;
                    if (isElf(d)) {
                        Object[] t = sectionTable(d);
                        int i = 0, n = 0;
                        while ((i = indexOf(d, oldB, i)) >= 0) {
                            String sec = t == null ? "?" : sectionOf((String[]) t[0], (long[][]) t[1], i);
                            oldBySection.merge(sec, 1, Integer::sum);
                            i += oldB.length; n++;
                        }
                        if (offenders.size() < 25) offenders.add(name + "  (" + n + " 处)");
                    } else if (offenders.size() < 25) {
                        offenders.add("[文本] " + name + "  (" + oh + " 处)");
                    }
                }

                int nh = count(d, newB);
                if (nh > 0) { newHits += nh; newPrefixFiles++; }
                if (isElf(d)) elfFiles++;
                if (d.length > 1 && d[0] == '#' && d[1] == '!') execScripts++;
            }
        }

        System.out.println("=== 验收报告: " + zip.getName() + " ===");
        System.out.println("目标包名            : " + pkg);
        System.out.println("期望前缀            : /data/data/" + pkg + "/files/usr");
        System.out.println();
        System.out.println("文件总数            : " + files);
        System.out.println("ELF 数              : " + elfFiles);
        System.out.println("#! 脚本数           : " + execScripts);
        System.out.println("SYMLINKS.txt 行数   : " + (sawSymlinks ? String.valueOf(symlinkLines) : "缺失!"));
        System.out.println();
        System.out.println("新前缀命中文件数    : " + newPrefixFiles + "  (" + newHits + " 次)");
        System.out.println("旧前缀命中          : " + oldHits + " 次");

        if (oldHits > 0) {
            System.out.println();
            System.out.println("--- 旧前缀所在 section ---");
            oldBySection.forEach((k, v) -> System.out.printf("  %-16s %d 次%n", k, v));
            System.out.println();
            System.out.println("--- 残留文件（前 25 个）---");
            offenders.forEach(s -> System.out.println("  " + s));
        }

        boolean pass = oldHits == 0 && sawSymlinks && symlinkLines > 0 && newPrefixFiles > 0;
        System.out.println();
        if (pass) {
            System.out.println("[PASS] 旧前缀已彻底清除，可交回工程替换 assets/bootstrap-aarch64.zip。");
            System.out.println("       工程侧需同步：applicationId / TermuxConstants 兜底值 == \"" + pkg + "\"");
            System.exit(0);
        } else {
            System.out.println("[FAIL] 未通过：");
            if (oldHits > 0) System.out.println("       - 仍有 " + oldHits + " 处旧前缀路径");
            if (!sawSymlinks) System.out.println("       - 缺 SYMLINKS.txt");
            else if (symlinkLines == 0) System.out.println("       - SYMLINKS.txt 为空");
            if (newPrefixFiles == 0) System.out.println("       - 完全找不到新前缀，构建可能没用到新包名");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ ELF 解析

    static boolean isElf(byte[] d) {
        return d.length > 4 && d[0] == 0x7F && d[1] == 'E' && d[2] == 'L' && d[3] == 'F';
    }

    /** 返回 { String[] 节名, long[][] {offset,size} }；失败返回 null。 */
    static Object[] sectionTable(byte[] d) {
        boolean is64 = d[4] == 2;
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        long shoff; int shentsize, shnum, shstrndx;
        if (is64) {
            shoff = b.getLong(0x28); shentsize = b.getShort(0x3A) & 0xFFFF;
            shnum = b.getShort(0x3C) & 0xFFFF; shstrndx = b.getShort(0x3E) & 0xFFFF;
        } else {
            shoff = b.getInt(0x20) & 0xFFFFFFFFL; shentsize = b.getShort(0x2E) & 0xFFFF;
            shnum = b.getShort(0x30) & 0xFFFF; shstrndx = b.getShort(0x32) & 0xFFFF;
        }
        if (shoff <= 0 || shnum <= 0 || shoff >= d.length) return null;

        long[][] raw = new long[shnum][];
        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) i * shentsize;
            if (o + shentsize > d.length) break;
            long name, type, off, size;
            if (is64) {
                name = b.getInt((int) o) & 0xFFFFFFFFL; type = b.getInt((int) o + 4) & 0xFFFFFFFFL;
                off = b.getLong((int) o + 24); size = b.getLong((int) o + 32);
            } else {
                name = b.getInt((int) o) & 0xFFFFFFFFL; type = b.getInt((int) o + 4) & 0xFFFFFFFFL;
                off = b.getInt((int) o + 16) & 0xFFFFFFFFL; size = b.getInt((int) o + 20) & 0xFFFFFFFFL;
            }
            raw[i] = new long[]{name, type, off, size};
        }
        if (shstrndx >= shnum || raw[shstrndx] == null) return null;
        long shstrOff = raw[shstrndx][2];
        if (shstrOff <= 0 || shstrOff >= d.length) return null;

        String[] names = new String[shnum];
        long[][] out = new long[shnum][];
        for (int i = 0; i < shnum; i++) {
            if (raw[i] == null) { names[i] = "?"; continue; }
            int p = (int) (shstrOff + raw[i][0]);
            if (p < 0 || p >= d.length) { names[i] = "?"; continue; }
            int q = p; while (q < d.length && d[q] != 0) q++;
            names[i] = new String(d, p, q - p, StandardCharsets.UTF_8);
            out[i] = new long[]{raw[i][2], raw[i][3]};
        }
        return new Object[]{names, out};
    }

    static String sectionOf(String[] names, long[][] se, int off) {
        if (names == null || se == null) return "?";
        for (int i = 0; i < se.length; i++) {
            if (se[i] == null || se[i][1] <= 0) continue;
            if (off >= se[i][0] && off < se[i][0] + se[i][1]) return names[i];
        }
        return "<未映射>";
    }

    // ------------------------------------------------------------ 工具

    static int count(byte[] h, byte[] n) {
        if (n.length == 0) return 0;
        int c = 0, i = 0;
        while ((i = indexOf(h, n, i)) >= 0) { c++; i += n.length; }
        return c;
    }

    static int indexOf(byte[] h, byte[] n, int from) {
        outer:
        for (int i = Math.max(0, from); i + n.length <= h.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(1 << 16);
        byte[] b = new byte[1 << 16];
        int n;
        while ((n = in.read(b)) != -1) o.write(b, 0, n);
        return o.toByteArray();
    }

    static void drain(InputStream in) throws IOException {
        byte[] b = new byte[1 << 16];
        while (in.read(b) != -1) { /* skip */ }
    }
}
