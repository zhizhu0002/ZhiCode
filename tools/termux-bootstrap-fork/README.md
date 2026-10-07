# 自建「前缀正确」的 Termux bootstrap

**目的**：产出前缀为 `/data/data/com.zhizhu.code/files/usr` 的 `bootstrap-aarch64.zip`，
这样内置用户空间**开箱即是本应用自己的路径**，不需要在解压时逐个文件做前缀改写。

这不是 hack：`termux-packages` 的 `scripts/build-bootstraps.sh` 头注释就写着它是为
**fork 出来的 termux 应用**准备 bootstrap 的；`scripts/properties.sh` 把包名做成了受校验的一等变量
（官方建议 `<= 21` 字符，`com.zhizhu.code` 是 15 字符）。

本目录里是与这件事有关的全部材料：

| 文件 | 作用 |
| --- | --- |
| `apply-prefix-changes.sh` | 在 fork 出来的 termux-packages 里改 `properties.sh`（幂等，留 `.bak`） |
| `build-bootstrap.yml` | 推荐的 GitHub Actions 工作流（含已踩过的坑与断言） |
| `build-bootstrap-short.yml` | 精简版工作流（少了 prune 与若干兜底断言，仅供快速试跑） |
| `prune-bootstrap.js` | 把构建出来的归档裁剪成「运行时依赖闭包」 |
| `VerifyBootstrap.java` | 验收脚本：确认归档里**没有旧前缀路径** |

---

## 1. 当前仓库里这一版的状态（已实测）

```bash
cd tools/termux-bootstrap-fork
javac -nowarn -d ~/.vb-out VerifyBootstrap.java
java -cp ~/.vb-out VerifyBootstrap ../../app/src/main/assets/bootstrap-aarch64.zip com.zhizhu.code
```

实测输出（**2026-09 那一版归档的快照**；换一版归档要重新跑上面的命令，别抄这里的数字）：

```
文件总数            : 3515
ELF 数              : 341
#! 脚本数           : 113
SYMLINKS.txt 行数   : 2689
新前缀命中文件数    : 621  (7272 次)
旧前缀命中          : 0 次
[PASS] 旧前缀已彻底清除
```

也就是说：**bootstrap 自身的路径问题已经解决**（旧前缀 0 命中）—— 这是下面每一条的前提。
体量数字同样只是那一版的快照：

```bash
ls -l app/src/main/assets/bootstrap-aarch64.zip        # 字节数
unzip -l app/src/main/assets/bootstrap-aarch64.zip | tail -1
unzip -p app/src/main/assets/bootstrap-aarch64.zip var/lib/dpkg/status | grep -c '^Package:'
```

⚠️ **但运行期从官方 apt 源装包时的路径改写没有消失**。`app/src/main/assets/zhicode/` 下的 6 个脚本
（`zhicode-deb-patch.sh`、`dpkg-wrapper.sh`、`apt-pre-install.sh`、`zhicode-patch-deb.sh`、
`99zhicode-prefix-rewrite.conf`、`99-zhicode-official-repository.sh`）正是为此存在：
官方 .deb 里写死的 `/data/data/com.termux` 要在安装时改写成本应用前缀。
另外 `app/build.gradle` 里记着一条硬依赖：所有执行内置二进制的路径都必须设置
`LD_LIBRARY_PATH=<prefix>/lib`（内置 ELF 的 `DT_RUNPATH` 指向的是 `com.termux` 的路径）。

**这两件事不是历史包袱，不要顺手删。**

---

## 2. 前置条件

- **x86_64 Linux 主机 + Docker + sudo**（本机 Android/Termux 做不到，这是唯一门槛）
- 磁盘：仓库 + 构建缓存约 **10~20 GB**
- 时间：首次构建约 **1~3 小时**（要编几十个包及其依赖）
- fork 是 public，且 fork 的 Actions 已启用（fork 默认关闭，这是最常见的首步失败）

---

## 3. 改动清单（只需要 2~3 处）

路径链自动派生，不需要手改任何路径：

```
TERMUX_APP__PACKAGE_NAME = "com.zhizhu.code"
  └─> TERMUX_APP__DATA_DIR = /data/data/com.zhizhu.code
        └─> TERMUX__ROOTFS  = /data/data/com.zhizhu.code/files
              ├─> TERMUX__HOME   = .../files/home
              └─> TERMUX__PREFIX = .../files/usr          ← bootstrap 的前缀
```

| # | 变量 | 原值 | 改为 | 作用 |
| --- | --- | --- | --- | --- |
| 1 | `TERMUX_APP__PACKAGE_NAME` | `com.termux` | `com.zhizhu.code` | **决定前缀**（经 DATA_DIR → ROOTFS → PREFIX 派生） |
| 2 | `TERMUX_APP__NAMESPACE` | `com.termux` | `com.zhizhu.code` | 组件类名前缀，须与应用的 Java 命名空间一致 |
| 3 | `TERMUX__NAME` | `Termux` | `ZhiCode` | 只进二阶段脚本的提示文本（可选） |

一键改（幂等）：

