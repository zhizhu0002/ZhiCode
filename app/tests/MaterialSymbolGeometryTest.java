import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「每个字形在 24 格视口里到底占多大」的守卫。
 *
 * <h2>为什么要有它</h2>
 *
 * 这一整集字形是**从上游拷来的路径数据**（见 `ZhiMaterialIcons.kt` 的文件头），
 * 而"拷对了没有"这件事很微妙：路径字符串本身抄错一个数字，
 * 大多数情况下**照样能解析、照样能画**，只是形状歪了或者小了。
 * 编译器管不了，界面上也只有并排看才发现。
 *
 * 所以这里自己走一遍 SVG 路径，把每个字形**真实的墨迹范围**算出来，
 * 再和一份写死在下面的**基线表**逐个比对。基线表就是"当时量到的值"：
 * 数据被改动、视口换算被改动、上游换了字形，都会让某个格子对上不上而失败。
 * 它逼着改动的人**有意识地**更新那张表，而不是让形状悄悄漂走。
 *
 * <h2>走查器是怎么写的、凭什么可信</h2>
 *
 * SVG 路径是一套很小的语言：`M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z`，
 * 加上「省略命令字母就重复上一个命令」与「`M` 之后的坐标对按 `L` 处理」两条隐式规则。
 * 这里把它们全实现了：
 *
 * <ul>
 *   <li>直线：取端点；</li>
 *   <li>三次 / 二次贝塞尔：**解析求极值**（导数为零的 t），不是采样 ——
 *       采样要挑步长，步长选错就会把"刚好差一点"判成通过；</li>
 *   <li>圆弧：端点参数化 → 圆心参数化，然后在角度上密采样（256 段）。
 *       这是唯一一处采样：圆弧的极值角度要按四种 sweep / 方向分情况讨论，容易写错；
 *       而半径换算到格子上不超过 12 格，256 段的弓高误差约 9e-4 格，
 *       比本测试的容差小两个数量级。</li>
 * </ul>
 *
 * 走查器由第 0 节自校：有几个字形的墨迹范围可以**直接从 `d` 里数出来**
 * （`add_circle` 是半径 400 的圆，`more_horiz` 是三个半径 80 的圆……），
 * 数出来的值必须与走查器算出的一致。走查器写错时第 0 节先炸，
 * 而不是让基线表看起来"过了"。
 *
 * <h2>为什么是"基线表"而不是"统一尺寸"</h2>
 *
 * 一开始想定的是一条统一规则（"短边 ≥14.4、长短边之比 ≤1.30"，也就是上一轮
 * 对自绘字形用过的那套），但**实测下来它不成立**：Material Symbols 里每个字形的
 * 墨迹大小本来就是按形状定的 —— 折线箭头只有 11.15 格长（细长是它的本质）、
 * 竖排三点只有 4 格宽、而云有 22 格宽。硬套一条规则的结果是例外表里要写十几行，
 * 那种"表比规则还长"的规则等于没有规则。
 *
 * 更要紧的是：上一轮"扁扁的"问题的根因不是某个字号，而是**自绘形状与库里那套不同源**。
 * 现在整集只有一个来源（上游），比例是设计好的，不该再被我们二次加工。
 * 所以这里守的是"**和当时量到的一样**"，而不是"符合某条我拍脑袋定的尺子"。
 * 基线表里另有三个衍生指标（居中、不越界、不许塌成一点）是真正有意义的硬约束。
 */
public final class MaterialSymbolGeometryTest {

    private static final String ICONS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiMaterialIcons.kt";

    /** 上游 svg 是 960 见方，Compose 视口是 24：比 40。 */
    private static final double SVG_TO_GRID = 40.0;

    /** 上游 y 轴向上、值域 [-960, 0]；Compose 是 [0, 24]。 */
    private static final double GRID_VIEWPORT = 24.0;

    /** 基线容差。走查器是精确算法，容差只为浮点误差与上游数据改到小数点后两位留位。 */
    private static final double TOLERANCE = 0.05;

