package com.example.ctsbutton

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

object CtsTrigger {

    private const val TAG = "CtsButton"

    /**
     * MiCTS uses entry point 1 for its Home-button long-press trigger.
     */
    private const val CTS_ENTRY_POINT = 1

    @SuppressLint("PrivateApi")
    fun triggerCircleToSearch(): Boolean {
        return runCatching {
            val bundle = Bundle().apply {
                putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                putInt("omni.entry_point", CTS_ENTRY_POINT)
                putBoolean("cts_trigger", true)
            }

            val vimsClass = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService"
            )

            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "voiceinteraction") as IBinder

            val vims = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService\$Stub"
            ).getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)

            val result = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                HiddenApiBypass.invoke(
                    vimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    bundle,
                    7,
                    "hyperOS_home"
                ) as Boolean
            } else {
                HiddenApiBypass.invoke(
                    vimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    bundle,
                    7
                ) as Boolean
            }

            result
        }.onFailure { error ->
            Log.e(TAG, "Circle to Search trigger failed", error)
        }.getOrDefault(false)
    }
}