```bash
cd /path/to/your/termux-packages-fork
/path/to/this/dir/apply-prefix-changes.sh com.zhizhu.code com.zhizhu.code ZhiCode
```

### ⚠️ 不要改 `TERMUX_REPO__*` 那几行

它们是**硬编码**的（官方注释里带 FIXME），看起来像「漏改」，但官方明确要求保持原样：

> If a custom repo is not being hosted … then **DO NOT change these values**.

原因：这些值只被 `build-package.sh` 的 `-i` / `-I` 使用（从 apt 源下载**预编译**依赖），
而 `build-bootstraps.sh` **根本不传** `-i` / `-I`。所以对 bootstrap 构建零影响；改了只会多一行警告。
**这一点很关键**：依赖必须**本地编译** —— 那些 deb 是按 `com.termux` 前缀编译的，混进来会污染 bootstrap
（这也正是验收要检查「旧前缀必须为 0」的原因）。

> 其他残留（`TERMUX_API_APP__PACKAGE_NAME`、`TERMUX_AM_APP__NAMESPACE`、`CGCT_DEFAULT_PREFIX`）是插件 / glibc 专用，
> 会在二进制里留下**裸字符串 `com.termux`**（命名空间，不是路径）。
> **验收标准是「不存在 `/data/data/com.termux` 路径」，不是「不存在 `com.termux` 字样」。**

---

## 4. 构建

改了包名后**必须清缓存**，否则会复用按旧前缀编译的产物：

```bash
./scripts/run-docker.sh ./clean.sh                       # 1) 清缓存（改了包名必须做一次）
./scripts/run-docker.sh ./scripts/build-bootstraps.sh \
    --architectures aarch64 2>&1 | tee build.log         # 2) 只构建 aarch64
# 产物（正常情况下）：仓库根目录 bootstrap-aarch64.zip
```

- Docker 镜像：`ghcr.io/termux/package-builder`（可用 `TERMUX_BUILDER_IMAGE_NAME` 覆盖）
- 想加包：`--add openssh,git`
- 归档格式（与本工程 `RuntimeInstaller` 的 `SYMLINKS_ENTRY` 解析一致）：内容 = `$TERMUX_PREFIX` 下的文件；
  **符号链接不直接存放**，而是记录到 `SYMLINKS.txt`，每行 `目标←链接`

### 推荐直接用工作流

`build-bootstrap.yml` 把上面这些坑都写成断言了：改 2 行并断言「改动行数 = 2」、
预载镜像后清盘、用 `clean.sh` 代替 `-f`、注入 `TERMUX_PACKAGE_MANAGER`、修 AppArmor 落点、
最后跑验收并上传产物。

---

## 5. 裁剪成运行时闭包（`prune-bootstrap.js`）

`build-bootstraps.sh` 的 `extract_debs()` 是 `for deb in *.deb` —— 把 `output/` 里**每一个** deb 都解进归档。
而 fork 场景下依赖下载被禁用（日志里会出现
`Ignoring -i option to download dependencies since repo package name (com.termux) does not equal app package name (com.zhizhu.code)`），
于是纯构建期依赖（doxygen、python、perl、tcl/tk、X11 全家桶、fontconfig…）也全被从源码编译进 `output/`，
再一起解进 bootstrap。

脚本注释里记录的实测对比：

```
官方 release :  82 个包 /  3473 文件 /  32 MB
我们构建的   : 159 个包 / 17485 文件 / 122 MB
```

官方那 82 个包就是运行时闭包（由 `generate-bootstraps.sh` 按 `Depends:` 递归生成），多出来的 77 个全是编译期依赖。

```bash
unzip -q bootstrap-aarch64.zip -d /tmp/vb && node prune-bootstrap.js /tmp/vb [--dry-run]
```

算法三步，缺一不可：

1. **元数据闭包**：以 `build-bootstraps.sh` 的 `PACKAGES` 清单为种子，沿 `var/lib/dpkg/status` 的
   `Depends:` / `Pre-Depends:` 做 BFS。该算法已用官方 bootstrap 自带的 status（82 个包）独立验证：
   算出的闭包**恰好 82 个包**；
2. **ELF 依赖兜底**（关键）：只信 `Depends:` 不够 —— fork 的单容器构建会让包**链接到它没有声明的库**。
   实测抓到过 `util-linux` 的 `bin/lsns` 实际链接了未声明的 `libmount.so`，
   按第 1 步算它不在闭包里 → 被删掉 → **`lsns` 变成 CANNOT LINK EXECUTABLE**，而且整个流程不报任何错。
   所以这一步会真的解析每个保留 ELF 的 `DT_NEEDED`，发现缺库就把提供它的包拉回闭包，重新迭代到不动点；
3. **复核**：兜底后仍解析不了的库会逐条打印（不静默通过）。

安全性：只删除「可删包拥有、且不被任何保留包拥有」的文件；闭包大小超出 `[60,150]` 就整体放弃
（宁可归档偏大，不要缺包）；不触碰二阶段脚本。

---

## 6. 验收（交给本工程前先自检）

