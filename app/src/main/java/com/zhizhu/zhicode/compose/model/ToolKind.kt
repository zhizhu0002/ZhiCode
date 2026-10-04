package com.zhizhu.zhicode.compose.model

/**
 * 工具类别，用于聚合卡的文案（"搜索 N 个模式 / 读取 N 个文件"）
 * 以及工具菜单的判据（只有 [COMMAND] 才有"命令"可复制）。
 *
 * ## 为什么它单独一个文件（原来住在 `UiModels.kt` 里）
 *
 * 因为它被**纯逻辑层**用到了：[ToolActions.flags] 按它决定 `isCommand`。
 * 而 `UiModels.kt` 里还有一大片只跟界面状态有关的东西（`WorkspaceUiState` 引用了
 * MCP / 技能 / 角色卡 / 内存 / 设置草稿等一整套状态类），把它整体拉进秒级回路
 * 就得连带编译那一串，代价与收益完全不成比例。
 *
 * 拆出来之后 `ToolKind.kt` + `ToolActions.kt` 是两个没有依赖的小文件，可以直接进
 * `test-jvm-fast.sh` 的 `MAIN_KT_SOURCES`（它不带 android.jar 编译，所以这同时也
 * 证明了"这个判据真的与 Android 无关"）。
 *
 * 同包，所以这个搬迁**不需要**改任何调用点的 import。
 */
enum class ToolKind { SEARCH, READ, EDIT, COMMAND, OTHER }
