package com.example.ctsbutton

import android.content.res.Resources
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

var module: CtsTriggerModule? = null

class CtsTriggerModule : XposedModule() {

    private var contextualSearchKey = 0
    private var contextualSearchPackageName = 0

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        module = this
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)

        runCatching {
            hookVoiceInteractionManagerService(param)
        }.onFailure { error ->
            log(
                Log.ERROR,
                "CtsButton",
                "Failed to hook VoiceInteractionManagerService",
                error
            )
        }
    }

    private fun hookVoiceInteractionManagerService(
        param: SystemServerStartingParam
    ) {
        val classLoader = param.classLoader

        val rStringClass = classLoader.loadClass(
            "com.android.internal.R\$string"
        )

        contextualSearchKey = rStringClass
            .getField("config_defaultContextualSearchKey")
            .getInt(null)

        contextualSearchPackageName = rStringClass
            .getField("config_defaultContextualSearchPackageName")
            .getInt(null)

        val vimsStubClass = classLoader.loadClass(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService\$VoiceInteractionManagerServiceStub"
        )

        val showSessionMethod = vimsStubClass.getDeclaredMethod(
            "showSessionFromSession",
            IBinder::class.java,
            Bundle::class.java,
            Int::class.java,
            String::class.java
        )

        hook(showSessionMethod).intercept(ShowSessionHooker())

        log(
            Log.INFO,
            "CtsButton",
            "VoiceInteractionManagerService hook installed"
        )
    }

    class ShowSessionHooker : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val bundle = chain.args[1] as? Bundle ?: return chain.proceed()

            if (!bundle.getBoolean("cts_trigger", false)) {
                return chain.proceed()
            }

            Binder.clearCallingIdentity()

            var resourceHook: XposedInterface.HookHandle? = null

            return try {
                resourceHook = module?.hook(
                    Resources::class.java.getDeclaredMethod(
                        "getString",
                        Int::class.java
                    )
                )?.intercept(GetStringHooker())

                chain.proceed()
            } finally {
                resourceHook?.unhook()
            }
        }
    }

    class GetStringHooker : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val resourceId = chain.args[0] as Int
            val currentModule = module ?: return chain.proceed()

            return when (resourceId) {
                currentModule.contextualSearchKey -> {
                    "omni.entry_point"
                }

                currentModule.contextualSearchPackageName -> {
                    "com.google.android.googlequicksearchbox"
                }

                else -> chain.proceed()
            }
        }
    }
}