    /**
     * 每个字形都必须**居中**在这个范围内。
     *
     * 1.5 格的余量不是随手给的：Material Symbols 是照着"live area 20×20 居中"画的，
     * 实测最大的偏移是 `Extension`（19.5 见方但中心在 12.75 / 11.25）——
     * 也就是 0.75 格。这条卡的是**视口换算写错**（少写 translationY 时整幅图会跳到
     * 上半格甚至负半格），那种错会让所有字形一起偏 12 格，一眼就能拦住。
     */
    private static final double CENTER_TOLERANCE = 1.5;

    /** 长边下限：卡"整个字形塌成一点"（例如 group 的 scale 写成了 0.0025）。 */
    private static final double LONG_MIN = 10.0;

    /**
     * 基线表：`{字形名, minX, minY, maxX, maxY}`（24 格视口，y 轴向下）。
     *
     * 值由本测试的走查器实测得到。**新增字形必须同时加一行**（少一行会失败），
     * 数值对不上也会失败 —— 那时候的正确做法是先问"这次改动本来就会改到形状吗"：
     * 是，就有意识地更新这一行；不是，就是改坏了。
     */
    private static final String[][] BASELINE = {
            {"Add", "5.00", "5.00", "19.00", "19.00"},
            {"AddCircle", "2.00", "2.00", "22.00", "22.00"},
            {"ArrowBack", "4.43", "4.43", "20.00", "19.59"},
            {"Build", "3.00", "3.00", "20.38", "20.38"},
            {"ChatBubble", "2.00", "2.00", "22.00", "20.61"},
            {"Check", "4.26", "6.38", "19.73", "17.60"},
            {"CheckCircle", "2.00", "2.00", "22.00", "22.00"},
            {"ChevronRight", "8.43", "6.43", "14.98", "17.58"},
            {"Circle", "2.00", "2.00", "22.00", "22.00"},
            {"Close", "5.43", "5.43", "18.58", "18.58"},
            {"Cloud", "1.00", "4.00", "23.00", "20.00"},
            {"Code", "2.43", "6.40", "21.58", "17.59"},
            {"Compress", "4.00", "1.00", "20.00", "22.00"},
            {"Contrast", "2.00", "2.00", "22.00", "22.00"},
            {"Delete", "4.00", "3.00", "20.00", "21.00"},
            {"DeployedCode", "3.00", "2.00", "21.00", "22.00"},
            {"Description", "4.00", "2.00", "20.00", "22.00"},
            {"Difference", "2.00", "1.00", "21.00", "23.00"},
            {"DriveFileMove", "2.00", "4.00", "22.00", "20.00"},
            {"Edit", "3.00", "3.00", "21.00", "21.00"},
            {"Error", "2.00", "2.00", "22.00", "22.00"},
            {"ExpandMore", "6.43", "8.40", "17.58", "14.95"},
            {"Extension", "3.00", "1.50", "22.50", "21.00"},
            {"Folder", "2.00", "4.00", "22.00", "20.00"},
            {"FolderOpen", "2.00", "4.00", "22.81", "20.00"},
            {"Help", "2.00", "2.00", "22.00", "22.00"},
            {"History", "3.00", "3.00", "21.00", "21.00"},
            {"Home", "4.00", "3.50", "20.00", "21.00"},
            {"Hub", "0.00", "0.00", "24.00", "23.00"},
            {"Image", "3.00", "3.00", "21.00", "21.00"},
            {"Info", "2.00", "2.00", "22.00", "22.00"},
            {"InkEraser", "2.04", "3.00", "22.00", "20.00"},
            {"Keyboard", "2.00", "5.00", "22.00", "19.00"},
            {"Layers", "3.64", "2.52", "20.36", "20.53"},
            {"Link", "2.00", "7.00", "22.00", "17.00"},
            {"List", "3.00", "7.00", "21.00", "17.00"},
            {"Lock", "4.00", "1.00", "20.00", "22.00"},
            {"Menu", "3.00", "6.00", "21.00", "18.00"},
            {"MoreHoriz", "4.00", "10.00", "20.00", "14.00"},
            {"MoreVert", "10.00", "4.00", "14.00", "20.00"},
            {"Person", "4.00", "4.00", "20.00", "20.00"},
            {"RadioButtonUnchecked", "2.00", "2.00", "22.00", "22.00"},
            {"Refresh", "4.00", "4.00", "20.00", "20.00"},
            {"Search", "3.00", "3.00", "20.58", "20.58"},
            {"Send", "3.00", "4.49", "20.43", "19.51"},
            {"Settings", "2.48", "2.00", "21.52", "22.00"},
            {"Stop", "6.00", "6.00", "18.00", "18.00"},
            {"Terminal", "2.00", "4.00", "22.00", "20.00"},
            {"Timer", "3.00", "1.00", "21.00", "22.00"},
            {"Tune", "3.00", "3.00", "21.00", "21.00"},
    };

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // ------------------------------------------------------------------ 走查器

