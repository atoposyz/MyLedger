package com.example.myledger.util

import android.content.Context
import android.content.pm.PackageManager

object AppVersion {
    fun name(context: Context): String = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "—"
}
