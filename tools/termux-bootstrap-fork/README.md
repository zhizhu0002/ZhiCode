# 自建前缀正确的 bootstrap（fork termux-packages）

**目的**：产出前缀为 `/data/data/com.zhizhu.code/files/usr` 的 `bootstrap-aarch64.zip`，
从而让本工程**彻底删除**前缀改写逻辑（等长替换、补丁脚本、`LD_LIBRARY_PATH` 硬依赖全都不再需要）。

这一切都来自官方设计，不是 hack。`scripts/build-bootstraps.sh` 自己的头部注释：

> A script to build bootstrap archives for the termux-app from local package sources
> instead of debs published in apt repo like done by generate-bootstrap.sh.
> **It allows bootstrap archives to be easily built for (forked) termux apps** …

`scripts/properties.sh` 把包名做成了**受校验的一等变量**（「Ideally package name should be
`<= 21` characters」）。`com.zhizhu.code` = 15 字符，在范围内。

---

## 一、前置条件

- **x86_64 Linux 主机 + Docker + sudo**（本机 Android/Termux 做不到，这是唯一门槛）
- 磁盘：仓库 + 构建缓存约 **10~20 GB**
- 时间：首次构建约 **1~3 小时**（要编 ~40 个包及其依赖）

---

## 二、改动清单（只需 3 处）

路径全自动派生，无需手改：

```
TERMUX_APP__PACKAGE_NAME = "com.zhizhu.code"
  └─> TERMUX_APP__DATA_DIR = /data/data/com.zhizhu.code
        └─> TERMUX__ROOTFS  = /data/data/com.zhizhu.code/files
              ├─> TERMUX__HOME   = .../files/home
              └─> TERMUX__PREFIX = .../files/usr          ← bootstrap 的前缀
```

`scripts/properties.sh` 中**实际赋值**（不是注释）的行：

| # | 行 | 变量 | 原值 | 改为 | 作用 |
| --- | --- | --- | --- | --- | --- |
| 1 | 467 | `TERMUX_APP__PACKAGE_NAME` | `com.termux` | `com.zhizhu.code` | **决定前缀**（经 DATA_DIR→ROOTFS→PREFIX 派生） |
| 2 | 1851 | `TERMUX_APP__NAMESPACE` | `com.termux` | `com.zhizhu.code` | 组件类名的前缀，须与 fork 应用的 Java 命名空间一致 |
| 3 | 374 | `TERMUX__NAME` | `Termux` | `蜘蛛` | 只进二阶段脚本的 3 处提示文本 |

第 2 项很重要：`..._ACTIVITY__CLASS_NAME` / `..._SERVICE__CLASS_NAME` / `..._RECEIVER__CLASS_NAME`
都是从它派生的，必须和应用 Java 包路径一致。

### ⚠️ 不要改 `TERMUX_REPO__*`（这 7 行）

`TERMUX_REPO_APP__PACKAGE_NAME`、`TERMUX_REPO__CORE_DIR`、`TERMUX_REPO__PREFIX` 等。它们是**硬编码**的
（官方注释里带 FIXME），看起来像「漏改」，但官方明确要求保持原样：

> If a custom repo is not being hosted … then **DO NOT change these values**.

原因：这些值只被 `build-package.sh` 的 `-i/-I` 使用（从 apt 源下载**预编译**依赖），而
`build-bootstraps.sh` **根本不传** `-i/-I`。所以对 bootstrap 构建零影响；若改了，只会多打一行警告。
**这一点很关键**：依赖必须**本地编译** —— 那些 deb 是按 `com.termux` 前缀编译的，混进来会污染
bootstrap（这也正是验收要检查「旧前缀必须为 0」的原因）。

> 其他残留（`TERMUX_API_APP__PACKAGE_NAME`、`TERMUX_AM_APP__NAMESPACE`、`CGCT_DEFAULT_PREFIX`）是
> 插件/glibc 专用，会在二进制里留下**裸字符串 `com.termux`**（命名空间，不是路径）。
> **验收标准是「不存在 `/data/data/com.termux` 路径」，不是「不存在 `com.termux` 字样」。**

---

## 三、一条命令完成改动

```bash
cd /path/to/your/termux-packages-fork
/path/to/this/dir/apply-prefix-changes.sh com.zhizhu.code com.zhizhu.code 蜘蛛
```

脚本是**幂等**的：重复运行不会叠加改动；每次改动都留 `.bak`。

