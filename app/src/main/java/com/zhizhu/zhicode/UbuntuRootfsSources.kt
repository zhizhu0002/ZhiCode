package com.zhizhu.zhicode

/** Ubuntu 24.04 LTS (Noble, arm64) 根文件系统云端下载源。 */
enum class UbuntuMirrorSource(
    val id: String,
    val displayName: String,
    val summary: String,
) {
    TUNA(
        id = "tuna",
        displayName = "清华大学 TUNA 镜像站",
        summary = "mirrors.tuna.tsinghua.edu.cn · Ubuntu Base 24.04 (.tar.gz)",
    ),
    USTC(
        id = "ustc",
        displayName = "中科大 USTC 镜像站",
        summary = "mirrors.ustc.edu.cn · Ubuntu Base 24.04 (.tar.gz)",
    ),
    GITHUB(
        id = "github",
        displayName = "GitHub Releases",
        summary = "termux/proot-distro · Ubuntu 24.04 LTS (.tar.xz)",
    );

    companion object {
        fun findById(raw: String?): UbuntuMirrorSource? {
            val key = raw?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.id == key }
        }

        fun fromId(raw: String?): UbuntuMirrorSource = findById(raw) ?: TUNA
    }
}

data class UbuntuDownloadSpec(
    val source: UbuntuMirrorSource,
    val versionLabel: String,
    val archiveUrl: String,
    val expectedSha256: String,
    val isXz: Boolean,
    val stripComponents: Int,
)

/** Ubuntu 24.04 rootfs 下载清单与 `SHA256SUMS` 纯解析器。 */
object UbuntuRootfsSources {

    const val TUNA_BASE_DIR_URL =
        "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/noble/release/"
    const val USTC_BASE_DIR_URL =
        "https://mirrors.ustc.edu.cn/ubuntu-cdimage/ubuntu-base/releases/noble/release/"

    const val GITHUB_ARCHIVE_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.17.3/ubuntu-noble-aarch64-pd-v4.17.3.tar.xz"
    const val GITHUB_ARCHIVE_SHA256 =
        "81ac0fb0d16ded12ab11cede62f67b875ff56f9fa1aa9eb786415c3ec5c477d2"

    fun sha256SumsUrl(source: UbuntuMirrorSource): String? = when (source) {
        UbuntuMirrorSource.TUNA -> "${TUNA_BASE_DIR_URL}SHA256SUMS"
        UbuntuMirrorSource.USTC -> "${USTC_BASE_DIR_URL}SHA256SUMS"
        UbuntuMirrorSource.GITHUB -> null
    }

    fun baseDirUrl(source: UbuntuMirrorSource): String? = when (source) {
        UbuntuMirrorSource.TUNA -> TUNA_BASE_DIR_URL
        UbuntuMirrorSource.USTC -> USTC_BASE_DIR_URL
        UbuntuMirrorSource.GITHUB -> null
    }

    fun parseUbuntuBaseSha256Sums(sumsText: String, source: UbuntuMirrorSource): UbuntuDownloadSpec? {
        val baseDir = baseDirUrl(source) ?: return null
        val linePattern = Regex("^([0-9a-fA-F]{64})\\s+\\*?\\.?/?(ubuntu-base-(24\\.04(?:\\.\\d+)?)-base-arm64\\.tar\\.gz)$")
        var best: UbuntuDownloadSpec? = null
        var bestPatch = -1
        for (rawLine in sumsText.lineSequence()) {
            val match = linePattern.matchEntire(rawLine.trim()) ?: continue
            val sha = match.groupValues[1].lowercase()
            val filename = match.groupValues[2]
            val version = match.groupValues[3]
            val patchNum = version.substringAfter("24.04.", "0").toIntOrNull() ?: 0
            if (best == null || patchNum >= bestPatch) {
                bestPatch = patchNum
                best = UbuntuDownloadSpec(
                    source = source,
                    versionLabel = "Ubuntu $version LTS (noble arm64)",
                    archiveUrl = "$baseDir$filename",
                    expectedSha256 = sha,
                    isXz = false,
                    stripComponents = 0,
                )
            }
        }
        return best
    }

    fun githubSpec(): UbuntuDownloadSpec = UbuntuDownloadSpec(
        source = UbuntuMirrorSource.GITHUB,
        versionLabel = "Ubuntu 24.04 LTS (noble aarch64 · proot-distro v4.17.3)",
        archiveUrl = GITHUB_ARCHIVE_URL,
        expectedSha256 = GITHUB_ARCHIVE_SHA256,
        isXz = true,
        stripComponents = 1,
    )

    fun aptPortsMirrorUrl(source: UbuntuMirrorSource): String = when (source) {
        UbuntuMirrorSource.TUNA -> "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/"
        UbuntuMirrorSource.USTC -> "http://mirrors.ustc.edu.cn/ubuntu-ports/"
        UbuntuMirrorSource.GITHUB -> "http://ports.ubuntu.com/ubuntu-ports/"
    }
}
