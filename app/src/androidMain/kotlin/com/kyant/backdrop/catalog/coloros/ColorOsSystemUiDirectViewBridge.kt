package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.view.View
import android.widget.ImageView
import java.lang.reflect.Method

/**
 * Direct ordinary-View probes for SystemUI material implementations that do not require a
 * SystemUI controller or SurfaceControl to construct.
 *
 * These are deliberately separate from the capability inventory: a PASS here means the actual
 * class from the installed SystemUI package was instantiated and its real API was invoked.
 */
internal class ColorOsSystemUiDirectViewBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val QS_PROGRESSIVE_BLUR = "com.oplus.systemui.qs.media.ProgressiveBlurOverlay"
        private const val NOTIFICATION_TILT_SHIFT = "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer"
        private const val KEYGUARD_GRADIENT_BLUR = "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView"
        private const val QS_MULTI_LIGHT = "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams"
    }

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader = systemUiContext.classLoader

    fun createQsProgressiveBlur(progress: Float): Result<View> = runCatching {
        val view = constructView(QS_PROGRESSIVE_BLUR)
        invokeRequired(view, "setBlurProgress", progress.coerceIn(0f, 1f))
        view
    }

    fun updateQsProgressiveBlur(view: View, progress: Float): Result<Unit> = runCatching {
        require(view.javaClass.name == QS_PROGRESSIVE_BLUR)
        invokeRequired(view, "setBlurProgress", progress.coerceIn(0f, 1f))
        view.invalidate()
    }

    fun createNotificationTiltShift(width: Int, height: Int): Result<View> = runCatching {
        val view = constructView(NOTIFICATION_TILT_SHIFT)
        if (width > 0 && height > 0) invokeOptional(view, "updateTiltShiftBlurtSize", width, height)
        invokeRequired(view, "setMaterialBlur")
        view
    }

    fun updateNotificationTiltShift(view: View, width: Int, height: Int): Result<Unit> = runCatching {
        require(view.javaClass.name == NOTIFICATION_TILT_SHIFT)
        if (width > 0 && height > 0) invokeOptional(view, "updateTiltShiftBlurtSize", width, height)
        invokeRequired(view, "setMaterialBlur")
        view.invalidate()
    }

    fun resetNotificationTiltShift(view: View) {
        runCatching { invokeOptional(view, "resetMaterialBlur") }
    }

    fun createKeyguardGradientBlur(bitmap: Bitmap, amount: Float = 1f): Result<View> = runCatching {
        val view = constructView(KEYGUARD_GRADIENT_BLUR)
        require(view is ImageView) { "$KEYGUARD_GRADIENT_BLUR is not ImageView" }
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.setImageBitmap(bitmap)
        invokeOptional(view, "setAvailableState", true)
        invokeRequired(view, "showBlurMask", 0L, amount.coerceIn(0f, 1f), true)
        view
    }

    fun updateKeyguardGradientBlur(view: View, bitmap: Bitmap, amount: Float): Result<Unit> = runCatching {
        require(view.javaClass.name == KEYGUARD_GRADIENT_BLUR)
        require(view is ImageView)
        view.setImageBitmap(bitmap)
        invokeOptional(view, "setAvailableState", true)
        invokeRequired(view, "showBlurMask", 0L, amount.coerceIn(0f, 1f), true)
        view.invalidate()
    }

    /**
     * Returns the real QS MultiLight RuntimeShader. Geometry is supplied by the demo, while the
     * shader source, default optical recipe and update logic remain SystemUI-owned.
     */
    fun createQsMultiLightShader(width: Int, height: Int, radiusPx: Float): Result<RuntimeShader> = runCatching {
        require(width > 0 && height > 0)
        val clazz = load(QS_MULTI_LIGHT)
        val params = clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invokeOptional(params, "setResolution", width.toFloat(), height.toFloat())
        invokeOptional(params, "setSize", width * 0.92f, height * 0.88f)
        invokeOptional(params, "setPosition", width * 0.5f, height * 0.5f)
        invokeOptional(params, "setCornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)
        invokeOptional(params, "setWeight", 1f)
        invokeOptional(params, "setEdgeEnabled", true)
        invokeOptional(params, "setShadowEnabled", true)
        invokeOptional(params, "setCircleEnabled", false)
        invokeOptional(params, "flushIfDirty", true, true)
        val shader = invokeRequired(params, "getRuntimeShader")
        require(shader is RuntimeShader) { "getRuntimeShader returned ${shader?.javaClass?.name}" }
        shader
    }

    fun diagnostics(): List<String> = listOf(
        classStatus(QS_PROGRESSIVE_BLUR),
        classStatus(NOTIFICATION_TILT_SHIFT),
        classStatus(KEYGUARD_GRADIENT_BLUR),
        classStatus(QS_MULTI_LIGHT),
    )

    private fun constructView(className: String): View {
        val clazz = load(className)
        val ctor = clazz.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val value = ctor.newInstance(systemUiContext)
        require(value is View) { "$className is not android.view.View" }
        return value
    }

    private fun invokeRequired(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findCompatible(receiver.javaClass, name, args)
            ?: error("${receiver.javaClass.name}.$name(${args.size}) not found")
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun invokeOptional(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findCompatible(receiver.javaClass, name, args) ?: return null
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun findCompatible(clazz: Class<*>, name: String, args: Array<out Any?>): Method? =
        clazz.declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.parameterCount == args.size &&
                method.parameterTypes.zip(args).all { (type, arg) -> compatible(type, arg) }
        }

    private fun compatible(type: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !type.isPrimitive
        return box(type).isAssignableFrom(arg.javaClass)
    }

    private fun box(type: Class<*>): Class<*> = when (type) {
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

    private fun classStatus(name: String): String = runCatching {
        load(name)
        "$name=available"
    }.getOrElse { "$name=unavailable:${describe(it)}" }

    private fun load(name: String): Class<*> = loader.loadClass(name)

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