---

## 四、构建

改了包名后**必须清缓存**，否则会复用按旧前缀编译的产物：

```bash
./scripts/run-docker.sh ./clean.sh                       # 1) 清缓存（改了包名后必须做一次）
./scripts/run-docker.sh ./scripts/build-bootstraps.sh \
    --architectures aarch64 2>&1 | tee build.log         # 2) 只构建 aarch64
```

- Docker 镜像：`ghcr.io/termux/package-builder`（可用 `TERMUX_BUILDER_IMAGE_NAME` 覆盖）
- 想加包：`--add openssh,git`
- **产物**：仓库根目录 `bootstrap-aarch64.zip`

归档格式（与本工程 `RuntimeInstaller` 的 `SYMLINKS_ENTRY` 解析完全一致）：内容 = `$TERMUX_PREFIX`
下的文件；**符号链接不直接存放**，而是记录到 `SYMLINKS.txt`，每行 `目标←链接`。

---

## 五、验收（交给本工程前先自检）

```bash
javac -d /tmp VerifyBootstrap.java
java -cp /tmp VerifyBootstrap bootstrap-aarch64.zip com.zhizhu.code
```

| 检查项 | 期望 |
| --- | --- |
| `/data/data/com.termux` 出现次数 | **0** |
| 新前缀文件数 | > 300 |
| ELF 数 | ~338 |
| `SYMLINKS.txt` | 存在且行数 > 0 |

退出码 0 = 通过。

---

## 六、产物交回后本工程的改动（届时执行）

前缀已正确，因此可以**净删**：

1. `RuntimeInstaller.kt`：删掉 `extractBootstrap` 里的前缀改写分支与 `replaceAll`，直接原样写出
2. 删 `assets/zhicode/` 下 6 个补丁脚本（`apt-pre-install.sh`、`dpkg-wrapper.sh`、
   `99zhicode-prefix-rewrite.conf` 等）
3. `TermuxShellExecutor.java` / `TermuxTerminalPane.java`：去掉 `LD_LIBRARY_PATH` 硬依赖
4. `app/build.gradle`：删掉那段关于「8 字符限制 / RUNPATH」的说明
5. `TermuxConstants` 兜底值 + `applicationId` 必须与构建时用的包名**逐字符一致**

第 5 项是唯一的强约束：**bootstrap 是用哪个包名构建的，APK 就必须用哪个包名**。好处是它终于成了
一个**单一事实来源**（`applicationId`），而不是散落的改写规则。

---

## 七、必须避开的四个坑（均实测踩到过）

### 坑 3：`util-linux` 在 NDK r30 + api-24 下编译失败

```
nsenter.c:189:27: error: variable has incomplete type 'struct file_handle'
nsenter.c:225:6:  error: call to undeclared function 'name_to_handle_at'
Failed to build package 'apt' for arch 'aarch64'
```

根因不是缺守卫（v2.42.1 的 `nsenter.c:60` 已有条件编译），而是两个探测**双双误判为 yes**：

| 探测 | 位置 | 为何误判 |
| --- | --- | --- |
| `HAVE_STRUCT_NSFS_FILE_HANDLE` | `configure.ac:1055` | 只验证 `<linux/nsfs.h>`（**NDK r30 起才有**），不验证 glibc 的 `struct file_handle`（bionic 需 API ≥ 26），而本构建用 `android-r30-api-24` sysroot |
| `HAVE_PIDFD_OPEN` | `UL_CHECK_SYSCALL([pidfd_open])` | 只查 **syscall 号**，aarch64 恒有；而缺的是 libc 包装 |

**为什么官方 CI 不炸**：官方 bootstrap 由 `generate-bootstraps.sh` 从 apt 源拉**预编译 deb**，
`packages.yml` 又只重建**变更过**的包，util-linux 一直没被重编，所以这个潜在问题从未暴露。
我们走源码构建才碰到。**证据**：官方已发布 bootstrap 里的 `bin/nsenter` 对
`name_to_handle_at` / `open_by_handle_at` / `nsfs` **零引用**（`grep -c` 全为 0）。

**修复**：在 `packages/util-linux/build.sh` 的 `TERMUX_PKG_EXTRA_CONFIGURE_ARGS` 里加一行
（与该文件既有的 `ac_cv_func_*` 覆盖风格一致）：`ac_cv_type_struct_nsfs_file_handle=no`。
效果等同编译掉该特性，即恢复官方产物的实际形态。