    /** 一个字形在 24 格视口里的墨迹范围。 */
    private static final class Box {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        void add(double x, double y) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        double width() { return maxX - minX; }
        double height() { return maxY - minY; }
        double longSide() { return Math.max(width(), height()); }
        double centerX() { return (minX + maxX) / 2; }
        double centerY() { return (minY + maxY) / 2; }
    }

    /** 走一遍上游 SVG 的路径数据，返回**已换算到 24 格视口**的墨迹范围。 */
    private static Box inkBox(String d) {
        Walker w = new Walker(d);
        w.run();
        Box box = new Box();
        box.minX = w.minX / SVG_TO_GRID;
        box.maxX = w.maxX / SVG_TO_GRID;
        box.minY = w.minY / SVG_TO_GRID + GRID_VIEWPORT;
        box.maxY = w.maxY / SVG_TO_GRID + GRID_VIEWPORT;
        return box;
    }

    /** SVG 路径走查。见类注释里「走查器是怎么写的」。 */
    private static final class Walker {
        private final String s;
        private int i;
        private double x, y;           // 当前点
        private double startX, startY; // 子路径起点（Z 用）
        private double cx, cy;         // 上一个三次贝塞尔的控制点（S 用）
        private double qx, qy;         // 上一个二次贝塞尔的控制点（T 用）
        private char lastCurve = ' ';  // 'C' / 'Q' / 其它
        private char prev = ' ';
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        Walker(String d) { this.s = d; }

        private void mark(double px, double py) {
            if (px < minX) minX = px;
            if (px > maxX) maxX = px;
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }

        private void skipSeparators() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        private double num() {
            skipSeparators();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i == start) {
                throw new AssertionError("路径数据里要数字，却读到：\""
                        + s.substring(start) + "\"（整段：" + s + "）");
            }
            return Double.parseDouble(s.substring(start, i));
        }

        /**
         * 圆弧的两个标志位。
         *
         * SVG 允许它们**与后面的数字连写**（`a1 1 0 011 1`），所以不能按普通数字读，
         * 只能读单个 `0`/`1` 字符。这条写错的话，整个字形的包围盒会静默跑偏。
         */
        private boolean flag() {
            skipSeparators();
            char c = s.charAt(i);
            if (c != '0' && c != '1') {
                throw new AssertionError("圆弧标志位只能是 0 或 1，读到 '" + c + "'（整段：" + s + "）");
            }
            i++;
            return c == '1';
        }

