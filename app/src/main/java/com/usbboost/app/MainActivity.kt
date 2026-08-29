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
    private var askedNotifications = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* boost stays off until the user taps Turn on */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BoostPrefs(this)
        prefs.applyOutOfBoxDefaults()
        bindUi()
        requestNotificationsIfNeeded()
        // Do not start a foreground service from onCreate — that crashed the app on open.
    }

    override fun onResume() {
        super.onResume()
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
            if (prefs.load().enabled) BoostEngine.start(this)
            refreshStatus()
        }

        binding.sliderBoost.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            prefs.save(updated)
            if (updated.enabled) BoostEngine.start(this)
            refreshStatus()
        }
        binding.sliderBass.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val updated = currentSettings()
            updateValueLabels(updated)
            prefs.save(updated)
            if (updated.enabled) BoostEngine.start(this)
            refreshStatus()
        }
    }

    private fun setBoostEnabled(enabled: Boolean) {
        prefs.save(currentSettings().copy(enabled = enabled))
        binding.switchEnabled.isChecked = enabled
        if (enabled) {
            BoostEngine.start(this)
            BoostService.startSafely(this)
        } else {
            BoostService.stop(this)
        }
        syncToggle()
        refreshStatus()
    }

    private fun syncToggle() {
        val on = prefs.load().enabled
        binding.buttonOn.isEnabled = !on
        binding.buttonOff.isEnabled = on
        binding.textPowerStatus.text = getString(if (on) R.string.boost_is_on else R.string.boost_is_off)
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

    private fun requestNotificationsIfNeeded() {
        if (askedNotifications) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        askedNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun refreshStatus() {
        val output = OutputMonitor.current(this)
        val on = prefs.load().enabled
        binding.textOutput.text = getString(R.string.output_label, output.label)
        binding.textHint.text = when {
            !on -> getString(R.string.hint_off)
            output.kind == OutputKind.USB -> getString(R.string.hint_usb)
            output.kind == OutputKind.BLUETOOTH -> getString(R.string.hint_bluetooth)
            else -> getString(R.string.hint_phone)
        }
    }
}
