<div align="center">

# ZhiCode

**面向 Android 的编码 Agent 工作区**

在手机上打开项目、与 Agent 协作、编辑代码，并在同一工作区运行终端。

![Android 7.0+ · arm64-v8a][android-badge]
![Kotlin][kotlin-badge]
[![Android Build][build-badge]][build]
[![MIT License][license-badge]](LICENSE)
![快速迭代中][status-badge]

[下载 APK][releases] · [快速开始](#快速开始) · [从源码构建](#从源码构建) · [贡献指南](CONTRIBUTING.md)

</div>

> [!WARNING]
> **ZhiCode 仍处于快速迭代和实验阶段。** 大部分代码、界面改造和文档由 AI Agent 协作完成，尚未经过充分的人工审查与长期稳定性验证。请在试用前备份重要数据，暂勿用于生产环境。


## 核心功能

| 功能 | 能力 |
| --- | --- |
| **编码 Agent** | 在项目目录中读取和编辑文件、运行命令、跟踪任务。支持流式对话、会话恢复和执行权限审批 |
| **模型接入** | 配置自己的模型服务与 Base URL，支持 OpenAI Responses、OpenAI Chat Completions 和 Anthropic Messages 等协议 |
| **终端与编辑器** | 应用内置 Ubuntu 24.04 与带 TextMate 语法高亮的代码编辑器。由 PRoot 运行，内置 Node.js 与 Pi |
| **工作区管理** | 可快速按项目组织和切换会话，添加 MCP 服务、技能、角色卡与记忆 |
| **Android 开发** | 在虚拟化运行时中免安装运行和调试 APK，按需通过 Shizuku 使用 Android shell 权限 |

## 快速开始

ZhiCode 仅构建 `arm64-v8a` APK，要求 Android 7.0（API 24）或更高版本。

**下载安装包：** 

Releases 版通道 [GitHub Releases][releases]

CI 构建通道 [GitHub Actions][build]

以下步骤适用于最新 CI 构建。Releases 暂时未跟进 Ubuntu ( Releases 1.0 支持 Termux ) [1.0.0 发行说明](docs/release-notes-1.0.0.md)

1. **初始化运行环境。** 安装并打开 APK，按首次启动引导选择镜像源，下载并安装 Ubuntu 24.04。此步骤需要网络和足够的解压空间。
2. **配置模型。** 在引导页点「前往 API 配置」，选择供应商，填写对应的凭据和模型。选择协议、填写 Base URL 与模型 ID。
3. **打开工作区。** 在侧栏点「添加工作区」，选择项目目录并新建会话。可以先请 Agent 阅读项目并说明目录结构和构建方式。

## 从源码构建

使用 JDK 21 和 Android SDK，Gradle 由仓库内提供。

```bash
git clone https://github.com/zhizhu0002/ZhiCode.git
cd ZhiCode
./gradlew :app:assembleDebug
```

JVM 测试： `./gradlew :app:testDebugUnitTest`

Termux 构建、Release 签名、R8 和 CI 的细节见[构建与发布文档](docs/build-and-release.md)。

## 项目结构

| 路径 | 职责 |
| --- | --- |
| [`app/`](app) | Kotlin / Jetpack Compose 界面、Java Agent 引擎、Pi 接入、模型协议、终端与沙箱宿主 |
| [`Bcore/`](Bcore) | 基于 BlackBox 的 Android 虚拟化运行时 |
| [`black-reflection/`](black-reflection) 与 [`compiler/`](compiler) | Bcore 的反射库和编译期注解处理器 |

- [架构与代码结构](docs/architecture.md) · [Bcore 补丁与沙箱约定](docs/provenance.md)
- [Miuix 界面开发](docs/ui-miuix.md) · [性能约定](docs/performance.md)
- [构建与发布](docs/build-and-release.md) · [许可与来源](docs/licensing.md)

## 贡献

欢迎提交问题、改进文档或发起 Pull Request。报告问题时请附上 ZhiCode 版本、Android 版本、设备型号和复现步骤。提交代码前请参阅[贡献指南](CONTRIBUTING.md)中的本地验证与代码约定。[提交 Issue][issues]

## 安全

Agent 可以执行命令并修改工作区文件，请在批准前检查操作内容。APK 虚拟化用于开发与调试，缺少对不可信 APK 的安全隔离保证。权限模式、密钥存储、合并清单和私下报告漏洞的方式见[安全说明](SECURITY.md)。

## 许可

ZhiCode 自有代码采用 [MIT 许可](LICENSE)。APK 还包含各自授权的第三方组件。分发时请随附 [LICENSE](LICENSE)、[NOTICE](NOTICE) 和 [第三方许可文本](THIRD-PARTY-LICENSES)，并按[许可与来源](docs/licensing.md)核对组件的署名和源码提供要求。

## 第三方开源项目

- **IQ Code** 是 ZhiCode 的上游项目。早期 Agent 引擎、协议、工具与沙箱宿主由此发展而来。[上游仓库](https://github.com/iqisge-gif/IQ-Code-Android) · [MIT 许可](THIRD-PARTY-LICENSES/IQ-Code-MIT.txt)
- **Termux、PRoot、Ubuntu、Node.js 和 Pi** 提供终端、Linux 用户空间与 Agent 运行时。[来源及许可](NOTICE)
- **BlackBox、Dobby 与 Shizuku** 提供 Android 虚拟化、底层调试与系统 shell 接入能力。[BlackBox](https://github.com/ALEX5402/NewBlackbox) · [Dobby](https://github.com/jmpews/Dobby) · [Shizuku](https://github.com/RikkaApps/Shizuku)
- **Miuix、Sora Editor、TextMate 与 Material Symbols** 提供界面组件、编辑器、语法资源与图标。[Miuix](https://github.com/compose-miuix-ui/miuix) · [Sora Editor](https://github.com/Rosemoe/sora-editor) · [TextMate 资源署名](app/src/main/assets/sora/textmate/NOTICE.md) · [完整致谢](NOTICE)

[android-badge]: https://img.shields.io/badge/Android-7.0%2B%20%7C%20arm64--v8a-3DDC84?logo=android&logoColor=white
[kotlin-badge]: https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white
[build-badge]: https://github.com/zhizhu0002/ZhiCode/actions/workflows/build.yml/badge.svg?branch=main
[license-badge]: https://img.shields.io/badge/License-MIT-yellow
[status-badge]: https://img.shields.io/badge/Status-Development-blue
[build]: https://github.com/zhizhu0002/ZhiCode/actions/workflows/build.yml
[releases]: https://github.com/zhizhu0002/ZhiCode/releases
[issues]: https://github.com/zhizhu0002/ZhiCode/issues/new
