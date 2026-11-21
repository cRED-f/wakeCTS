package com.example.ctsbutton

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import android.graphics.drawable.Icon

/** User-added Android Quick Settings toggle (not an iOS Control Center tile). */
class WakeQuickSettingsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        // Avoid changing an accessibility overlay while the device is locked.
        if (isLocked) {
            unlockAndRun { handleToggle() }
        } else {
            handleToggle()
        }
    }

    private fun handleToggle() {
        AppPreferences.load(this)
        // Without an enabled Accessibility service, the floating control cannot
        // appear. Open the app's existing setup controls rather than showing ON.
        if (!isAccessibilityReady()) {
            openAppForSetup()
            updateTileState()
            return
        }
        WakeStateController.setEnabled(this, !AppPreferences.isWakeEnabled)
        updateTileState()
    }

    private fun updateTileState() {
        AppPreferences.load(this)
        val enabled = AppPreferences.isWakeEnabled && isAccessibilityReady()
        qsTile?.apply {
            label = getString(R.string.quick_tile_label)
            icon = Icon.createWithResource(this@WakeQuickSettingsTile, R.drawable.ic_cts)
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (!isAccessibilityReady()) R.string.quick_tile_setup_needed
                    else if (enabled) R.string.quick_tile_on else R.string.quick_tile_off
                )
            }
            updateTile()
        }
    }

    private fun isAccessibilityReady(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(this, AssistantButtonService::class.java)
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { service ->
            service.resolveInfo?.serviceInfo?.let { info ->
                ComponentName(info.packageName, info.name) == component
            } ?: false
        }
    }

    private fun openAppForSetup() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestSync(context: Context) {
            // ACTIVE_TILE causes SystemUI to call onStartListening to pick up the
            // preference changed from the app or the ocean shutdown animation.
            runCatching {
                TileService.requestListeningState(
                    context.applicationContext,
                    ComponentName(context, WakeQuickSettingsTile::class.java)
                )
            }
        }
    }
}