        void run() {
            while (true) {
                skipSeparators();
                if (i >= s.length()) break;
                char c = s.charAt(i);
                char cmd;
                if (Character.isLetter(c)) {
                    cmd = c;
                    i++;
                } else {
                    // 隐式重复：M 之后的坐标对按 L 处理，其余重复上一个命令。
                    if (prev == ' ') throw new AssertionError("路径以坐标开头：" + s);
                    cmd = prev == 'M' ? 'L' : prev == 'm' ? 'l' : prev;
                }

                boolean rel = Character.isLowerCase(cmd);
                char upper = Character.toUpperCase(cmd);
                switch (upper) {
                    case 'M': {
                        double nx = num(), ny = num();
                        if (rel) { nx += x; ny += y; }
                        x = nx; y = ny;
                        startX = x; startY = y;
                        mark(x, y);
                        lastCurve = ' ';
                        break;
                    }
                    case 'L': {
                        double nx = num(), ny = num();
                        if (rel) { nx += x; ny += y; }
                        x = nx; y = ny;
                        mark(x, y);
                        lastCurve = ' ';
                        break;
                    }
                    case 'H': {
                        double nx = num();
                        if (rel) nx += x;
                        x = nx;
                        mark(x, y);
                        lastCurve = ' ';
                        break;
                    }
                    case 'V': {
                        double ny = num();
                        if (rel) ny += y;
                        y = ny;
                        mark(x, y);
                        lastCurve = ' ';
                        break;
                    }
                    case 'C': {
                        double x1 = num(), y1 = num(), x2 = num(), y2 = num(), nx = num(), ny = num();
                        if (rel) { x1 += x; y1 += y; x2 += x; y2 += y; nx += x; ny += y; }
                        cubic(x, y, x1, y1, x2, y2, nx, ny);
                        cx = x2; cy = y2;
                        x = nx; y = ny;
                        lastCurve = 'C';
                        break;
                    }
                    case 'S': {
                        double x1, y1;
                        if (lastCurve == 'C') { x1 = 2 * x - cx; y1 = 2 * y - cy; } else { x1 = x; y1 = y; }
                        double x2 = num(), y2 = num(), nx = num(), ny = num();
                        if (rel) { x2 += x; y2 += y; nx += x; ny += y; }
                        cubic(x, y, x1, y1, x2, y2, nx, ny);
                        cx = x2; cy = y2;
                        x = nx; y = ny;
                        lastCurve = 'C';
                        break;
                    }
                    case 'Q': {
                        double x1 = num(), y1 = num(), nx = num(), ny = num();
                        if (rel) { x1 += x; y1 += y; nx += x; ny += y; }
                        quad(x, y, x1, y1, nx, ny);
                        qx = x1; qy = y1;
                        x = nx; y = ny;
                        lastCurve = 'Q';
                        break;
                    }
                    case 'T': {
                        double x1, y1;
                        if (lastCurve == 'Q') { x1 = 2 * x - qx; y1 = 2 * y - qy; } else { x1 = x; y1 = y; }
                        double nx = num(), ny = num();
                        if (rel) { nx += x; ny += y; }
                        quad(x, y, x1, y1, nx, ny);
                        qx = x1; qy = y1;
                        x = nx; y = ny;
                        lastCurve = 'Q';
                        break;
                    }
                    case 'A': {
                        double rx = num(), ry = num(), rot = num();
                        boolean largeArc = flag();
                        boolean sweep = flag();
                        double nx = num(), ny = num();
                        if (rel) { nx += x; ny += y; }
                        arc(x, y, rx, ry, rot, largeArc, sweep, nx, ny);
                        x = nx; y = ny;
                        lastCurve = ' ';
                        break;
                    }
                    case 'Z': {
                        x = startX;
                        y = startY;
                        lastCurve = ' ';
                        break;
                    }
                    default:
                        throw new AssertionError("不认识的 SVG 命令 '" + cmd + "'（路径数据：" + s + "）");
                }
                prev = upper == 'Z' ? ' ' : cmd;
            }
        }

        /** 三次贝塞尔：解析求两个方向上的极值点（导数为零的 t）。 */
        private void cubic(double x0, double y0, double x1, double y1,
                           double x2, double y2, double x3, double y3) {
            mark(x0, y0);
            mark(x3, y3);
            for (double t : cubicExtrema(x0, x1, x2, x3)) {
                mark(bez(x0, x1, x2, x3, t), bez(y0, y1, y2, y3, t));
            }
            for (double t : cubicExtrema(y0, y1, y2, y3)) {
                mark(bez(x0, x1, x2, x3, t), bez(y0, y1, y2, y3, t));
            }
        }

        private void quad(double x0, double y0, double x1, double y1, double x2, double y2) {
            mark(x0, y0);
            mark(x2, y2);
            for (double t : quadExtrema(x0, x1, x2)) {
                mark(qbez(x0, x1, x2, t), qbez(y0, y1, y2, t));
            }
            for (double t : quadExtrema(y0, y1, y2)) {
                mark(qbez(x0, x1, x2, t), qbez(y0, y1, y2, t));
            }
        }

        private static double bez(double p0, double p1, double p2, double p3, double t) {
            double u = 1 - t;
            return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
        }

