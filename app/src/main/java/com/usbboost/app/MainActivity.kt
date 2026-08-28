package com.usbboost.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.usbboost.app.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BoostPrefs
    private var pendingOneTapAfterNotification = false
    private var pendingOneTapAfterShizuku = false

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED && pendingOneTapAfterShizuku) {
            pendingOneTapAfterShizuku = false
            runOneTapSetup(showToast = true)
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingOneTapAfterNotification) {
            pendingOneTapAfterNotification = false
            runOneTapSetup(showToast = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BoostPrefs(this)
        Shizuku.addOnRequestPermissionResultListener(shizukuListener)
        bindUi()
    }

    override fun onDestroy() {
        Shizuku.removeOnRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindUi() {
        val settings = prefs.load()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchAutoCar.isChecked = settings.autoCarMode
        binding.switchLegacy.isChecked = settings.legacyMode
        binding.switchEnhanced.isChecked = settings.enhancedDetection
        binding.switchBoot.isChecked = settings.startOnBoot
        binding.sliderBoost.value = settings.boostPercent.toFloat()
        binding.sliderBass.value = settings.bassPercent.toFloat()
        updateValueLabels(settings)

        binding.buttonOneTapSetup.setOnClickListener { beginOneTapSetup() }

        binding.switchEnabled.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchAutoCar.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchLegacy.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchEnhanced.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchBoot.setOnCheckedChangeListener { _, _ -> persistAndApply() }

        binding.sliderBoost.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            saveAndReloadService(updated)
        }
        binding.sliderBass.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            saveAndReloadService(updated)
        }

        binding.buttonStart.setOnClickListener { ensurePermissionAndStart() }
        binding.buttonStop.setOnClickListener {
            BoostService.stop(this)
            Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
    }

    private fun beginOneTapSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingOneTapAfterNotification = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        runOneTapSetup(showToast = true)
    }

    private fun runOneTapSetup(showToast: Boolean) {
        if (EnhancedPermissionHelper.needsShizukuPermission()) {
            pendingOneTapAfterShizuku = true
            EnhancedPermissionHelper.requestShizukuPermission()
            if (showToast) {
                Toast.makeText(this, R.string.setup_shizuku_wait, Toast.LENGTH_LONG).show()
            }
            return
        }

        val setupSettings = SetupHelper.buildSettingsForSetup(this, currentSettings())
        prefs.save(setupSettings)
        applySettingsToUi(setupSettings)
        reloadService()

        if (showToast) {
            val message = when {
                OutputMonitor.hasDumpPermission(this) -> R.string.setup_done_enhanced
                setupSettings.legacyMode -> R.string.setup_done_legacy
                else -> R.string.setup_done
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    private fun applySettingsToUi(settings: BoostSettings) {
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchAutoCar.isChecked = settings.autoCarMode
        binding.switchLegacy.isChecked = settings.legacyMode
        binding.switchEnhanced.isChecked = settings.enhancedDetection
        binding.switchBoot.isChecked = settings.startOnBoot
        updateValueLabels(settings)
    }

    private fun currentSettings(): BoostSettings = BoostSettings(
        enabled = binding.switchEnabled.isChecked,
        autoCarMode = binding.switchAutoCar.isChecked,
        boostPercent = binding.sliderBoost.value.toInt(),
        bassPercent = binding.sliderBass.value.toInt(),
        legacyMode = binding.switchLegacy.isChecked,
        enhancedDetection = binding.switchEnhanced.isChecked,
        startOnBoot = binding.switchBoot.isChecked
    )

    private fun updateValueLabels(settings: BoostSettings) {
        binding.textBoostValue.text = getString(
            R.string.boost_value,
            settings.boostPercent,
            settings.boostDecibels()
        )
        binding.textBassValue.text = getString(R.string.percent_value, settings.bassPercent)
    }

    private fun persistAndApply() {
        val settings = currentSettings()
        prefs.save(settings)
        if (settings.enabled) {
            ensurePermissionAndStart()
        } else {
            BoostService.stop(this)
        }
        refreshStatus()
    }

    private fun saveAndReloadService(settings: BoostSettings) {
        prefs.save(settings)
        if (settings.enabled) {
            reloadService()
        }
        refreshStatus()
    }

    private fun reloadService() {
        val intent = Intent(this, BoostService::class.java).setAction(BoostService.ACTION_RELOAD)
        startForegroundService(intent)
    }

    private fun ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED -> startBoosting(showToast = true)
                else -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startBoosting(showToast = true)
        }
    }

    private fun startBoosting(showToast: Boolean) {
        reloadService()
        if (showToast) {
            Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val output = OutputMonitor.current(this)
        binding.textOutput.text = getString(R.string.output_label, output.label)
        binding.textDumpStatus.text = if (OutputMonitor.hasDumpPermission(this)) {
            getString(R.string.dump_granted)
        } else {
            getString(R.string.dump_missing)
        }
        binding.textHint.text = when (output.kind) {
            OutputKind.USB -> getString(R.string.hint_usb)
            OutputKind.BLUETOOTH -> getString(R.string.hint_bluetooth)
            else -> getString(R.string.hint_phone)
        }
    }
}
