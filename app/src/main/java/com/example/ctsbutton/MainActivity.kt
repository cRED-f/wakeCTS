package com.example.ctsbutton

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.example.ctsbutton.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppPreferences.load(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.load(this)
        refreshUi()
    }

    private fun setupListeners() {
        binding.enableServiceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
