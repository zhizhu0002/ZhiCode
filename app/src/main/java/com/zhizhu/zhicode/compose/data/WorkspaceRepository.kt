package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.TerminalLine

/**
 * 尚未真实化的数据来源。
 *
 * 现状：会话历史（[SessionReader]）、文件面板（[FileBrowser]）、
 * git 变更（[GitChanges]）、项目路径（[WorkspacePaths]）、
 * 对话引擎（`engine/ZhiEngineController`）全部已接真实实现。
 *
 * 只剩最后一项 [terminalBanner]：内置环境**未就绪**时终端页显示的只读占位文案。
 * 真实 PTY 接上后（TerminalPane 的 runtimeReady 分支）它就不再被使用，
 * 等终端页完全不依赖占位数据时这个接口可以整体删掉。
 *
 * ⚠️ 刻意不保留任何"返回假列表 / 假历史 / 假 diff / 假项目"的方法：
 * 那类方法一旦存在就容易被误接回去，表现为"界面像在工作、其实数据是假的"。
 */
interface WorkspaceRepository {
    fun terminalBanner(project: String): List<TerminalLine>
}
