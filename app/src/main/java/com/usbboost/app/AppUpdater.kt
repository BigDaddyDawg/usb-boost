package com.usbboost.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

object AppUpdater {
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/BigDaddyDawg/usb-boost/main/update.json"

    fun check(currentVersionCode: Int): UpdateInfo? {
        val info = runCatching {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "USB-Boost")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(body)
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl")
            )
        }.getOrNull() ?: return null
        return info.takeIf { it.versionCode > currentVersionCode }
    }

    fun download(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "USB-Boost")
        }
        connection.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
    }

    fun needsInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return !context.packageManager.canRequestPackageInstalls()
    }

    fun installPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
