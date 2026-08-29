package com.usbboost.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.usbboost.app.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BoostPrefs
    private var pendingEnable = false
    private var suppressEq = false
    private var pendingUpdate: UpdateInfo? = null
    private val io = Executors.newSingleThreadExecutor()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            refreshStatus()
            syncToggle()
            refreshHandler.postDelayed(this, STATUS_POLL_MS)
        }
    }

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
        OutputWatcher.start(this)
        bindUi()
        if (intent.getBooleanExtra(EXTRA_ENABLE, false)) {
            setBoostEnabled(true)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ENABLE, false)) {
            setBoostEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()
        val settings = prefs.load()
        if (settings.enabled || settings.autoOnUsb) {
            runCatching { BoostEngine.start(this) }
            BoostService.startSafely(this)
        }
        loadControls(prefs.load())
        refreshStatus()
        syncToggle()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun bindUi() {
        loadControls(prefs.load())
        syncToggle()

        binding.buttonOn.setOnClickListener { setBoostEnabled(true) }
        binding.buttonOff.setOnClickListener { setBoostEnabled(false) }
        binding.buttonLockOn.setOnClickListener { MediaNudge.lockOn(this) }
        binding.buttonUpdate.setOnClickListener { onUpdateClicked() }

        binding.switchAutoCar.setOnCheckedChangeListener { _, _ -> persistAndApply() }
        binding.switchAutoUsb.setOnCheckedChangeListener { _, checked ->
            persistAndApply()
            if (checked) BoostService.startSafely(this)
        }

        bindBoostSlider()
        bindEqSlider(binding.sliderEqBass) { eq, v -> eq.copy(bass = v) }
        bindEqSlider(binding.sliderEqLowMid) { eq, v -> eq.copy(lowMid = v) }
        bindEqSlider(binding.sliderEqMid) { eq, v -> eq.copy(mid = v) }
        bindEqSlider(binding.sliderEqPresence) { eq, v -> eq.copy(presence = v) }
        bindEqSlider(binding.sliderEqTreble) { eq, v -> eq.copy(treble = v) }

        binding.chipsPreset.setOnCheckedStateChangeListener { _, _ ->
            if (suppressEq) return@setOnCheckedStateChangeListener
            val preset = selectedPreset()
            val eq = if (preset == SoundPreset.CUSTOM) currentEqFromSliders() else EqShapes.forPreset(preset)
            suppressEq = true
            applyEqToSliders(eq)
            suppressEq = false
            persistAndApply(preset = preset, eq = eq)
        }
    }

    private fun bindBoostSlider() {
        binding.sliderBoost.addOnChangeListener { _, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            persistAndApply()
        }
    }

    private fun bindEqSlider(slider: Slider, update: (EqBands, Int) -> EqBands) {
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || suppressEq) return@addOnChangeListener
            suppressEq = true
            binding.chipCustom.isChecked = true
            suppressEq = false
            persistAndApply(preset = SoundPreset.CUSTOM, eq = update(currentEqFromSliders(), value.toInt()))
        }
    }

    private fun loadControls(settings: BoostSettings) {
        suppressEq = true
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchAutoCar.isChecked = settings.autoCarMode
        binding.switchAutoUsb.isChecked = settings.autoOnUsb
        binding.sliderBoost.value = settings.boostPercent.toFloat()
        val eq = settings.resolvedEq()
        applyEqToSliders(eq)
        when (settings.preset) {
            SoundPreset.FLAT -> binding.chipFlat.isChecked = true
            SoundPreset.PODCAST -> binding.chipPodcast.isChecked = true
            SoundPreset.ROCK -> binding.chipRock.isChecked = true
            SoundPreset.COUNTRY -> binding.chipCountry.isChecked = true
            SoundPreset.CUSTOM -> binding.chipCustom.isChecked = true
        }
        suppressEq = false
        updateValueLabels(settings.copy(eq = eq))
    }

    private fun applyEqToSliders(eq: EqBands) {
        binding.sliderEqBass.value = eq.bass.toFloat()
        binding.sliderEqLowMid.value = eq.lowMid.toFloat()
        binding.sliderEqMid.value = eq.mid.toFloat()
        binding.sliderEqPresence.value = eq.presence.toFloat()
        binding.sliderEqTreble.value = eq.treble.toFloat()
        updateEqLabels(eq)
    }

    private fun currentEqFromSliders(): EqBands = EqBands(
        bass = binding.sliderEqBass.value.toInt(),
        lowMid = binding.sliderEqLowMid.value.toInt(),
        mid = binding.sliderEqMid.value.toInt(),
        presence = binding.sliderEqPresence.value.toInt(),
        treble = binding.sliderEqTreble.value.toInt()
    ).coerced()

    private fun selectedPreset(): SoundPreset = when (binding.chipsPreset.checkedChipId) {
        R.id.chipPodcast -> SoundPreset.PODCAST
        R.id.chipRock -> SoundPreset.ROCK
        R.id.chipCountry -> SoundPreset.COUNTRY
        R.id.chipCustom -> SoundPreset.CUSTOM
        else -> SoundPreset.FLAT
    }

    private fun persistAndApply(
        preset: SoundPreset = selectedPreset(),
        eq: EqBands = currentEqFromSliders()
    ) {
        val car = OutputWatcher.carActive(this)
        val updated = currentSettings(preset, eq).writeBack(car)
        prefs.save(updated)
        updateValueLabels(updated)
        if (updated.enabled || updated.autoOnUsb) {
            runCatching { BoostEngine.start(this) }
            BoostService.startSafely(this)
        }
        refreshStatus()
        syncToggle()
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
            val car = OutputWatcher.carActive(this)
            val updated = currentSettings().copy(enabled = false).writeBack(car)
            prefs.save(updated)
            binding.switchEnabled.isChecked = false
            if (updated.autoOnUsb) {
                BoostEngine.stop()
                BoostService.startSafely(this)
            } else {
                BoostService.stop(this)
            }
            syncToggle()
            refreshStatus()
        }
    }

    private fun applyBoostOn() {
        val car = OutputWatcher.carActive(this)
        val updated = currentSettings().copy(enabled = true).writeBack(car)
        prefs.save(updated)
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
        val attach = BoostEngine.currentStatus()
        val car = OutputWatcher.carActive(this)
        val applying = BoostLogic.shouldApplyEffects(settings.enabled, settings.autoCarMode, car)
        binding.buttonOn.isEnabled = !settings.enabled
        binding.buttonOff.isEnabled = settings.enabled
        binding.textPowerStatus.text = when {
            !settings.enabled -> getString(R.string.boost_is_off)
            !applying -> getString(R.string.boost_waiting_car)
            attach.lockedOn -> getString(R.string.boost_is_on)
            else -> getString(R.string.boost_searching)
        }
    }

    private fun refreshStatus() {
        val output = OutputMonitor.current(this)
        val settings = prefs.load()
        val attach = BoostEngine.currentStatus()
        val car = OutputWatcher.carActive(this)
        val applying = BoostLogic.shouldApplyEffects(settings.enabled, settings.autoCarMode, car)
        binding.textOutput.text = getString(R.string.output_label, output.label)
        binding.textProfile.text = getString(if (car) R.string.profile_car else R.string.profile_home)
        binding.textHint.text = when {
            !settings.enabled -> getString(R.string.hint_off)
            !applying -> getString(R.string.hint_waiting_car)
            attach.lockedOn && output.kind == OutputKind.USB -> getString(R.string.hint_usb)
            attach.lockedOn && output.kind == OutputKind.BLUETOOTH -> getString(R.string.hint_bluetooth)
            attach.lockedOn -> getString(R.string.hint_phone_boosting)
            attach.musicPlaying || SessionTracker.musicPlaying(this) -> getString(R.string.hint_pause_play)
            else -> getString(R.string.hint_need_music)
        }
    }

    private fun currentSettings(
        preset: SoundPreset = selectedPreset(),
        eq: EqBands = currentEqFromSliders()
    ): BoostSettings {
        val stored = prefs.load()
        return stored.copy(
            enabled = binding.switchEnabled.isChecked,
            autoCarMode = binding.switchAutoCar.isChecked,
            autoOnUsb = binding.switchAutoUsb.isChecked,
            boostPercent = binding.sliderBoost.value.toInt(),
            preset = preset,
            eq = if (preset == SoundPreset.CUSTOM) eq else EqShapes.forPreset(preset),
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
        updateEqLabels(settings.resolvedEq())
    }

    private fun updateEqLabels(eq: EqBands) {
        fun label(view: TextView, name: Int, db: Int) {
            val shown = if (db > 0) "+$db" else "$db"
            view.text = getString(R.string.eq_db, getString(name), shown)
        }
        label(binding.textEqBass, R.string.eq_bass, eq.bass)
        label(binding.textEqLowMid, R.string.eq_low_mid, eq.lowMid)
        label(binding.textEqMid, R.string.eq_mid, eq.mid)
        label(binding.textEqPresence, R.string.eq_presence, eq.presence)
        label(binding.textEqTreble, R.string.eq_treble, eq.treble)
    }

    private fun onUpdateClicked() {
        val pending = pendingUpdate
        if (pending != null) {
            installUpdate(pending)
            return
        }
        binding.textUpdate.text = getString(R.string.update_checking)
        io.execute {
            val info = AppUpdater.check(BuildConfig.VERSION_CODE)
            runOnUiThread {
                if (info == null) {
                    binding.textUpdate.text = getString(R.string.update_none)
                } else {
                    pendingUpdate = info
                    binding.textUpdate.text = getString(R.string.update_found, info.versionName)
                    binding.buttonUpdate.text = getString(R.string.update_found, info.versionName)
                }
            }
        }
    }

    private fun installUpdate(info: UpdateInfo) {
        if (AppUpdater.needsInstallPermission(this)) {
            startActivity(AppUpdater.installPermissionIntent(this))
            binding.textUpdate.text = getString(R.string.update_allow_install)
            return
        }
        binding.textUpdate.text = getString(R.string.update_downloading)
        io.execute {
            runCatching {
                val apk = File(cacheDir, "usb-boost-update.apk")
                AppUpdater.download(info.apkUrl, apk)
                apk
            }.onSuccess { apk ->
                runOnUiThread { startActivity(AppUpdater.installIntent(this, apk)) }
            }.onFailure {
                runOnUiThread { binding.textUpdate.text = getString(R.string.update_failed) }
            }
        }
    }

    companion object {
        const val EXTRA_ENABLE = "enable"
        private const val STATUS_POLL_MS = 1000L
    }
}
