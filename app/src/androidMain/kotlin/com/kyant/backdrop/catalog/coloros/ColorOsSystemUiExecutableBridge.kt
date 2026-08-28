package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Direct execution bridge for SystemUI material implementations that are not
 * covered by the generic COUI bridge or by keyguard GlassEffectBuilder.
 *
 * Shader/source text is always read from the installed com.android.systemui
 * package at runtime. No ColorOS shader source is copied into this project.
 */
internal class ColorOsSystemUiExecutableBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val BAR_GLOW_ASSET = "barglow.agsl"

        private const val QS_STROKE = "com.oplus.systemui.qs.base.widget.strokeshader.GradientStrokeShader"
        private const val QS_STROKE_CORNER = "$QS_STROKE\$CornerType"
        private const val VOLUME_STROKE = "com.oplus.systemui.volume.utils.material.VolumeGradientStrokeShader"
        private const val VOLUME_STROKE_CORNER = "$VOLUME_STROKE\$CornerType"
        private const val GRADIENT_STROKE_PARAMS = "com.oplus.posteffect.params.GradientStrokeLineParams"

        private const val META_BALL_PARAMS = "com.oplus.posteffect.params.MetaBallParams"
        private const val META_BALL_BALL_PARAMS = "$META_BALL_PARAMS\$BallParams"
        private const val META_BALL_BLEND_PARAMS = "$META_BALL_PARAMS\$BlendParams"

        private const val WALLPAPER_BLUR_DRAWABLE = "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable"
        private const val SHADER_BLEND_PARAM = "com.oplus.posteffect.agsl.ShaderBlendParam"
        private const val DRAWABLE_SHADER = "com.oplus.posteffect.agsl.DrawableShader"
    }

    private val hostContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val packageContextResult = runCatching {
        hostContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val packageContext: Context get() = packageContextResult.getOrThrow()
    private val loader: ClassLoader get() = packageContext.classLoader
    private val baseBridge = ColorOsSystemUiPostEffectBridge(context)

    private val barGlowSource by lazy {
        packageContext.assets.open(BAR_GLOW_ASSET).bufferedReader().use { it.readText() }
    }

    /**
     * Runs SystemUI's shipping barglow.agsl as a post-effect over the View.
     * The shader contains its own parabola/segment distance field, exponential
     * glow, chromatic split and input-gradient composition.
     */
    fun applyBarGlow(
        view: View,
        bend: Float = 0.055f,
        glowChromaticOffset: Float = 0.010f,
    ): Result<Unit> = runCatching {
        require(Build.VERSION.SDK_INT >= 33) { "RuntimeShader requires Android 13+" }
        require(view.width > 0 && view.height > 0) { "view is not laid out" }

        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val shader = RuntimeShader(barGlowSource)
        shader.setFloatUniform("bar_alpha", 0.72f)
        shader.setFloatUniform("glow_alpha", 0.92f)
        shader.setFloatUniform("gradient_alpha", 0.30f)
        shader.setFloatUniform("offsets_bar_gradient", 0f, 0f, 0f, 0f)
        shader.setFloatUniform("viewport", w, h)
        shader.setFloatUniform("texIn_dims", w, h)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("paraboladims", 0.38f, 0.06f, 0.0075f)
        shader.setFloatUniform("glowparams", glowChromaticOffset, 0.88f)
        shader.setFloatUniform("center", 0.50f, 0.54f)
        shader.setFloatUniform("glowcolor", 1f, 1f, 1f, 0.72f)
        shader.setFloatUniform("upShiftColor", 0.10f, 0.35f, 1.00f, 0.85f)
        shader.setFloatUniform("downShiftColor", 1.00f, 0.18f, 0.10f, 0.85f)
        view.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "texIn"))
        view.invalidate()
    }

    /**
     * Builds the QS-specific shipping GradientStrokeShader. We prime the
     * internal layout fields before setCorner(), because that method rebuilds
     * its common uniforms from the current layout snapshot.
     */
    fun createQsStrokeShader(width: Int, height: Int, radiusPx: Float): Result<RuntimeShader> = runCatching {
        require(width > 0 && height > 0)
        val stroke = load(QS_STROKE).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        setField(stroke, "viewWidth", width.toFloat())
        setField(stroke, "viewHeight", height.toFloat())
        setField(stroke, "drawWidth", width.toFloat())
        setField(stroke, "drawHeight", height.toFloat())
        setField(stroke, "offsetX", 0f)
        setField(stroke, "offsetY", 0f)

        val params = visibleGradientStrokeParams()
        invoke(stroke, "applyStrokeLineParams", params)
        val corner = enumConstant(load(QS_STROKE_CORNER), "G2")
            ?: error("QS GradientStrokeShader.G2 missing")
        val accepted = invoke(stroke, "setCorner", corner, radiusPx, 1f)
        if (accepted is Boolean && !accepted) error("QS GradientStrokeShader rejected corner")
        invoke(stroke, "getShader") as? RuntimeShader
            ?: error("QS GradientStrokeShader.getShader returned null")
    }

    /** Executes the volume-panel-specific FULL/SMOOTH gradient-stroke shader. */
    fun createVolumeStrokeShader(width: Int, height: Int, radiusPx: Float): Result<RuntimeShader> = runCatching {
        require(width > 0 && height > 0)
        val stroke = load(VOLUME_STROKE).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invoke(stroke, "setResolution", PointF(width.toFloat(), height.toFloat()))
        val params = visibleGradientStrokeParams()
        invoke(stroke, "applyStrokeLineParams", params)
        invokeOptional(stroke, "setStrokeLineWidthNear", 1.6f)
        invokeOptional(stroke, "setStrokeLineWidthFar", 1.0f)
        val corner = enumConstant(load(VOLUME_STROKE_CORNER), "SMOOTH")
            ?: enumConstant(load(VOLUME_STROKE_CORNER), "FULL")
            ?: error("VolumeGradientStrokeShader corner type missing")
        val accepted = invoke(stroke, "setCorner", corner, radiusPx, 1f)
        if (accepted is Boolean && !accepted) error("VolumeGradientStrokeShader rejected corner")
        invoke(stroke, "updateShader")
        invoke(stroke, "getShader") as? RuntimeShader
            ?: error("VolumeGradientStrokeShader.getShader returned null")
    }

    /**
     * Exercises the Metaball module embedded in SystemUI DrawableShader using a
     * plain BlendDrawable. This is the same AGSL module used by
     * MetaBallBlurDrawable, but without pretending that a third-party View has
     * the latter's SurfaceControl-backed live-blur transport.
     */
    fun createMetaBallPostEffectDrawable(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        radiusPx: Float,
        phase: Float,
    ): Result<Drawable> = runCatching {
        val drawable = baseBridge.createPostEffectDrawable(
            bitmap = bitmap,
            width = width,
            height = height,
            options = ColorOsSystemUiPostEffectBridge.PostEffectOptions(
                cornerType = "G2",
                cornerRadiusPx = radiusPx,
                cornerWeight = 1f,
                optics = false,
                gradientStroke = false,
                innerShadow = false,
            ),
        ).getOrThrow()

        val drawableShader = invoke(drawable, "getDrawableShader")
            ?: error("BlendDrawable.getDrawableShader returned null")
        require(load(DRAWABLE_SHADER).isInstance(drawableShader))

        val params = load(META_BALL_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invoke(params, "setValid", true)
        invoke(params, "setIndex", phase.coerceIn(0f, 1f))
        invoke(params, "setMode", 0f)

        val balls = java.util.ArrayList<Any>()
        val d = height.toFloat().coerceAtLeast(1f)
        val travel = width * 0.16f
        balls += newBall(
            posX = width * 0.38f - travel * (phase - 0.5f),
            posY = height * 0.50f,
            width = d * 0.72f,
            height = d * 0.72f,
            corner = d * 0.36f,
        )
        balls += newBall(
            posX = width * 0.62f + travel * (phase - 0.5f),
            posY = height * 0.50f,
            width = d * 0.72f,
            height = d * 0.72f,
            corner = d * 0.36f,
        )
        invoke(params, "setBallParamsList", balls)

        val blendParams = load(META_BALL_BLEND_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invokeOptional(blendParams, "setAntiAliasing", 1.2f)
        invokeOptional(blendParams, "setBlendStrength", 0.78f)
        invokeOptional(blendParams, "setStrengthSmooth", 0.72f)
        invokeOptional(blendParams, "setShapeDis", d * 0.80f)
        invokeOptional(blendParams, "setShapeChangeRangeX", d * 0.80f)
        invokeOptional(blendParams, "setShapeChangeRangeY", d * 0.80f)
        invokeOptional(blendParams, "setEdgeFade", 0.18f)
        invoke(params, "setBlendParams", blendParams)

        val accepted = invoke(drawableShader, "setMetaBallParams", params)
        if (accepted is Boolean && !accepted) error("DrawableShader rejected MetaBallParams")
        drawable.setBounds(0, 0, width, height)
        drawable
    }

    /** Simple executable wrapper around SystemUI's WallpaperBlurDrawable. */
    fun createWallpaperBlurDrawable(bitmap: Bitmap, overlayColor: Int): Result<Drawable> = runCatching {
        val clazz = load(WALLPAPER_BLUR_DRAWABLE)
        val drawable = clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        require(drawable is Drawable) { "$WALLPAPER_BLUR_DRAWABLE is not a Drawable" }
        val bitmapDrawable = BitmapDrawable(packageContext.resources, bitmap)
        invoke(drawable, "setDrawableConfig", bitmapDrawable, overlayColor)
        drawable
    }

    /**
     * Returns the mode strings accepted by SystemUI ShaderBlendParam without
     * guessing their integer semantics in the demo.
     */
    fun shaderBlendModes(): List<String> = runCatching {
        val clazz = load(SHADER_BLEND_PARAM)
        val companionField = clazz.getDeclaredField("Companion").apply { isAccessible = true }
        val companion = companionField.get(null)
        val method = companion.javaClass.declaredMethods.first { it.name == "getModeString" && it.parameterCount == 1 }
            .apply { isAccessible = true }
        (0..15).mapNotNull { mode ->
            val name = method.invoke(companion, mode)?.toString() ?: return@mapNotNull null
            if (name.isBlank()) null else "$mode:$name"
        }.distinct()
    }.getOrDefault(emptyList())

    /**
     * Loads SystemUI's res/raw/metaball.agsl and binds its bitmap input. This is
     * a separate rotating texture/light mask, not the geometric Metaball fusion
     * module in DrawableShader.
     */
    fun createRawMetaballShader(bitmap: Bitmap, width: Int, height: Int, time: Float): Result<RuntimeShader> = runCatching {
        require(Build.VERSION.SDK_INT >= 33)
        val id = packageContext.resources.getIdentifier("metaball", "raw", SYSTEM_UI_PACKAGE)
        require(id != 0) { "res/raw/metaball.agsl not found" }
        val source = packageContext.resources.openRawResource(id).bufferedReader().use { it.readText() }
        val shader = RuntimeShader(source)
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setInputShader("u_texture", bitmapShader)
        shader.setFloatUniform("u_textureSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_radius", minOf(width, height) * 0.48f)
        shader.setFloatUniform("u_centerPx", width * 0.50f, height * 0.50f)
        shader.setFloatUniform("a_alpha", 0.92f)
        shader.setFloatUniform("u_rotationSpeed", 0.32f)
        shader
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        view.foreground = null
    }

    private fun newBall(
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        corner: Float,
    ): Any {
        val ball = load(META_BALL_BALL_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invoke(ball, "setPosX", posX)
        invoke(ball, "setPosY", posY)
        invoke(ball, "setWidth", width)
        invoke(ball, "setHeight", height)
        invoke(ball, "setCorner", corner)
        invoke(ball, "setWeight", 1f)
        invoke(ball, "setBlendShape", 1f)
        invoke(ball, "setShapeRangeFactorX", 1f)
        invoke(ball, "setShapeRangeFactorY", 1f)
        return ball
    }

    private fun visibleGradientStrokeParams(): Any {
        val params = load(GRADIENT_STROKE_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invoke(params, "setStrokeLineColor", Color.valueOf(Color.WHITE))
        invoke(params, "setRatio", 0.18f)
        invoke(params, "setStrokeLineAlphaNear", 0.78f)
        invoke(params, "setStrokeLineAlphaFar", 0.22f)
        invoke(params, "setStrokeLineMix", 1f)
        invoke(params, "setStrokeLinePow", 1f)
        invoke(params, "setStrokeLineTransverseNearSolid", 0.12f)
        invoke(params, "setStrokeLineTransverseNearFade", 0.34f)
        invoke(params, "setStrokeLineTransverseFarSolid", 0.06f)
        invoke(params, "setStrokeLineTransverseFarFade", 0.26f)
        invoke(params, "setStrokeLineVerticalNearSolid", 0.12f)
        invoke(params, "setStrokeLineVerticalNearFade", 0.36f)
        invoke(params, "setStrokeLineVerticalFarSolid", 0.06f)
        invoke(params, "setStrokeLineVerticalFarFade", 0.28f)
        return params
    }

    private fun setField(receiver: Any, name: String, value: Any?) {
        val field = findField(receiver.javaClass, name)
            ?: error("${receiver.javaClass.name}.$name field not found")
        field.isAccessible = true
        field.set(receiver, value)
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == name }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun invoke(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findMethod(receiver.javaClass, name, args)
            ?: error("${receiver.javaClass.name}.$name(${args.size}) not found")
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    private fun invokeOptional(receiver: Any, name: String, vararg args: Any?): Any? {
        val method = findMethod(receiver.javaClass, name, args) ?: return null
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

    private fun enumConstant(clazz: Class<*>, name: String): Any? =
        clazz.enumConstants?.firstOrNull { (it as Enum<*>).name == name }

    private fun load(name: String): Class<*> = loader.loadClass(name)
}
