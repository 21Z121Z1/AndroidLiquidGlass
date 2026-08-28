package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Executes interactive SystemUI material effects whose shipping API is already View/Canvas based.
 *
 * No shader source or visual parameter is reconstructed here. The installed SystemUI classes own
 * the drawable, touch state, animation and light recipe; this bridge only supplies a small ordinary
 * View host so those APIs can be exercised outside their production controller hierarchy.
 */
internal class ColorOsSystemUiInteractiveEffectBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        const val NOTIFICATION_SPOTLIGHT =
            "com.oplus.systemui.notification.material.NotificationSpotLightDelegate"
        const val NOTIFICATION_SPOTLIGHT_KIND =
            "com.oplus.systemui.notification.material.NotificationSpotLightParamsKind"
        const val QS_MEDIA_SPOTLIGHT =
            "com.oplus.systemui.qs.media.QsMediaSpotLightHelper"
        const val QS_MEDIA_CLIP_SHAPE =
            "com.oplus.systemui.qs.media.QsMediaSpotLightHelper\$ClipShape"
        const val SHARED_SPOTLIGHT =
            "com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect"
        const val VOLUME_SETTINGS_SPOTLIGHT =
            "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSettingsButtonSpotLightHelper"
        const val METABALL_LIGHT_RENDERER =
            "com.oplusos.systemui.common.shader.MetaballLightRenderer"
        const val SCENARIO_LIGHT_DRAWABLE =
            "com.oplus.systemui.scenario.host.impl.domain.widget.ScenarioLightBackgroundDrawable"
        private const val TILE_DRAWABLE_WRAPPER =
            "com.oplus.systemui.qs.base.res.drawable.TileDrawableWrapper"
    }

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader = systemUiContext.classLoader

    fun notificationSpotLightKinds(): List<String> = enumNames(NOTIFICATION_SPOTLIGHT_KIND)

    /**
     * CAPSULE/CIRCLE are QsMediaSpotLightHelper shipping states. SHARED is a direct probe of the
     * separate SharedSpotLightEffect static API discovered in the same SystemUI build.
     */
    fun qsMediaClipShapes(): List<String> = enumNames(QS_MEDIA_CLIP_SHAPE) + "SHARED"

    fun createNotificationSpotLight(kindName: String): Result<View> = runCatching {
        val delegate = load(NOTIFICATION_SPOTLIGHT)
        val kind = enumValue(NOTIFICATION_SPOTLIGHT_KIND, kindName)
        val host = EffectHostView(systemUiContext)
        invokeStaticRequired(delegate, "markLazyParamsKind", host, kind)
        host.drawEffect = { canvas -> invokeStaticRequired(delegate, "draw", host, canvas) }
        host.touchEffect = { event -> invokeStaticRequired(delegate, "onTouchEvent", host, event) }
        host.detachEffect = { invokeStaticOptional(delegate, "suppressUntilPointerUp", host, true) }
        host
    }

    fun createQsMediaSpotLight(clipShapeName: String): Result<View> {
        if (clipShapeName == "SHARED") return createSharedSpotLightEffect()
        return runCatching {
            val helperClass = load(QS_MEDIA_SPOTLIGHT)
            val clipClass = load(QS_MEDIA_CLIP_SHAPE)
            val clip = enumValue(QS_MEDIA_CLIP_SHAPE, clipShapeName)
            val host = EffectHostView(systemUiContext)
            val ctor = helperClass.getDeclaredConstructor(View::class.java, clipClass).apply { isAccessible = true }
            val helper = ctor.newInstance(host, clip)
            invokeRequired(helper, "ensureDrawable")
            invokeOptional(helper, "syncDrawableEnabledFromHost")
            host.sizeEffect = { w, h -> invokeRequired(helper, "updateDrawableBounds", w, h) }
            host.drawEffect = { canvas -> invokeRequired(helper, "drawSpotLightEffect", canvas) }
            host.touchEffect = { event -> invokeRequired(helper, "handleMotionEvent", event) }
            host.detachEffect = { invokeOptional(helper, "releaseDrawable") }
            host
        }
    }

    /**
     * Directly exercises SharedSpotLightEffect's shipping static API. DEX in the current build
     * exposes updateBounds(View,int,int), draw(View,Canvas), onTouchEvent(View,MotionEvent) and
     * detach(View,String), so no business View or reconstructed spotlight shader is required.
     */
    fun createSharedSpotLightEffect(): Result<View> = runCatching {
        val effect = load(SHARED_SPOTLIGHT)
        val host = EffectHostView(systemUiContext)
        host.sizeEffect = { w, h -> invokeStaticRequired(effect, "updateBounds", host, w, h) }
        host.drawEffect = { canvas -> invokeStaticRequired(effect, "draw", host, canvas) }
        host.touchEffect = { event -> invokeStaticRequired(effect, "onTouchEvent", host, event) }
        host.detachEffect = { invokeStaticOptional(effect, "detach", host, "AndroidLiquidGlass parity demo") }
        host
    }

    fun createVolumeSettingsButtonSpotLight(): Result<View> = runCatching {
        val helperClass = load(VOLUME_SETTINGS_SPOTLIGHT)
        val host = EffectHostView(systemUiContext)
        val helper = helperClass.getDeclaredConstructor(View::class.java)
            .apply { isAccessible = true }
            .newInstance(host)
        invokeRequired(helper, "ensureDrawable")
        invokeOptional(helper, "syncDrawableEnabledFromHost")
        host.sizeEffect = { w, h -> invokeRequired(helper, "updateDrawableBounds", w, h) }
        host.drawEffect = { canvas -> invokeRequired(helper, "drawSpotLightEffect", canvas) }
        host.touchEffect = { event -> invokeRequired(helper, "handleMotionEvent", event) }
        host.detachEffect = { invokeOptional(helper, "releaseDrawable") }
        host
    }

    /**
     * Executes MetaballLightRenderer through its shipping ScenarioLightBackgroundDrawable consumer.
     * The consumer creates the real MetaballLightConfig via its Companion and owns animation,
     * texture/shader resources and path integration. The demo only supplies a base Drawable host.
     */
    fun createScenarioMetaballLight(): Result<View> = runCatching {
        val host = EffectHostView(systemUiContext)
        val wrapperClass = load(TILE_DRAWABLE_WRAPPER)
        val baseDrawable = ColorDrawable(0x14000000)
        val wrapper = wrapperClass.getDeclaredConstructor(Drawable::class.java)
            .apply { isAccessible = true }
            .newInstance(baseDrawable)

        val scenarioClass = load(SCENARIO_LIGHT_DRAWABLE)
        val scenario = scenarioClass.getDeclaredConstructor(View::class.java, wrapperClass)
            .apply { isAccessible = true }
            .newInstance(host, wrapper)
        require(scenario is Drawable) { "$SCENARIO_LIGHT_DRAWABLE is not Drawable" }

        invokeOptional(scenario, "setAnimationEnable", true)
        invokeOptional(scenario, "setLightVisible", true)
        invokeOptional(scenario, "showLightFirstFrame")
        host.sizeEffect = { w, h -> scenario.setBounds(0, 0, w, h) }
        host.attachEffect = {
            invokeOptional(scenario, "setLightVisible", true)
            invokeOptional(scenario, "restartLight")
        }
        host.drawEffect = { canvas -> scenario.draw(canvas) }
        host.detachEffect = { invokeOptional(scenario, "dispose") }
        host
    }

    fun diagnostics(): List<String> = listOf(
        classStatus(NOTIFICATION_SPOTLIGHT),
        classStatus(NOTIFICATION_SPOTLIGHT_KIND),
        classStatus(QS_MEDIA_SPOTLIGHT),
        classStatus(QS_MEDIA_CLIP_SHAPE),
        classStatus(SHARED_SPOTLIGHT),
        classStatus(VOLUME_SETTINGS_SPOTLIGHT),
        classStatus(METABALL_LIGHT_RENDERER),
        classStatus(SCENARIO_LIGHT_DRAWABLE),
    )

    private fun enumNames(className: String): List<String> =
        load(className).enumConstants?.map { (it as Enum<*>).name }.orEmpty()

    private fun enumValue(className: String, name: String): Any {
        val clazz = load(className)
        return clazz.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
            ?: error("$className.$name unavailable")
    }

    private fun invokeRequired(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findCompatible(receiver.javaClass, name, args, requireStatic = false)
            ?: error("${receiver.javaClass.name}.$name(${args.size}) not found")
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun invokeOptional(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findCompatible(receiver.javaClass, name, args, requireStatic = false) ?: return null
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun invokeStaticRequired(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = findCompatible(clazz, name, args, requireStatic = true)
            ?: error("${clazz.name}.$name(${args.size}) static method not found")
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    private fun invokeStaticOptional(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = findCompatible(clazz, name, args, requireStatic = true) ?: return null
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    private fun findCompatible(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        requireStatic: Boolean,
    ): Method? = (clazz.declaredMethods.asSequence() + clazz.methods.asSequence())
        .distinct()
        .firstOrNull { method ->
            method.name == name &&
                Modifier.isStatic(method.modifiers) == requireStatic &&
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

    internal class EffectHostView(context: Context) : View(context) {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0AFFFFFF }
        internal var drawEffect: ((Canvas) -> Unit)? = null
        internal var touchEffect: ((MotionEvent) -> Unit)? = null
        internal var sizeEffect: ((Int, Int) -> Unit)? = null
        internal var attachEffect: (() -> Unit)? = null
        internal var detachEffect: (() -> Unit)? = null
        internal var onRuntimeStatus: ((String) -> Unit)? = null
        private var firstDrawPassed = false
        private var runtimeFailure: String? = null

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            isClickable = true
            isFocusable = true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            safe("attach") { attachEffect?.invoke() }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            safe("size") { sizeEffect?.invoke(w, h) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                minOf(width, height) * 0.18f,
                minOf(width, height) * 0.18f,
                basePaint,
            )
            safe("draw") { drawEffect?.invoke(canvas) }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            safe("touch") { touchEffect?.invoke(event) }
            invalidate()
            return true
        }

        override fun onDetachedFromWindow() {
            safe("detach") { detachEffect?.invoke() }
            super.onDetachedFromWindow()
        }

        private inline fun safe(stage: String, block: () -> Unit) {
            runCatching(block)
                .onSuccess {
                    if (stage == "draw" && !firstDrawPassed && runtimeFailure == null) {
                        firstDrawPassed = true
                        onRuntimeStatus?.invoke("PASS — real SystemUI interactive draw completed")
                    }
                }
                .onFailure {
                    val root = generateSequence(it) { error -> error.cause }.last()
                    runtimeFailure = "$stage:${root.javaClass.simpleName}:${root.message}"
                    onRuntimeStatus?.invoke("UNAVAILABLE — real SystemUI interactive $runtimeFailure")
                }
        }

        override fun toString(): String = runtimeFailure?.let { "SystemUiInteractiveEffect(failure=$it)" }
            ?: "SystemUiInteractiveEffect(active)"
    }
}
