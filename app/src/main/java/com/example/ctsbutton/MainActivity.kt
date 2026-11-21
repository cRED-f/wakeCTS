package com.example.ctsbutton

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ctsbutton.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var stateReceiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppPreferences.load(this@MainActivity)
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppPreferences.load(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        binding.scrollContainer.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.glassBackdrop.setScrollOffset(scrollY)
        }
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (!stateReceiverRegistered) {
            ContextCompat.registerReceiver(
                this, stateReceiver,
                IntentFilter(AssistantButtonService.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            stateReceiverRegistered = true
        }
        AppPreferences.load(this)
        refreshUi()
        WakeQuickSettingsTile.requestSync(this)
    }

    override fun onPause() {
        if (stateReceiverRegistered) {
            unregisterReceiver(stateReceiver)
            stateReceiverRegistered = false
        }
        super.onPause()
    }

    private fun setupListeners() {
        binding.enableServiceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.powerButton.setOnClickListener {
            setWakeEnabled(!AppPreferences.isWakeEnabled)
        }
        binding.powerButton.setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> button.animate().scaleX(0.975f).scaleY(0.975f)
                    .setDuration(120L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    button.animate().scaleX(1f).scaleY(1f).setDuration(170L).start()
            }
            false // MaterialButton retains its ripple, click and accessibility behavior.
        }

        binding.wakeSwitch.setOnCheckedChangeListener { _, checked ->
            if (AppPreferences.isWakeEnabled != checked) setWakeEnabled(checked)
        }

        binding.fallbackSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setFallbackEnabled(this, checked)
        }

        binding.hapticSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setHapticEnabled(this, checked)
        }

        binding.sideButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val onLeft = checkedId == R.id.leftSideButton
                AppPreferences.setButtonLeft(this, onLeft)

                Intent(AssistantButtonService.ACTION_UPDATE_SETTINGS).also {
                    it.setPackage(packageName)
                    sendBroadcast(it)
                }
            }
        }

        binding.testTriggerButton.setOnClickListener {
            val triggered = CtsTrigger.triggerCircleToSearch()

            val message = if (triggered) {
                getString(R.string.cts_trigger_success)
            } else {
                getString(R.string.cts_trigger_failed)
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }

        binding.helpButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setWakeEnabled(enabled: Boolean) {
        WakeStateController.setEnabled(this, enabled)
        refreshUi()
    }

    private fun refreshUi() {
        val serviceEnabled = isAccessibilityServiceEnabled()

        binding.statusChip.text = if (serviceEnabled) {
            getString(R.string.service_enabled)
        } else {
            getString(R.string.service_disabled)
        }

        binding.enableServiceButton.text = if (serviceEnabled) {
            getString(R.string.open_accessibility_settings)
        } else {
            getString(R.string.enable_service)
        }

        binding.wakeSwitch.isChecked = AppPreferences.isWakeEnabled
        binding.heroStatus.setText(
            if (AppPreferences.isWakeEnabled) R.string.status_active else R.string.status_paused
        )
        binding.powerButton.setText(
            if (AppPreferences.isWakeEnabled) R.string.turn_off_wakects
            else R.string.turn_on_wakects
        )
        binding.powerButton.contentDescription = binding.powerButton.text
        binding.fallbackSwitch.isChecked = AppPreferences.isFallbackEnabled
        binding.hapticSwitch.isChecked = AppPreferences.isHapticEnabled

        binding.sideButtonGroup.check(
            if (AppPreferences.isButtonLeft) {
                R.id.leftSideButton
            } else {
                R.id.rightSideButton
            }
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager =
            getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val enabledServices =
            accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )

        val expectedService =
            "$packageName/${AssistantButtonService::class.java.canonicalName}"

        return enabledServices.any { service ->
            service.resolveInfo?.serviceInfo?.let { info ->
                "${info.packageName}/${info.name}" == expectedService
            } ?: false
        }
    }
}