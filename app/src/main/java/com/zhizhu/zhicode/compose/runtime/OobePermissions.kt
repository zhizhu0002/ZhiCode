package com.zhizhu.zhicode.compose.runtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import com.zhizhu.zhicode.shizuku.ShizukuBridge
import java.io.File

/** OOBE 向导步骤。 */
enum class OobeStep(val index: Int, val title: String) {
    PERMISSIONS(0, "系统权限"),
    RUNTIME(1, "运行环境"),
    MODEL(2, "模型配置"),
}

/** 当前设备权限与可选增强能力快照。 */
data class OobePermissionsSnapshot(
    val legacyStorageGranted: Boolean = false,
    val allFilesGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val installPackagesGranted: Boolean = false,
    val shizukuState: Int = ShizukuBridge.UNAVAILABLE,
    val shizukuLabel: String = "未安装",
    val rootBinaryPresent: Boolean = false,
) {
    val storageGranted: Boolean
        get() = legacyStorageGranted && allFilesGranted

    val allRequiredGranted: Boolean
        get() = storageGranted && notificationsGranted && batteryOptimizationIgnored
}

/** 探测与拉起 OOBE 所需系统权限设置。 */
internal object OobePermissions {

    val STORAGE_RUNTIME_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    private val SU_CANDIDATES = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
    )

    fun inspect(context: Context): OobePermissionsSnapshot {
        val pm = context.packageManager
        val pkg = context.packageName
        val readOk = hasPermission(pm, pkg, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writeOk = hasPermission(pm, pkg, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val allFilesOk = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notifChannelOk = notifManager?.areNotificationsEnabled() ?: true
        val notifRuntimeOk = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(pm, pkg, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = power?.isIgnoringBatteryOptimizations(pkg) ?: true

        val installOk = if (Build.VERSION.SDK_INT >= 26) {
            runCatching { pm.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

        val shizukuState = runCatching { ShizukuBridge.state(context) }.getOrDefault(ShizukuBridge.UNAVAILABLE)
        val shizukuLabel = runCatching { ShizukuBridge.stateLabel(context) }.getOrDefault("未安装")

        return OobePermissionsSnapshot(
            legacyStorageGranted = readOk && writeOk,
            allFilesGranted = allFilesOk,
            notificationsGranted = notifChannelOk && notifRuntimeOk,
            batteryOptimizationIgnored = batteryIgnored,
            installPackagesGranted = installOk,
            shizukuState = shizukuState,
            shizukuLabel = shizukuLabel,
            rootBinaryPresent = hasSuBinary(),
        )
    }

    fun allFilesAccessIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 30) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        appDetailsIntent(context)
    }

    fun allFilesAccessFallbackIntent(): Intent = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun notificationSettingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        appDetailsIntent(context)
    }

    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batterySettingsFallbackIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun unknownAppSourcesIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        appDetailsIntent(context)
    }

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun hasPermission(pm: PackageManager, pkg: String, permission: String): Boolean =
        runCatching { pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    private fun hasSuBinary(): Boolean = SU_CANDIDATES.any { path ->
        runCatching { File(path).exists() }.getOrDefault(false)
    }
}