```bash
javac -nowarn -d ~/.vb-out VerifyBootstrap.java
java -cp ~/.vb-out VerifyBootstrap bootstrap-aarch64.zip com.zhizhu.code
```

| 检查项 | 期望 |
| --- | --- |
| `/data/data/com.termux` 出现次数 | **0**（唯一硬指标） |
| 新前缀命中文件数 | **> 0** |
| `SYMLINKS.txt` | 存在且行数 > 0 |
| 退出码 | 0 |

> 这两项的具体数值随归档版本变化，**上面第 1 节那些数字只是某一版的快照**；
> 判据是「> 0」，不是「等于某个数」。

---

## 7. 必须避开的四个坑（均实测踩到过）

### 坑 1：**不要传 `-f`** —— 会触发 `rm -f /*`

官方帮助说改包名后要 `clean.sh` **或** `-f`。**只能走 `clean.sh`。** 因为 `-f` 会设
`FORCE_BUILD_PACKAGES=1`，然后命中上游一个空变量 bug：

```bash
# scripts/build-bootstraps.sh
399   if [[ $FORCE_BUILD_PACKAGES == "1" ]]; then
400       rm -f "$TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH"/*     # ← 该变量从未被赋值
401       rm -f "$TERMUX_BUILT_DEBS_DIRECTORY"/*
```

`TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH` 在 `scripts/` 下**只出现在这一行**。
空展开后就是 `rm -f /*`，而 `-f` **不抑制** `rm: cannot remove '/bin': Permission denied` 这类错误 ——
`set -e` 之下第一条 `rm` 返回非 0 就直接退出。

### 坑 2：必须显式 `export TERMUX_PACKAGE_MANAGER=apt`，而且要**注进容器**

`build-bootstraps.sh` 只 source 两个文件，而 `TERMUX_PACKAGE_MANAGER` 两者都没定义。
脚本是 `set -e` 而非 `-u`，所以不报错，只会把空值替换进二阶段脚本：

```bash
export TERMUX_PACKAGE_MANAGER=""          # ← 坏掉
```

后果：apt 的包注册逻辑整段被跳过。而 `docker exec` **不继承宿主环境**，所以不能只在宿主 `export`，
要用它预留的注入口：

```bash
export TERMUX_DOCKER_EXEC_EXTRA_ARGS="--env TERMUX_PACKAGE_MANAGER=apt"
```

工作流的验收步骤因此加了断言：`grep -q '^export TERMUX_PACKAGE_MANAGER="apt"$'`，
以及「不得残留 `@占位符@`」。

### 坑 3：`util-linux` 在 NDK r30 + api-24 下编译失败

```
nsenter.c:189:27: error: variable has incomplete type 'struct file_handle'
Failed to build package 'apt' for arch 'aarch64'
```

根因不是探测写错，而是两个探测**双双误判为 yes**：

| 探测 | 为何误判 |
| --- | --- |
| `HAVE_STRUCT_NSFS_FILE_HANDLE` | 只验证 `<linux/nsfs.h>`（NDK r30 起才有），不验证 glibc 的 `struct file_handle`（bionic 需 API ≥ 26），而本构建用 api-24 sysroot |
| `HAVE_PIDFD_OPEN` | 只查 syscall **号**（aarch64 恒有），而缺的是 libc 包装 |

**修复**：在 `packages/util-linux/build.sh` 的 `TERMUX_PKG_EXTRA_CONFIGURE_ARGS` 里加
`ac_cv_type_struct_nsfs_file_handle=no`（与该文件既有的 `ac_cv_func_*` 覆盖风格一致），
效果等同编译掉该特性。**为什么官方 CI 不炸**：官方 bootstrap 由 `generate-bootstraps.sh` 拉预编译 deb，
且只为变更过的包重建，util-linux 一直没被重编 —— 走源码构建才会碰到。

### 坑 4：AppArmor 只允许容器写 `output/`（「编完了却拿不到 zip」的真因）

**现象**：日志里编译、提取、打包全部成功，最后只剩一行报错 + 工作区没有 zip：

```
  adding: bin/... (deflated 71%)          ← zip 其实已经生成好了
mv: cannot create regular file '/home/builder/termux-packages/bootstrap-aarch64.zip': Permission denied
[*] Finished successfully (aarch64).      ← 居然是「成功」
```

**根因**：`scripts/run-docker.sh` 在 `docker exec` 之前**一定会**加载受限 AppArmor 模板
`scripts/profile-restricted.apparmor`，它只允许容器写仓库里的 `output/`。
更坑的是那句 `mv` 被 `create_bootstrap_archive "$TERMUX_ARCH" || return $?` 调用，
`errexit` 被抑制 → 脚本继续打印 "Finished successfully" → EXIT trap 又 `rm -rf` 掉临时目录（zip 就在里面）。

**修法**：宿主侧（不受 AppArmor 约束）把 `mv` 目标改成 `output/`，构建成功后再把 zip 搬回仓库根；
**并且自己断言产物存在** —— 否则两小时构建白跑。`build-bootstrap.yml` 里这两步都有。