        private static double qbez(double p0, double p1, double p2, double t) {
            double u = 1 - t;
            return u * u * p0 + 2 * u * t * p1 + t * t * p2;
        }

        /** B'(t) = 3[(-p0+3p1-3p2+p3)t² + 2(p0-2p1+p2)t + (p1-p0)] = 0 的根。 */
        private static List<Double> cubicExtrema(double p0, double p1, double p2, double p3) {
            double a = -p0 + 3 * p1 - 3 * p2 + p3;
            double b = 2 * (p0 - 2 * p1 + p2);
            double c = p1 - p0;
            List<Double> roots = new ArrayList<>();
            if (Math.abs(a) < 1e-12) {
                if (Math.abs(b) > 1e-12) roots.add(-c / b);
            } else {
                double disc = b * b - 4 * a * c;
                if (disc >= 0) {
                    double sq = Math.sqrt(disc);
                    roots.add((-b + sq) / (2 * a));
                    roots.add((-b - sq) / (2 * a));
                }
            }
            List<Double> out = new ArrayList<>();
            for (double t : roots) if (t > 1e-9 && t < 1 - 1e-9) out.add(t);
            return out;
        }

        /** Q'(t) = 2[(1-t)(p1-p0) + t(p2-p1)] = 0 的根。 */
        private static List<Double> quadExtrema(double p0, double p1, double p2) {
            List<Double> out = new ArrayList<>();
            double denom = p0 - 2 * p1 + p2;
            if (Math.abs(denom) > 1e-12) {
                double t = (p0 - p1) / denom;
                if (t > 1e-9 && t < 1 - 1e-9) out.add(t);
            }
            return out;
        }

        /** 圆弧：端点参数化 → 圆心参数化，再按角度密采样（见类注释）。 */
        private void arc(double x0, double y0, double rx, double ry, double rotDeg,
                         boolean largeArc, boolean sweep, double x1, double y1) {
            mark(x0, y0);
            mark(x1, y1);
            if (rx == 0 || ry == 0) return;                 // 退化成直线，端点已记
            double rxAbs = Math.abs(rx), ryAbs = Math.abs(ry);
            double phi = Math.toRadians(rotDeg % 360);
            double cosPhi = Math.cos(phi), sinPhi = Math.sin(phi);

            double dx2 = (x0 - x1) / 2, dy2 = (y0 - y1) / 2;
            double x1p = cosPhi * dx2 + sinPhi * dy2;
            double y1p = -sinPhi * dx2 + cosPhi * dy2;

            double lambda = (x1p * x1p) / (rxAbs * rxAbs) + (y1p * y1p) / (ryAbs * ryAbs);
            if (lambda > 1) {
                double k = Math.sqrt(lambda);
                rxAbs *= k;
                ryAbs *= k;
            }

            double num = rxAbs * rxAbs * ryAbs * ryAbs
                    - rxAbs * rxAbs * y1p * y1p
                    - ryAbs * ryAbs * x1p * x1p;
            double den = rxAbs * rxAbs * y1p * y1p + ryAbs * ryAbs * x1p * x1p;
            double coef = Math.sqrt(Math.max(0, num / den));
            if (largeArc == sweep) coef = -coef;
            double cxp = coef * rxAbs * y1p / ryAbs;
            double cyp = -coef * ryAbs * x1p / rxAbs;
            double cxc = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2;
            double cyc = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2;

            double theta1 = angle(1, 0, (x1p - cxp) / rxAbs, (y1p - cyp) / ryAbs);
            double delta = angle((x1p - cxp) / rxAbs, (y1p - cyp) / ryAbs,
                    (-x1p - cxp) / rxAbs, (-y1p - cyp) / ryAbs);
            if (!sweep && delta > 0) delta -= 2 * Math.PI;
            if (sweep && delta < 0) delta += 2 * Math.PI;

            int steps = 256;
            for (int k = 0; k <= steps; k++) {
                double theta = theta1 + delta * k / steps;
                mark(cosPhi * rxAbs * Math.cos(theta) - sinPhi * ryAbs * Math.sin(theta) + cxc,
                        sinPhi * rxAbs * Math.cos(theta) + cosPhi * ryAbs * Math.sin(theta) + cyc);
            }
        }

