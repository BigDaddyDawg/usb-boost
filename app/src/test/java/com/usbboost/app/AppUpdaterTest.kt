package com.usbboost.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdaterTest {
    @Test
    fun parseManifestReadsVersionAndStableDownloadUrl() {
        val info = AppUpdater.parseManifest(
            """
            {
              "versionCode": 18,
              "versionName": "2.6.1",
              "apkUrl": "https://github.com/BigDaddyDawg/usb-boost/releases/download/v2.1.1/app-release.apk"
            }
            """.trimIndent()
        )
        assertEquals(18, info.versionCode)
        assertEquals("2.6.1", info.versionName)
        assertEquals(AppUpdater.TAGGED_APK_URL, info.apkUrl)
    }

    @Test
    fun checkTreatsEqualVersionAsUpToDateAndHigherAsAvailable() {
        val info = UpdateInfo(18, "2.6.1", AppUpdater.TAGGED_APK_URL)
        assertTrue(AppUpdater.decide(info, 17) is UpdateCheck.Available)
        assertTrue(AppUpdater.decide(info, 18) is UpdateCheck.UpToDate)
        assertTrue(AppUpdater.decide(info, 19) is UpdateCheck.UpToDate)
    }

    @Test
    fun downloadFallbacksAlwaysIncludeTheStableReleaseLink() {
        val urls = AppUpdater.apkUrls(UpdateInfo(18, "2.6.1", "https://example.com/missing.apk"))
        assertTrue(urls.contains(AppUpdater.TAGGED_APK_URL))
        assertTrue(urls.contains(AppUpdater.LATEST_APK_URL))
        assertEquals("https://example.com/missing.apk", urls.first())
    }

    @Test
    fun apkMagicIsZipHeaderAndRejectsHtml() {
        assertTrue(AppUpdater.looksLikeApkHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(AppUpdater.looksLikeApkHeader(byteArrayOf(0x3C, 0x68, 0x74, 0x6D)))
        val tiny = File.createTempFile("usb-boost", ".apk")
        tiny.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        assertFalse(AppUpdater.looksLikeApk(tiny))
        tiny.delete()
    }
}
