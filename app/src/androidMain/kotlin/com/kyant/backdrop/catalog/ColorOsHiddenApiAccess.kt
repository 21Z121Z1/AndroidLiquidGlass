package com.kyant.backdrop.catalog

import java.lang.reflect.Method

/**
 * Enables the narrow hidden-API surface required by the ColorOS glass builder.
 *
 * Android 17 still exposes VMDebug.allowHiddenApiReflectionFrom() for
 * debuggable processes. It exempts only the supplied class, which lets the
 * vendor helper perform its own RenderEffect reflection without changing the
 * device-wide hidden-API policy.
 */
object ColorOsHiddenApiAccess {
    private const val RENDER_EFFECT_PREFIX = "Landroid/graphics/RenderEffect;"
    private const val VMDEBUG_CLASS = "dalvik.system.VMDebug"

    @Volatile
    private var lastAttempt: Result<String>? = null

    fun enable(vararg classes: Class<*>): Result<String> {
        val targets = if (classes.isEmpty()) {
            arrayOf(ColorOsHiddenApiAccess::class.java)
        } else {
            classes
        }

        val result = runCatching {
            val vmDebugClass = Class.forName(VMDEBUG_CLASS)
            val allow = findDeclaredMethod(
                vmDebugClass,
                "allowHiddenApiReflectionFrom",
                Class::class.java
            ).accessible()
            targets.forEach { allow.invoke(null, it) }
            "vmd-exempted:${targets.joinToString(",") { it.name }}"
        }.recoverCatching { vmDebugFailure ->
            // Keep compatibility with older Android releases where VMDebug is
            // absent or unavailable, while retaining the narrow RenderEffect
            // prefix used by the legacy VMRuntime route.
            runCatching {
                val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
                val getRuntime = findDeclaredMethod(vmRuntimeClass, "getRuntime").accessible()
                val runtime = getRuntime.invoke(null)
                val setHiddenApiExemptions = findDeclaredMethod(
                    vmRuntimeClass,
                    "setHiddenApiExemptions",
                    Array<String>::class.java
                )
                    .accessible()
                setHiddenApiExemptions.invoke(runtime, arrayOf(RENDER_EFFECT_PREFIX))
                "vmruntime-exempted:$RENDER_EFFECT_PREFIX"
            }.getOrElse { vmRuntimeFailure ->
                throw IllegalStateException(
                    "VMDebug=${describe(vmDebugFailure)}; VMRuntime=${describe(vmRuntimeFailure)}",
                    vmRuntimeFailure
                )
            }
        }

        lastAttempt = result
        return result
    }

    fun diagnostics(): String = lastAttempt?.fold(
        onSuccess = { "hiddenApiExemption=$it" },
        onFailure = { "hiddenApiExemption=failed:${describe(it)}" }
    ) ?: diagnosticsFor(enable())

    private fun diagnosticsFor(result: Result<String>): String = result.fold(
        onSuccess = { "hiddenApiExemption=$it" },
        onFailure = { "hiddenApiExemption=failed:${describe(it)}" }
    )

    private fun Method.accessible(): Method = apply { isAccessible = true }

    private fun findDeclaredMethod(
        type: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>
    ): Method = runCatching {
        type.getDeclaredMethod(name, *parameterTypes)
    }.getOrElse { directLookupFailure ->
        type.declaredMethods.firstOrNull { method ->
            method.name == name && method.parameterTypes.contentEquals(parameterTypes)
        } ?: throw directLookupFailure
    }

    private fun describe(throwable: Throwable): String {
        val root = generateSequence(throwable) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