### 坑 1：**不要传 `-f`** —— 会触发 `rm -f /*`

官方帮助说改包名后要 `clean.sh` **或** `-f`。**只能走 `clean.sh`。** 因为 `-f` 会设
`FORCE_BUILD_PACKAGES=1`，然后命中上游一个空变量 bug：

```bash
# scripts/build-bootstraps.sh
399   if [[ $FORCE_BUILD_PACKAGES == "1" ]]; then
400       rm -f "$TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH"/*     # ← 该变量从未被赋值
401       rm -f "$TERMUX_BUILT_DEBS_DIRECTORY"/*
```

`TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH` 在 `scripts/` 下**只出现在这一行**。空展开后就是
`rm -f /*`，而 `-f` **不抑制** `rm: cannot remove '/bin': Permission denied` 这类错误 ——
`set -e` 之下第一条 `rm` 返回非 0 就直接退出（这是实测失败日志）。

### 坑 2：必须显式 `export TERMUX_PACKAGE_MANAGER=apt`

`build-bootstraps.sh` 只 source 两个文件（`scripts/properties.sh`、
`scripts/build/termux_step_handle_buildarch.sh`），而 `TERMUX_PACKAGE_MANAGER` **两者都没定义**
（`generate-bootstraps.sh:33` 才有；`termux_step_setup_variables.sh` 里那份只在 `build-package.sh`
的子进程里生效，救不了父进程的 sed 替换）。

脚本是 `set -e` **而非 `-u`**，所以不报错，只会把空值替换进二阶段脚本：

```bash
export TERMUX_PACKAGE_MANAGER=""          # ← 坏掉
```

后果：`[ "${TERMUX_PACKAGE_MANAGER}" = "apt" ]` 为假，**apt 的包注册逻辑整段被跳过**。
本工程要的就是 `apt`（与官方 `apt-android-7` 变体一致）。对照证据：官方构建的现存 bootstrap
里该行是 `export TERMUX_PACKAGE_MANAGER="apt"`（正确）。

**注入方式必须正确**：`docker exec` **不继承宿主环境**，所以不能只在宿主 `export`。
唯一可行的是用它预留的注入口：

```bash
export TERMUX_DOCKER_EXEC_EXTRA_ARGS="--env TERMUX_PACKAGE_MANAGER=apt"
```

workflow 的验收步骤因此加了这两条断言：

```bash
grep -q '^export TERMUX_PACKAGE_MANAGER="apt"$' "$stage2"   # 必须为 apt
grep -q '@[A-Z_]\{3,\}@' "$stage2" && fail                  # 不得残留 @占位符@
```

其余 `@占位符@`（`@TERMUX_PREFIX@`、`@TERMUX_APP__NAME@`、`@TERMUX_ENV__S_TERMUX@`、
`@TERMUX_BOOTSTRAP__…@`、`@TERMUX_PACKAGE_ARCH@`）已逐个核对，均由 `properties.sh` 或
`build-bootstraps.sh` 的局部变量提供。**只有 `@TERMUX_PACKAGE_MANAGER@` 无处定义，必须手动 export。**

### 坑 4：AppArmor 只允许容器写 `output/`（上次构建「编完了却拿不到 zip」的真因）

**现象**：日志里编译、提取、打包全部成功，最后只剩一行报错 + 工作区没有 zip：

```
  adding: bin/... (deflated 71%)          ← zip 其实已经生成好了
mv: cannot create regular file '/home/builder/termux-packages/bootstrap-aarch64.zip': Permission denied
[*] Finished successfully (aarch64).      ← 居然是「成功」
...
##[error]Process completed with exit code 2.
```

**根因**：`scripts/run-docker.sh` 在 `docker exec` 之前**一定会**加载受限 AppArmor 模板
`scripts/profile-restricted.apparmor`：

```
deny  /home/builder/termux-packages/[^o]** wlk,     ← 仓库里除 output/ 外一律禁写
allow /home/builder/termux-packages/output/** rw,   ← 只有 output/ 可写
```

而 `create_bootstrap_archive()` 把 zip 落在**仓库根**（`mv -f …… "$TERMUX_PACKAGES_DIRECTORY/"`）
→ 命中 `deny`，`EACCES`。

