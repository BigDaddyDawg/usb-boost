package com.usbboost.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object EnhancedPermissionHelper {
    const val SHIZUKU_REQUEST_CODE = 9001

    fun tryGrantDumpViaShizuku(context: Context): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val process = Shizuku.newProcess(
                arrayOf(
                    "pm",
                    "grant",
                    context.packageName,
                    "android.permission.DUMP"
                ),
                null,
                null
            )
            val exit = process.waitFor()
            exit == 0 && OutputMonitor.hasDumpPermission(context)
        }.getOrElse {
            Log.w(TAG, "Shizuku DUMP grant failed", it)
            false
        }
    }

    fun shizukuAvailable(): Boolean = Shizuku.pingBinder()

    fun needsShizukuPermission(): Boolean {
        return shizukuAvailable() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
    }

    fun requestShizukuPermission() {
        if (needsShizukuPermission()) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    companion object {
        private const val TAG = "EnhancedPermissionHelper"
    }
}
