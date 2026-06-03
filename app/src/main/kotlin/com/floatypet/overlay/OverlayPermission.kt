package com.floatypet.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 悬浮窗权限（SYSTEM_ALERT_WINDOW）。Android 不提供运行时弹窗，只能跳系统设置页，
 * 故 UI 先展示引导，再调 [createSettingsIntent] 跳转（见 AGENT.md §3.5）。
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 跳转到本应用的「显示在其他应用上层」系统设置页。 */
    fun createSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
