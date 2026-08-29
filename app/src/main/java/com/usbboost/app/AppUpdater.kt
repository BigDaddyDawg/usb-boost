package com.usbboost.app

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

sealed class UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck()
    object UpToDate : UpdateCheck()
    data class Failed(val message: String) : UpdateCheck()
}

object AppUpdater {
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/BigDaddyDawg/usb-boost/main/update.json"
    const val MANIFEST_FALLBACK =
        "https://github.com/BigDaddyDawg/usb-boost/raw/main/update.json"
    const val TAGGED_APK_URL =
        "https://github.com/BigDaddyDawg/usb-boost/releases/download/v2.1.1/app-release.apk"
    const val LATEST_APK_URL =
        "https://github.com/BigDaddyDawg/usb-boost/releases/latest/download/app-release.apk"
    const val ACTION_INSTALL_RESULT = "com.usbboost.app.INSTALL_RESULT"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 USBBoost/2.6.0"

    fun parseManifest(body: String): UpdateInfo {
        return UpdateInfo(
            versionCode = intField(body, "versionCode"),
            versionName = stringField(body, "versionName"),
            apkUrl = stringField(body, "apkUrl")
        )
    }

    fun decide(info: UpdateInfo, currentVersionCode: Int): UpdateCheck {
        return if (info.versionCode > currentVersionCode) {
            UpdateCheck.Available(info)
        } else {
            UpdateCheck.UpToDate
        }
    }

    fun apkUrls(info: UpdateInfo): List<String> {
        return linkedSetOf(info.apkUrl, TAGGED_APK_URL, LATEST_APK_URL).filter { it.isNotBlank() }
    }

    fun looksLikeApk(file: File): Boolean {
        if (!file.isFile || file.length() < 10_000) return false
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        return looksLikeApkHeader(header)
    }

    fun looksLikeApkHeader(header: ByteArray): Boolean {
        return header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
    }

    fun check(currentVersionCode: Int): UpdateCheck {
        val errors = mutableListOf<String>()
        for (url in listOf(MANIFEST_URL, MANIFEST_FALLBACK)) {
            val result = runCatching {
                decide(parseManifest(readUtf8(url)), currentVersionCode)
            }
            val value = result.getOrNull()
            if (value != null) return value
            errors += result.exceptionOrNull()?.message ?: "check failed"
        }
        return UpdateCheck.Failed(errors.lastOrNull() ?: "Could not reach the update")
    }

    fun download(urls: List<String>, dest: File) {
        dest.parentFile?.mkdirs()
        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadOne(url, dest)
                if (!looksLikeApk(dest)) {
                    dest.delete()
                    lastError = IllegalStateException("Download was not a valid APK")
                    continue
                }
                return
            } catch (error: Exception) {
                dest.delete()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Download failed")
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

    fun signaturesMatch(context: Context, apk: File): Boolean? {
        return runCatching {
            val incoming = archiveSignatures(context, apk) ?: return@runCatching null
            val current = installedSignatures(context) ?: return@runCatching null
            incoming.isNotEmpty() && incoming == current
        }.getOrNull()
    }

    fun copyToDownloads(context: Context, apk: File, versionName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val name = "usb-boost-$versionName.apk"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                apk.inputStream().use { it.copyTo(out) }
            } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.onFailure {
            Log.w(TAG, "Could not copy update to Downloads", it)
        }.getOrDefault(false)
    }

    fun install(context: Context, apk: File) {
        if (installWithSession(context, apk)) return
        context.startActivity(viewIntent(context, apk))
    }

    fun installIntent(context: Context, apk: File): Intent = viewIntent(context, apk)

    fun handleInstallResult(intent: Intent): String? {
        if (intent.action != ACTION_INSTALL_RESULT) return null
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> null
            PackageInstaller.STATUS_PENDING_USER_ACTION -> null
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> SIGNATURE_MESSAGE
            else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                ?: "Install failed"
        }
    }

    fun pendingUserAction(intent: Intent): Intent? {
        if (intent.action != ACTION_INSTALL_RESULT) return null
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun installWithSession(context: Context, apk: File): Boolean {
        return runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val callback = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_INSTALL_RESULT
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingFlag()
                val pending = PendingIntent.getActivity(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
            true
        }.onFailure {
            Log.w(TAG, "PackageInstaller session failed", it)
        }.getOrDefault(false)
    }

    private fun viewIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName?.let { pkg ->
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        ).forEach { pkg ->
            runCatching {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return intent
    }

    private fun downloadOne(url: String, dest: File) {
        val connection = openFollowingRedirects(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readUtf8(url: String): String {
        val connection = openFollowingRedirects(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun openFollowingRedirects(url: String): HttpURLConnection {
        var current = url
        repeat(8) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = connection.responseCode
            if (code in REDIRECTS) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("HTTP $code with no Location")
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun archiveSignatures(context: Context, apk: File): Set<String>? {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return null
        return signatureStrings(info)
    }

    private fun installedSignatures(context: Context): Set<String>? {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return signatureStrings(info)
    }

    private fun signatureStrings(info: android.content.pm.PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            val sigs = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            sigs.map { it.toCharsString() }.toSet()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
        }
    }

    private fun mutablePendingFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }

    const val SIGNATURE_MESSAGE =
        "Android blocked the update (different signing key). Uninstall USB Boost, then open Downloads and tap the APK. After that, Check for update will work."

    private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    private const val TAG = "AppUpdater"

    private fun intField(json: String, key: String): Int =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toInt()
            ?: throw IllegalArgumentException("Missing $key")

    private fun stringField(json: String, key: String): String =
        Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.replace("\\\"", "\"")
            ?: throw IllegalArgumentException("Missing $key")
}
