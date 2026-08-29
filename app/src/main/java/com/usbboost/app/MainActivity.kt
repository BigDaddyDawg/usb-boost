package com.usbboost.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.usbboost.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BoostPrefs
    private var pendingEnable = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (pendingEnable) {
            pendingEnable = false
            applyBoostOn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BoostPrefs(this)
        prefs.applyOutOfBoxDefaults()
        bindUi()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.load().enabled) {
            runCatching { BoostEngine.start(this) }
            BoostService.startSafely(this)
        }
        refreshStatus()
        syncToggle()
    }

    private fun bindUi() {
        val settings = prefs.load()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchAutoCar.isChecked = settings.autoCarMode
        binding.sliderBoost.value = settings.boostPercent.toFloat()
        binding.sliderBass.value = settings.bassPercent.toFloat()
        updateValueLabels(settings)
        syncToggle()

        binding.buttonOn.setOnClickListener { setBoostEnabled(true) }
        binding.buttonOff.setOnClickListener { setBoostEnabled(false) }

        binding.switchAutoCar.setOnCheckedChangeListener { _, _ ->
            prefs.save(currentSettings())
            if (prefs.load().enabled) runCatching { BoostEngine.start(this) }
            refreshStatus()
        }

        binding.sliderBoost.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            prefs.save(updated)
            if (updated.enabled) runCatching { BoostEngine.start(this) }
            refreshStatus()
        }
        binding.sliderBass.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            prefs.save(updated)
            if (updated.enabled) runCatching { BoostEngine.start(this) }
            refreshStatus()
        }
    }

    private fun setBoostEnabled(enabled: Boolean) {
        if (enabled) {
            if (needsNotificationPermission()) {
                pendingEnable = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            applyBoostOn()
        } else {
            prefs.save(currentSettings().copy(enabled = false))
            binding.switchEnabled.isChecked = false
            BoostService.stop(this)
            syncToggle()
            refreshStatus()
        }
    }

    private fun applyBoostOn() {
        prefs.save(currentSettings().copy(enabled = true))
        binding.switchEnabled.isChecked = true
        runCatching { BoostEngine.start(this) }
        BoostService.startSafely(this)
        syncToggle()
        refreshStatus()
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    private fun syncToggle() {
        val settings = prefs.load()
        val output = OutputMonitor.current(this)
        val applying = BoostLogic.shouldApplyEffects(
            settings.enabled,
            settings.autoCarMode,
            output.carLikely || output.kind == OutputKind.USB
        )
        binding.buttonOn.isEnabled = !settings.enabled
        binding.buttonOff.isEnabled = settings.enabled
        binding.textPowerStatus.text = when {
            !settings.enabled -> getString(R.string.boost_is_off)
            applying -> getString(R.string.boost_is_on)
            else -> getString(R.string.boost_waiting_car)
        }
    }

    private fun refreshStatus() {
        val output = OutputMonitor.current(this)
        val settings = prefs.load()
        val applying = BoostLogic.shouldApplyEffects(
            settings.enabled,
            settings.autoCarMode,
            output.carLikely || output.kind == OutputKind.USB
        )
        binding.textOutput.text = getString(R.string.output_label, output.label)
        binding.textHint.text = when {
            !settings.enabled -> getString(R.string.hint_off)
            !applying -> getString(R.string.hint_waiting_car)
            output.kind == OutputKind.USB -> getString(R.string.hint_usb)
            output.kind == OutputKind.BLUETOOTH -> getString(R.string.hint_bluetooth)
            else -> getString(R.string.hint_phone_boosting)
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
}
