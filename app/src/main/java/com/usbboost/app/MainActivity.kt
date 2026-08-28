package com.usbboost.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.usbboost.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BoostPrefs

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startBoosting()
        maybeAskBatteryExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BoostPrefs(this)
        prefs.applyOutOfBoxDefaults()
        bindUi()
        startBoosting()
        maybeAskBatteryExemption()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (prefs.load().enabled) startBoosting()
    }

    private fun bindUi() {
        val settings = prefs.load()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchAutoCar.isChecked = settings.autoCarMode
        binding.sliderBoost.value = settings.boostPercent.toFloat()
        binding.sliderBass.value = settings.bassPercent.toFloat()
        updateValueLabels(settings)

        binding.switchEnabled.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchAutoCar.setOnCheckedChangeListener { _, _ -> persistAndApply() }

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
    }

    private fun currentSettings(): BoostSettings {
        val stored = prefs.load()
        return stored.copy(
            enabled = binding.switchEnabled.isChecked,
            autoCarMode = binding.switchAutoCar.isChecked,
            boostPercent = binding.sliderBoost.value.toInt(),
            bassPercent = binding.sliderBass.value.toInt(),
            legacyMode = true,
            startOnBoot = true
        )
    }

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
        if (settings.enabled) startBoosting() else BoostService.stop(this)
        refreshStatus()
    }

    private fun saveAndReloadService(settings: BoostSettings) {
        prefs.save(settings)
        if (settings.enabled) reloadService()
        refreshStatus()
    }

    private fun reloadService() {
        startForegroundService(
            Intent(this, BoostService::class.java).setAction(BoostService.ACTION_RELOAD)
        )
    }

    private fun startBoosting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        reloadService()
        refreshStatus()
    }

    private fun maybeAskBatteryExemption() {
        if (prefs.batteryPromptShown()) return
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            prefs.markBatteryPromptShown()
            return
        }
        prefs.markBatteryPromptShown()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    private fun refreshStatus() {
        val output = OutputMonitor.current(this)
        binding.textOutput.text = getString(R.string.output_label, output.label)
        binding.textHint.text = when (output.kind) {
            OutputKind.USB -> getString(R.string.hint_usb)
            OutputKind.BLUETOOTH -> getString(R.string.hint_bluetooth)
            else -> getString(R.string.hint_phone)
        }
    }
}
