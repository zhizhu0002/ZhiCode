package com.zhizhu.zhicode.compose.model

/**
 * 对话流中的条目类型。
 *
 * ## 为什么它单独一个文件（原来住在 `UiModels.kt` 里）
 *
 * 与 [ToolKind] 同一个理由：**纯逻辑层要用它**。[TurnLayout] 要按"是不是用户发言"
 * 来切分助手回合，而 `UiModels.kt` 里还拖着一整套界面状态类（MCP / 技能 / 角色卡 /
 * 内存 / 设置草稿…），把整份拉进秒级回路不划算。
 *
 * 同包，所以这个搬迁**不需要**改任何调用点的 import。
 */
enum class ChatKind { USER, ASSISTANT, TOOL_GROUP, ERROR, INFO }