**不是 uid 问题**：容器里的 `builder` 就是 `1001`，宿主 runner 也是 `1001`，所以 `run-docker.sh`
的 `__change_builder_uid_gid` **根本不执行**。AppArmor 是 LSM，在 DAC 之后拦截，**root 也照样被拒**。

**为什么炸得这么晚、还报「成功」**：`create_bootstrap_archive "$TERMUX_ARCH" || return $?` 里
`errexit` 被 `||` 抑制，`mv` 失败后函数继续往下跑并打印成功；随后 EXIT trap
**`rm -rf "$BOOTSTRAP_TMPDIR"`** —— zip 就在里面，连尸体都不剩。

**修复**（已写进 `build-bootstrap.yml`，宿主侧打补丁）：容器只能写 `output/`，那就写 `output/`，
由**宿主**（不受该 AppArmor 约束）搬回来：

```yaml
# 1) 构建前改掉 mv 目标（唯一一处；用 grep -cF 先确认唯一命中，避免误伤
#    TERMUX_BUILT_DEBS_DIRECTORY="$TERMUX_PACKAGES_DIRECTORY/output" 这类不带引号包裹目标的行）
sed -i 's|"\$TERMUX_PACKAGES_DIRECTORY/"|"\$TERMUX_PACKAGES_DIRECTORY/output/"|' scripts/build-bootstraps.sh
# 2) 构建后搬回仓库根 + 自己断言产物存在
mv -f output/bootstrap-aarch64.zip ./bootstrap-aarch64.zip
[ -f bootstrap-aarch64.zip ] || { ls -l output/*.zip; exit 1; }
```

**教训**：这个坑难定位，是因为它**不符合 `run-docker.sh` 里两条「正常」解释** —— 既不是
SELinux/btrfs（没打印 `Changed builder uid/gid...`），也不是人为 chmod（`ls -la` 看权限完全正常）。
**报 Permission denied 但 uid/gid/权限位都对时，先想 LSM。**

---

## 八、常见误区：fork 了 termux-app

**bootstrap 不在 termux-app 里。** termux-app 是外壳，它在**构建时**从 termux-packages 的
GitHub Release 下载 bootstrap 并做 SHA-256 校验。所以**只 fork termux-app，前缀一个字节都不会变。**

若同时要构建 termux-app（可选），除了 `namespace` / `applicationId` /
`manifestPlaceholders.TERMUX_PACKAGE_NAME` 三处包名，还必须改 `downloadBootstraps` 里那个
**硬编码的期望校验值**，否则你放进 `app/src/main/cpp/bootstrap-aarch64.zip` 的自建 bootstrap 会被
**删掉并替换成官方版**（`afterEvaluate` 里 `javaCompileProvider.dependsOn(downloadBootstraps)`，
所以这个 task 每次编译都会跑，删文件的行为是静默的）。更稳妥的做法是直接绕过
`downloadBootstrap`，把自建 zip 拷进去并跳过该 task。

**本工程需要哪个**：只需要 termux-packages 产出的 zip，以及 `applicationId` 与构建时包名逐字符
一致 —— bootstrap 作为 **assets** 打包，**不需要 termux-app**。

---

## 九、构建之后必须做的后处理（已实测，2026-09-25）

CI 产出的 zip **可用，但偏大**，且有两处上游瑕疵。

### 9.1 为什么偏大：`extract_debs()` 会把每个 deb 都打进去

`extract_debs()` 是 `for deb in *.deb` —— 把 `output/` 里**每一个** deb 都解进归档。而 fork 场景下
依赖下载被禁用（`Ignoring -i option to download dependencies since repo package name
(com.termux) does not equal app package name (com.zhizhu.code)`），于是**所有**依赖都从源码编译，
包括纯构建期依赖（doxygen、python、perl、tcl/tk、X11 全家桶、fontconfig…）也被一起解进去：

| | 包数 | 文件数 | 体积 |
| --- | --- | --- | --- |
| 官方 release | 82 | 3473 | 32.2 MB |
| CI 原始产出（运行 #6） | 159 | 17485 | 122 MB |
| **裁剪后**（本流程） | **83** | **3504** | **31.6 MB** |

官方 82 个包就是**运行时依赖闭包** —— 由 `generate-bootstraps.sh` 按 `Depends:` 递归生成，天然不含
构建期依赖；`build-bootstraps.sh` 面向 fork 走源码编译，才暴露了这个差异。

### 9.2 四道后处理

