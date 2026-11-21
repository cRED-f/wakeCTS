package com.example.ctsbutton

import android.content.Context
import android.content.Intent

/** A single state transition for the in-app button, overlay and Quick Settings. */
object WakeStateController {
    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.setWakeEnabled(context, enabled)
        context.sendBroadcast(
            Intent(AssistantButtonService.ACTION_TOGGLE_BUTTON).setPackage(context.packageName)
        )
        notifyStateChanged(context)
    }

    fun notifyStateChanged(context: Context) {
        context.sendBroadcast(
            Intent(AssistantButtonService.ACTION_STATE_CHANGED).setPackage(context.packageName)
        )
        WakeQuickSettingsTile.requestSync(context)
    }
}
