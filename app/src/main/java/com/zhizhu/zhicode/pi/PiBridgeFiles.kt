package com.zhizhu.zhicode.pi

import android.content.Context
import android.system.Os
import com.zhizhu.zhicode.ContainerPaths
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 同步宿主提供的 Pi 扩展与容器内提权 CLI 命令到 Debian 根目录。
 */
internal object PiBridgeFiles {

    private const val BRIDGE_ASSET = "pi/zhicode-bridge.ts"

    @JvmStatic
    fun bridgeExtensionGuestPath(): String = "/etc/zhicode/zhicode-bridge.ts"

    @JvmStatic
    fun bridgeExtensionHostFile(): File = File(ContainerPaths.rootfsDir(), "etc/zhicode/zhicode-bridge.ts")

    @JvmStatic
    fun ensureInstalled(context: Context) {
        val rootfs = ContainerPaths.rootfsDir()
        if (!File(rootfs, "bin/bash").exists()) return

        val assetBytes = context.assets.open(BRIDGE_ASSET).use { it.readBytes() }
        writeIfChanged(bridgeExtensionHostFile(), assetBytes, executable = false)

        val binDir = File(rootfs, "usr/local/bin")
        binDir.mkdirs()
        writeIfChanged(File(binDir, "zhi-root"), commandScript("Root").toByteArray(StandardCharsets.UTF_8), executable = true)
        writeIfChanged(File(binDir, "zhi-shizuku"), commandScript("Shizuku").toByteArray(StandardCharsets.UTF_8), executable = true)
        writeIfChanged(File(binDir, "zhi-open"), openScript().toByteArray(StandardCharsets.UTF_8), executable = true)
    }

    private fun writeIfChanged(target: File, bytes: ByteArray, executable: Boolean) {
        target.parentFile?.mkdirs()
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) {
            if (executable) runCatching { Os.chmod(target.absolutePath, 493) }
            return
        }
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (executable) runCatching { Os.chmod(tmp.absolutePath, 493) }
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
        if (executable) runCatching { Os.chmod(target.absolutePath, 493) }
    }

    private fun commandScript(toolName: String): String = """#!/usr/bin/env node
const url = process.env.ZHICODE_BRIDGE_URL;
const token = process.env.ZHICODE_BRIDGE_TOKEN;
if (!url || !token) {
  console.error("ZHICODE_BRIDGE_URL / ZHICODE_BRIDGE_TOKEN 未就绪，请从 ZhiCode 终端或 Agent 会话运行");
  process.exit(1);
}
const command = process.argv.slice(2).join(" ").trim();
if (!command) {
  console.error("用法: ${if (toolName == "Root") "zhi-root" else "zhi-shizuku"} <command>");
  process.exit(2);
}
(async () => {
  const res = await fetch(url + "/invoke", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-ZhiCode-Token": token },
    body: JSON.stringify({ tool: "$toolName", input: { command, cwd: process.cwd() } }),
  });
  const data = await res.json();
  if (data.text) process.stdout.write(data.text.endsWith("\n") ? data.text : data.text + "\n");
  process.exit(typeof data.exitCode === "number" ? data.exitCode : (data.isError ? 1 : 0));
})().catch((err) => {
  console.error(String(err));
  process.exit(1);
});
"""

    private fun openScript(): String = """#!/usr/bin/env node
const url = process.env.ZHICODE_BRIDGE_URL;
const token = process.env.ZHICODE_BRIDGE_TOKEN;
if (!url || !token) {
  console.error("ZHICODE_BRIDGE_URL / ZHICODE_BRIDGE_TOKEN 未就绪");
  process.exit(1);
}
const target = (process.argv[2] || "").trim();
if (!target) {
  console.error("用法: zhi-open <url|file_or_apk_path>");
  process.exit(2);
}
const input = target.includes("://")
  ? { operation: "intent", uri: target }
  : target.endsWith(".apk")
    ? { operation: "install_apk", path: target }
    : { operation: "intent", path: target };
(async () => {
  const res = await fetch(url + "/invoke", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-ZhiCode-Token": token },
    body: JSON.stringify({ tool: "AndroidIntent", input }),
  });
  const data = await res.json();
  if (data.text) process.stdout.write(data.text.endsWith("\n") ? data.text : data.text + "\n");
  process.exit(data.isError ? 1 : 0);
})().catch((err) => {
  console.error(String(err));
  process.exit(1);
});
"""
}