```bash
# 0) 准备：artifact 里是 bootstrap-aarch64-com.zhizhu.code.zip（外层容器），里面才是内层 zip
unzip -q bootstrap-aarch64-com.zhizhu.code.zip
unzip -q bootstrap-aarch64.zip -d /tmp/bs

# 1) 裁剪成运行时闭包（工具已随本目录提供）
node prune-bootstrap.js /tmp/bs --dry-run              # 先看要删什么
node prune-bootstrap.js /tmp/bs                        # 实删

# 2) 修上游 bug：二阶脚本的 @TERMUX_PACKAGE_ARCH@ 被替换成了空串
#    根因：add_termux_bootstrap_second_stage_files 收 $1，而调用处传的是未定义的
#    $package_arch（循环变量其实叫 TERMUX_ARCH）
sed -i 's|^export TERMUX_PACKAGE_ARCH=""$|export TERMUX_PACKAGE_ARCH="aarch64"|' \
    /tmp/bs/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh

# 3) 修 termux-exec 两个注释里残留的旧前缀（源码构建不会碰它们）
sed -i 's|/data/data/com\.termux|/data/data/com.zhizhu.code|g' \
    /tmp/bs/bin/termux-exec-ld-preload-lib \
    /tmp/bs/include/termux-exec/.../ExecIntercept.h

# 4) 重新打包（Termux 里没有 zip，用 jar）
cd /tmp/bs && jar --create --file /tmp/bootstrap-new.zip --no-manifest -C . .
```

> `jar` 打包**不保留可执行位**，但本工程是安全的：`RuntimeInstaller` 用 `isExecutableContent()`
> **按内容**推断（ELF magic 或 `#!`）。已实测该 bootstrap 的 424 个可执行文件**全部**是 ELF(324)
> 或 `#!` 脚本(100)，"其他" 为 0。若将来某个包引入既非 ELF 也非 `#!` 的可执行文件，这条路会失效。

### 9.3 验收（裁剪后必须全绿）

```bash
cd /tmp/bs
find . -type f | wc -l                                              # ≈3504（官方 3473）
grep -c '^Package: ' var/lib/dpkg/status                            # ≈83（官方 82）
grep -rl --binary-files=text '/data/data/com.termux' . | wc -l      # 必须 0
grep -rl --binary-files=text '/data/data/com.zhizhu.code' . | wc -l # ≈615
```

**与官方做包级对比（最强的正确性检查）**：

```bash
comm -23 <(官方包名排序) <(新包名排序)    # 必须为空：官方有的我们一个不缺
comm -13 <(官方包名排序) <(新包名排序)    # 我们的多出项
```

2026-09-25 实测：第一项为**空**；第二项仅 `libmagic`（新版 nano 依赖它，官方那份是 7 月旧版），
属版本差异而非漏删。

### 9.4 裁剪算法的正确性依据

以 `build-bootstraps.sh` 的 `PACKAGES` 清单为种子，沿 `var/lib/dpkg/status` 的
`Depends:`/`Pre-Depends:` 做广度优先闭包。该算法用**官方 bootstrap 自带的 status**（82 个包）
独立验证过：算出的闭包**恰好 82 个包，一个不多一个不少**。

安全性：`prune-bootstrap.js` 带**重叠保护**（只删「可删包拥有、且不被任何保留包拥有」的文件）
与**数量守卫**（闭包大小超出 60~150 就整体放弃，宁可归档偏大也不产出缺包的坏包）。实测 13813 个
删除文件中因重叠而跳过的为 **0**。

### 9.5 ELF 依赖兜底（2026-09-26 新增）

只按 `Depends:` 算闭包**不够** —— fork 的单容器构建会让包链接到它没声明的库。实测抓到一个：
`util-linux` 声明里没有 `libmount`，但 `bin/lsns` 实际链接了它 → 按 `Depends:` 算不在闭包里 →
被删 → **`bin/lsns` 变成 CANNOT LINK EXECUTABLE**，而整个过程不报任何错。

所以 `prune-bootstrap.js` 现在会**真的解析每个保留 ELF 的 `DT_NEEDED`**（自己读 ELF 小节头，
不依赖 readelf —— Termux 前缀里没有 binutils），发现「需要但没有任何保留包提供」的库时把它所属的
包拉回闭包，再重算，直到不动点；跑完还会复核一遍，仍有解析不了的库就逐条打印，**不静默通过**。

