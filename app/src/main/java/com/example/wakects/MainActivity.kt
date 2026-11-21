package com.example.wakects

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = getString(
            if (enabled) R.string.service_enabled else R.string.service_disabled
        )
        enableButton.text = getString(
            if (enabled) R.string.open_accessibility_settings else R.string.enable_service
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager =
            getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val expectedName = AssistantButtonService::class.java.name
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { service ->
            val info = service.resolveInfo.serviceInfo
            info.packageName == packageName && info.name == expectedName
        }
    }
}