        private static double angle(double ux, double uy, double vx, double vy) {
            double dot = ux * vx + uy * vy;
            double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
            double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
            return (ux * vy - uy * vx < 0) ? -a : a;
        }
    }

    // ------------------------------------------------------------------ 主流程

    private static final Pattern GLYPH = Pattern.compile(
            "val\\s+([A-Za-z0-9_]+)\\s*:\\s*ImageVector\\s+by\\s+lazy\\s*\\{\\s*"
                    + "material\\(\"([^\"]+)\",\\s*\"([^\"]*)\"\\)\\s*\\}");

    private static Map<String, String> allPaths(String source) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = GLYPH.matcher(source);
        while (m.find()) {
            require(m.group(1).equals(m.group(2)),
                    "字形的 Kotlin 名与传给 material() 的名字不一致：" + m.group(1) + " / " + m.group(2));
            out.put(m.group(1), m.group(3));
        }
        return out;
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static String fmt(Box b) {
        return String.format("x∈[%.2f,%.2f] y∈[%.2f,%.2f]（%.2f × %.2f）",
                b.minX, b.maxX, b.minY, b.maxY, b.width(), b.height());
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String source = new String(Files.readAllBytes(root.resolve(ICONS)), StandardCharsets.UTF_8);

        Map<String, String> paths = allPaths(source);
        require(paths.size() >= 50,
                ICONS + " 里只解析到 " + paths.size() + " 个字形：生成那一侧的写法变了，"
                        + "这条守卫会静默失效");

        Map<String, Box> boxes = new TreeMap<>();
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            boxes.put(entry.getKey(), inkBox(entry.getValue()));
        }

        // 先把实测表打出来：断言失败时这张表就是排错的第一手材料，
        // 平时它也是"这个字形到底多大"的唯一权威答案（人工核对时看它）。
        System.out.println("字形墨迹表（24 格视口）：");
        for (Map.Entry<String, Box> entry : boxes.entrySet()) {
            Box b = entry.getValue();
            System.out.printf("  %-22s [%5.2f..%5.2f] × [%5.2f..%5.2f]  %5.2f × %5.2f%n",
                    entry.getKey(), b.minX, b.maxX, b.minY, b.maxY, b.width(), b.height());
        }

        // ---- 0. 走查器自校 ---------------------------------------------------
        //
        // 这三条的值可以**直接从上游的 d 里数出来**，不需要跑程序：
        //   add_circle: 半径 400 的圆 + 一个加号，圆心 (480,-480) → 格子上正好 [2,22]×[2,22]
        //   more_horiz: 三个半径 80 的圆，圆心 x 240/480/720、y 都是 -480
        //               → 格子上 x∈[4,20]、y∈[10,14]
        //   terminal  : 外框 x 80..880、y -800..-160          → 格子上 [2,22]×[4,20]
        //   settings  : 齿轮的上下齿顶到画布边缘（y -880..-80 → 格子上 [2,22]），
        //               而左右极值是**圆角后的齿**，落在 99.2 / 860.8 上（不是控制点 80 / 880）
        //               → 格子上 x∈[2.48,21.52]。这一条正好也验证了走查器在处理
        //               二次贝塞尔极值时没有偷懒只取控制点。
        Box addCircle = boxes.get("AddCircle");
        require(near(addCircle.minX, 2, 0.02) && near(addCircle.maxX, 22, 0.02)
                        && near(addCircle.minY, 2, 0.02) && near(addCircle.maxY, 22, 0.02),
                "走查器自校失败：add_circle 是半径 400 的圆，墨迹应当正好 [2,22]×[2,22]，实算 "
                        + fmt(addCircle) + " —— 走查器算错了，下面的基线表全部不可信");
        Box moreHoriz = boxes.get("MoreHoriz");
        require(near(moreHoriz.minX, 4, 0.02) && near(moreHoriz.maxX, 20, 0.02)
                        && near(moreHoriz.minY, 10, 0.02) && near(moreHoriz.maxY, 14, 0.02),
                "走查器自校失败：more_horiz 是三个半径 80 的圆，墨迹应当正好 "
                        + "[4,20]×[10,14]，实算 " + fmt(moreHoriz));
        Box terminal = boxes.get("Terminal");
        require(near(terminal.minY, 4, 0.02) && near(terminal.maxY, 20, 0.02),
                "走查器自校失败：terminal 的外框 y 从 -800 到 -160，墨迹应当 y∈[4,20]，"
                        + "实算 " + fmt(terminal));
        Box settings = boxes.get("Settings");
        require(near(settings.minY, 2, 0.02) && near(settings.maxY, 22, 0.02)
                        && near(settings.minX, 2.48, 0.02) && near(settings.maxX, 21.52, 0.02),
                "走查器自校失败：settings 的 y 应当 [2,22]、x 应当 [2.48,21.52]"
                        + "（左右极值是圆角后的齿，不在控制点上），实算 " + fmt(settings));

        // ---- 1. 基线表与实际字形必须一一对应 ---------------------------------
        Map<String, String[]> baseline = new LinkedHashMap<>();
        for (String[] row : BASELINE) {
            require(row.length == 5, "基线表的行必须是 {名字, minX, minY, maxX, maxY}：" + row[0]);
            require(baseline.put(row[0], row) == null, "基线表里有重复条目：" + row[0]);
        }
        List<String> missing = new ArrayList<>(boxes.keySet());
        missing.removeAll(baseline.keySet());
        List<String> extra = new ArrayList<>(baseline.keySet());
        extra.removeAll(boxes.keySet());
        require(missing.isEmpty() && extra.isEmpty(),
                "基线表与 " + ICONS + " 的字形集合不一致：\n  少了基线：" + missing
                        + "\n  多了基线：" + extra
                        + "\n（新增/删除字形时必须同步改这张表 —— 它是「改动是否影响形状」的唯一闸门）");

        // ---- 2. 逐个比对：基线、越界、居中、不许塌 ---------------------------
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Box> entry : boxes.entrySet()) {
            String name = entry.getKey();
            Box box = entry.getValue();
            String[] base = baseline.get(name);

            double[] expected = {
                    Double.parseDouble(base[1]), Double.parseDouble(base[2]),
                    Double.parseDouble(base[3]), Double.parseDouble(base[4]),
            };
            double[] actual = {box.minX, box.minY, box.maxX, box.maxY};
            String[] axis = {"minX", "minY", "maxX", "maxY"};
            for (int k = 0; k < 4; k++) {
                if (!near(actual[k], expected[k], TOLERANCE)) {
                    problems.add("「" + name + "」的 " + axis[k] + " 从基线的 "
                            + expected[k] + " 变成了 " + String.format("%.2f", actual[k])
                            + " —— 形状/视口换算被改动了。确实要改就同步更新基线表那一行");
                }
            }

            if (box.minX < -0.01 || box.maxX > 24.01 || box.minY < -0.01 || box.maxY > 24.01) {
                problems.add("「" + name + "」的墨迹出了 24 格视口：" + fmt(box)
                        + " —— 边缘会被裁掉");
            }
            if (!near(box.centerX(), 12, CENTER_TOLERANCE)
                    || !near(box.centerY(), 12, CENTER_TOLERANCE)) {
                problems.add("「" + name + "」在视口里不居中：中心是 ("
                        + String.format("%.2f", box.centerX()) + ", "
                        + String.format("%.2f", box.centerY()) + ")，应当接近 (12, 12)："
                        + fmt(box) + " —— 这是视口换算写错的典型症状（例如漏了 translation）");
            }
            if (box.longSide() < LONG_MIN) {
                problems.add("「" + name + "」两个方向都不足 " + LONG_MIN + " 格：" + fmt(box)
                        + " —— 整个字形塌成一点了，多半是 group 的 scale 写错");
            }
        }
        require(problems.isEmpty(), "有 " + problems.size() + " 处几何不合格：\n  "
                + String.join("\n  ", problems));

        System.out.println("MaterialSymbolGeometryTest PASS（" + boxes.size()
                + " 个字形逐个比对基线 · 越界/居中/塌陷三项硬约束全部通过）");
    }
}