两个路径下的文件不参与兜底（都属于「装了也用不到」的可选组件）：

| 路径 | 为什么跳过 |
| --- | --- |
| `libexec/installed-tests/` | 随包安装的测试套件，含 `-fsanitize=address` 编出的二进制，需要 `libclang_rt.asan-*.so`（不属于任何运行时包） |
| `lib/python<版本>/site-packages/` | 可选的语言绑定。例如 libmount 带了 `pylibmount.so`，它需要 `libpython3.14.so`，不跳过就会把整个 python 包（**+23 MB**）拉进闭包 —— 而 bootstrap 里没有任何东西 import 它 |

> 顺带一个容易踩的坑：在 JS 里写文档注释时，路径 `lib/python*/site-packages/` 里面的 `*/` 会
> **提前关闭块注释**，后面内容变成代码直接语法报错。这就是上面写成 `lib/python<版本>/…` 的原因。

**注意**：`bin/mount`、`bin/lsblk`、`bin/cfdisk` 这类命令不在包里是**正常**的 —— 它们属于
util-linux 的**子包** `mount-utils` / `blk-utils` / `fdisk`，不在运行时闭包里，会被整体裁掉。

另外，bootstrap 的 zip 里**没有真实符号链接**（`find -type l` 结果为 0），符号链接全部记在
`SYMLINKS.txt`（约 2689 条），由 App 在解包时重建。所以 `bin/awk` 在 zip 里查不到实体文件是正常的。

### 9.6 官方 bootstrap 的 `bin/lsns` **也是坏的**（上游 bug，不是我们裁剪引入的）

拿官方那份 2026-07 的 bootstrap 对照：

```
官方 82 包，bin/lsns 的 DT_NEEDED = libsmartcols.so, libmount.so, libc.so
官方包内实际提供：libsmartcols.so ✅ / libmount.so ❌ / libblkid.so ❌
```

也就是说官方那份 `lsns` 一执行就 CANNOT LINK EXECUTABLE。**根因**：`bin/lsns` 属于 `util-linux`
主包，而 `libmount` 是**同一次 build 的子包** —— 上游没法在元数据里声明这个依赖，那是自依赖环；
而 `build-bootstraps.sh` 只按**包名**收集归档，没有任何东西依赖 `libmount`，于是它被漏掉。

所以这是**上游 bug**，我们用 ELF 兜底顺手修掉了。包数对比：

```
官方 82 包
我们 85 包 = 官方 82 包一个不缺 + libmagic（nano 新版依赖）
                              + libmount（修 lsns）+ libblkid（libmount 的依赖）
```

### 9.7 那句 SELinux 警告**只有 `sed` 会发**，`coreutils` / `tar` 不会

三个二进制都链接 `libandroid-selinux.so`，但只有 `sed` 的依赖是**未声明的**：
`coreutils` ✅ 已声明、`tar` ✅ 已声明、`sed` ❌ `Depends:` 无。光看「链接了 libandroid-selinux」
不能下结论（coreutils / tar 是上游**有意**链接的），要看它**会不会真的打那句警告**。实测方法是拿
裁剪后的出货二进制直接跑：

```bash
BS=<解包后的 bootstrap 目录>
LD_LIBRARY_PATH="$BS/lib" /system/bin/linker64 "$BS/bin/sed" -i 's/a/b/' f.txt
```

> 为什么要绕 `linker64`：`unzip` 在 Android 上**不保留可执行位**，解出来的二进制是 `-rw-------`，
> 直接跑 `Permission denied`；交给系统 linker 加载只需要读权限。
> `coreutils` 是 multicall 二进制，不能用 `--coreutils-prog=`（这个构建不认），得用**真实的
> argv[0]**：`ln -s coreutils ./touch` 然后跑 `./touch`，而且 argv[0] 必须是**绝对路径**。

| 命令 | 是否打警告 |
| --- | --- |
| `sed -i 's/bbb/BBB/' f.txt` | **⚠️ 会** |
| `coreutils touch` / `mkdir` / `cp` / `install` | ✅ 不会 |
| `tar cf` | ✅ 不会 |

结论：fork 的 `--without-selinux` 补丁**正确且充分** —— 只需要给 `sed` 打，不需要动
`coreutils` / `tar`（那是上游设计，改了反而偏离上游）。用户看到警告的路径也对得上：
`pkg install git` → `dpkg` 的 maintainer script 里大量用 `sed -i`。
