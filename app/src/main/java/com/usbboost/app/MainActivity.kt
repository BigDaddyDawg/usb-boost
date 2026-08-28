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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BoostPrefs

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startBoosting(showToast = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BoostPrefs(this)
        bindUi()
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
        binding.buttonCopyAdb.setOnClickListener {
            val cmd = "adb shell pm grant $packageName android.permission.DUMP"
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
            Toast.makeText(this, R.string.adb_copied, Toast.LENGTH_SHORT).show()
        }
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
