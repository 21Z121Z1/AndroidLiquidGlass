package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Color
import android.graphics.RuntimeShader
import java.lang.reflect.Method

/**
 * Direct bridge to the lock-screen notification capsule StrokeShader.
 *
 * The ColorOS class itself extends android.graphics.RuntimeShader. It is a
 * separate SystemUI implementation from both the generic post-effect
 * GradientStrokeLine module and the QS/volume stroke shaders, so the demo
 * executes it independently.
 */
internal class ColorOsNotificationStrokeBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val STROKE_SHADER =
            "com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeShader"
    }

    @Suppress("DEPRECATION")
    private val packageContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader: ClassLoader get() = packageContext.classLoader

    fun create(width: Int, height: Int, radiusPx: Float): Result<RuntimeShader> = runCatching {
        require(width > 0 && height > 0) { "invalid shader size" }
        val clazz = loader.loadClass(STROKE_SHADER)
        require(RuntimeShader::class.java.isAssignableFrom(clazz)) {
            "$STROKE_SHADER no longer extends RuntimeShader"
        }

        val instance = clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invoke(instance, "setResolution", width.toFloat(), height.toFloat())
        invoke(instance, "setSize", width.toFloat(), height.toFloat())
        invoke(instance, "setOffset", 0f, 0f)
        invoke(instance, "setCornerRadius", radiusPx.coerceAtLeast(0f))
        invoke(instance, "setWeight", 1f)
        invoke(instance, "setAntiAliasing", 1.25f)
        invoke(instance, "setAngle", 45f)
        invoke(instance, "setStrokeColor", Color.WHITE)

        // Isolated comparison values. These are deliberately not claimed as
        // the shipping notification preset; they exercise the shipping shader
        // with a visible near/far envelope.
        invoke(instance, "setNearStrokeStart", 0f)
        invoke(instance, "setNearStrokeWidth", 1.7f)
        invoke(instance, "setNearFadeStart", 0.14f)
        invoke(instance, "setNearFadeWidth", 0.34f)
        invoke(instance, "setNearAlpha", 0.82f)
        invoke(instance, "setFarStrokeStart", 0f)
        invoke(instance, "setFarStrokeWidth", 1.0f)
        invoke(instance, "setFarFadeStart", 0.08f)
        invoke(instance, "setFarFadeWidth", 0.26f)
        invoke(instance, "setFarAlpha", 0.24f)
        invoke(instance, "setStrokePower", 1f)
        invoke(instance, "setStrokePowerMix", 1f)

        // The setters update individual uniforms; force both packed arrays to
        // be rebuilt as the original SystemUI hosts do after geometry changes.
        invoke(instance, "updateCommonArray")
        invoke(instance, "updateEdgeArray")
        instance as RuntimeShader
    }

    private fun invoke(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findMethod(receiver.javaClass, name, args)
            ?: error("${receiver.javaClass.name}.$name(${args.size}) not found")
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun findMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterCount == args.size &&
                    method.parameterTypes.zip(args).all { (type, arg) -> compatible(type, arg) }
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun compatible(type: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !type.isPrimitive
        val boxed = when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            else -> type
        }
        return boxed.isAssignableFrom(arg.javaClass)
    }
}
