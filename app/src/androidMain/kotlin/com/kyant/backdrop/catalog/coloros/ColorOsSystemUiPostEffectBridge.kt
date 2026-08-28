package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Runtime bridge to the ColorOS 17 SystemUI post-effect stack.
 *
 * The bridge deliberately loads code and AGSL from the installed
 * com.android.systemui package. No vendor shader source is copied into the
 * repository. Capabilities that require a SurfaceControl are reported by
 * diagnostics instead of being imitated inside an ordinary third-party View.
 */
internal class ColorOsSystemUiPostEffectBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        private const val BASE_DRAWABLE = "com.oplus.posteffect.drawable.BaseDrawable"
        private const val BLEND_DRAWABLE = "com.oplus.posteffect.drawable.BlendDrawable"
        private const val CONTINUOUS_BLUR_DRAWABLE = "com.oplus.posteffect.drawable.ContinuousBlurDrawable"
        private const val METABALL_BLUR_DRAWABLE = "com.oplus.posteffect.drawable.MetaBallBlurDrawable"

        private const val BLEND_PARAM = "com.oplus.posteffect.BlendParam"
        private const val CORNER_PARAMS = "com.oplus.posteffect.params.CornerParams"
        private const val CORNER_TYPE = "com.oplus.posteffect.params.CornerType"
        private const val OPTICS_PARAMS = "com.oplus.posteffect.params.OpticsParams"
        private const val INNER_SHADOW_PARAMS = "com.oplus.posteffect.params.InnerShadowParams"
        private const val GRADIENT_STROKE_PARAMS = "com.oplus.posteffect.params.GradientStrokeLineParams"

        private const val CHROMATIC_ASSET = "chromatic.agsl"
        private const val BAR_GLOW_ASSET = "barglow.agsl"
        private const val GAUSSIAN_BLUR_ASSET = "gaussian_blur_fragment_shader.glsl"
        private const val BLUR_DOWN_ASSET = "blur_down_fragment_shader.glsl"
        private const val BLUR_UP_ASSET = "blur_up_fragment_shader.glsl"
    }

    private val hostContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val systemUiContextResult = runCatching {
        hostContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val systemUiContext: Context get() = systemUiContextResult.getOrThrow()
    private val loader: ClassLoader get() = systemUiContext.classLoader

    private val chromaticSourceResult: Result<String> by lazy {
        runCatching {
            systemUiContext.assets.open(CHROMATIC_ASSET).bufferedReader().use { it.readText() }
        }
    }

    data class PostEffectOptions(
        val cornerType: String = "G2",
        val cornerRadiusPx: Float,
        val cornerWeight: Float = 1f,
        val optics: Boolean = true,
        val gradientStroke: Boolean = true,
        val innerShadow: Boolean = true,
    )

    data class RuntimeCapability(
        val name: String,
        val status: String,
        val runnableInOrdinaryView: Boolean,
    )

    fun capabilities(): List<RuntimeCapability> = buildList {
        add(RuntimeCapability("BlendDrawable / bitmap post-effect", classStatus(BLEND_DRAWABLE), true))
        add(RuntimeCapability("G2 / FULL / CONIC corner field", classStatus(CORNER_PARAMS), true))
        add(RuntimeCapability("OpticsEffect", classStatus(OPTICS_PARAMS), true))
        add(RuntimeCapability("GradientStrokeLine", classStatus(GRADIENT_STROKE_PARAMS), true))
        add(RuntimeCapability("InnerShadow", classStatus(INNER_SHADOW_PARAMS), true))
        add(RuntimeCapability("SystemUI chromatic.agsl", assetStatus(CHROMATIC_ASSET), Build.VERSION.SDK_INT >= 33))
        add(RuntimeCapability("SystemUI barglow.agsl", assetStatus(BAR_GLOW_ASSET), false))
        add(RuntimeCapability("SystemUI Gaussian blur shader", assetStatus(GAUSSIAN_BLUR_ASSET), false))
        add(RuntimeCapability("SystemUI downsample blur shader", assetStatus(BLUR_DOWN_ASSET), false))
        add(RuntimeCapability("SystemUI upsample blur shader", assetStatus(BLUR_UP_ASSET), false))
        add(RuntimeCapability("ContinuousBlurDrawable", classStatus(CONTINUOUS_BLUR_DRAWABLE), false))
        add(RuntimeCapability("MetaBallBlurDrawable", classStatus(METABALL_BLUR_DRAWABLE), false))
    }

    fun diagnostics(): List<String> = buildList {
        add("sourcePackage=$SYSTEM_UI_PACKAGE")
        add("packageContext=${systemUiContextResult.fold({ "loaded:${it.packageName}" }, { "failed:${describe(it)}" })}")
        if (systemUiContextResult.isSuccess) {
            add("baseDrawable=${classStatus(BASE_DRAWABLE)}")
            add("blendDrawable=${classStatus(BLEND_DRAWABLE)}")
            add("continuousBlur=${classStatus(CONTINUOUS_BLUR_DRAWABLE)}; requires=SurfaceControl")
            add("metaball=${classStatus(METABALL_BLUR_DRAWABLE)}; requires=SurfaceControl")
            add("cornerTypes=${enumNames(CORNER_TYPE).joinToString()}")
            add("chromatic=${assetStatus(CHROMATIC_ASSET)}")
            add("barGlow=${assetStatus(BAR_GLOW_ASSET)}")
            add("gaussianBlur=${assetStatus(GAUSSIAN_BLUR_ASSET)}")
            add("blurDown=${assetStatus(BLUR_DOWN_ASSET)}")
            add("blurUp=${assetStatus(BLUR_UP_ASSET)}")
        }
    }

    /**
     * Creates a real ColorOS BlendDrawable and enables the same post-effect
     * modules exposed by BaseDrawable. The supplied bitmap should already be
     * the background crop for this View; the bridge does not synthesize an
     * imitation backdrop.
     */
    fun createPostEffectDrawable(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        options: PostEffectOptions,
    ): Result<android.graphics.drawable.Drawable> = runCatching {
        require(width > 0 && height > 0) { "invalid drawable size" }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "bitmap is unusable" }

        val drawableClass = load(BLEND_DRAWABLE)
        val drawable = drawableClass
            .getDeclaredConstructor(Context::class.java, Boolean::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(systemUiContext, true)
        require(drawable is android.graphics.drawable.Drawable) {
            "$BLEND_DRAWABLE is not a Drawable"
        }

        val blendParamClass = load(BLEND_PARAM)
        val blendParam = blendParamClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val setBitmapDefault = drawableClass.declaredMethods.firstOrNull { method ->
            method.name == "setBitmap\$default" && method.parameterCount == 3
        } ?: error("BlendDrawable.setBitmap\$default not found")
        setBitmapDefault.isAccessible = true
        setBitmapDefault.invoke(null, drawable, bitmap, blendParam)

        invokeNamed(drawable, "setEnableShader", true)
        invokeNamed(drawable, "setEnableBlurShader", true)

        val cornerTypeClass = load(CORNER_TYPE)
        val cornerType = enumConstant(cornerTypeClass, options.cornerType)
            ?: error("Unknown CornerType ${options.cornerType}; available=${enumNames(CORNER_TYPE)}")
        val cornerParamsClass = load(CORNER_PARAMS)
        val corner = cornerParamsClass.getDeclaredConstructor(
            cornerTypeClass,
            Float::class.javaPrimitiveType!!,
            Float::class.javaPrimitiveType!!,
        ).apply { isAccessible = true }.newInstance(
            cornerType,
            options.cornerRadiusPx.coerceAtLeast(0f),
            options.cornerWeight.coerceAtLeast(0f),
        )
        requireBooleanSuccess(drawable, "setCornerParams", corner)

        if (options.optics) {
            val optics = load(OPTICS_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            // Keep vendor defaults for the material recipe, but explicitly make
            // the optics band visible and scale it inside the surface. These
            // are demo controls, not asserted shipping preset values.
            invokeNamed(optics, "setColor", Color.valueOf(Color.WHITE))
            invokeNamed(optics, "setAlpha", 0.30f)
            invokeNamed(optics, "setOffsetX", 0f)
            invokeNamed(optics, "setOffsetY", 0f)
            invokeNamed(optics, "setScaleX", 0.94f)
            invokeNamed(optics, "setScaleY", 0.90f)
            invokeNamed(optics, "setWidth", (width.coerceAtMost(height) * 0.12f).coerceAtLeast(2f))
            requireBooleanSuccess(drawable, "setOpticsParams", optics)
        }

        if (options.gradientStroke) {
            val stroke = load(GRADIENT_STROKE_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            invokeNamed(stroke, "setStrokeLineColor", Color.valueOf(Color.WHITE))
            // All values are normalized or alpha-like in the recovered shader.
            // Start from the vendor object defaults and only guarantee a
            // visible near/far line envelope for the comparison sample.
            invokeNamed(stroke, "setRatio", 0.18f)
            invokeNamed(stroke, "setStrokeLineAlphaNear", 0.70f)
            invokeNamed(stroke, "setStrokeLineAlphaFar", 0.18f)
            invokeNamed(stroke, "setStrokeLineMix", 1f)
            invokeNamed(stroke, "setStrokeLinePow", 1f)
            invokeNamed(stroke, "setStrokeLineTransverseNearSolid", 0.18f)
            invokeNamed(stroke, "setStrokeLineTransverseNearFade", 0.30f)
            invokeNamed(stroke, "setStrokeLineTransverseFarSolid", 0.08f)
            invokeNamed(stroke, "setStrokeLineTransverseFarFade", 0.24f)
            invokeNamed(stroke, "setStrokeLineVerticalNearSolid", 0.20f)
            invokeNamed(stroke, "setStrokeLineVerticalNearFade", 0.35f)
            invokeNamed(stroke, "setStrokeLineVerticalFarSolid", 0.08f)
            invokeNamed(stroke, "setStrokeLineVerticalFarFade", 0.28f)
            val valid = invokeOptional(stroke, "isValid") as? Boolean
            if (valid == false) error("GradientStrokeLineParams rejected comparison parameters")
            requireBooleanSuccess(drawable, "setGradientStrokeLineParams", stroke)
        }

        if (options.innerShadow) {
            val shadow = load(INNER_SHADOW_PARAMS).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            invokeNamed(shadow, "setShadowColor", Color.valueOf(Color.argb(46, 0, 0, 0)))
            // Preserve all vendor constructor defaults except the visible
            // alpha floor/ceiling used for this isolated sample.
            invokeNamed(shadow, "setNoOffsetShadowAlpha", 0.10f)
            invokeNamed(shadow, "setOffsetShadowMinAlpha", 0.02f)
            invokeNamed(shadow, "setOffsetShadowMaxAlpha", 0.16f)
            requireBooleanSuccess(drawable, "setInnerShadowParams", shadow)
        }

        drawable.setBounds(0, 0, width, height)
        drawable
    }

    /**
     * Applies the shipping SystemUI chromatic utility to the View's own
     * rendered content. This is a post-effect utility, not the keyguard glass
     * refraction path; the comparison page labels the distinction explicitly.
     */
    fun applyChromatic(view: View, offsetPx: Float): Result<Unit> = runCatching {
        require(Build.VERSION.SDK_INT >= 33) { "RuntimeShader requires Android 13+" }
        require(view.width > 0 && view.height > 0) { "view is not laid out" }
        val source = chromaticSourceResult.getOrThrow()
        val shader = RuntimeShader(source)
        shader.setFloatUniform("aberrationOffset", 0f, offsetPx)
        shader.setFloatUniform("viewport", view.width.toFloat(), view.height.toFloat())
        // Shift red one way, blue the other way, keep green at the centre.
        shader.setFloatUniform("posShiftColor", 1f, 0f, 0f, 0f)
        shader.setFloatUniform("negShiftColor", 0f, 0f, 1f, 0f)
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "texIn")
        view.setRenderEffect(effect)
        view.invalidate()
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        view.foreground = null
    }

    /** Creates a screen-space crop while preserving transparent areas outside the bitmap. */
    fun cropWallpaperForView(wallpaper: Bitmap, view: View): Bitmap {
        val outW = view.width.coerceAtLeast(1)
        val outH = view.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationOnScreen(location)

        val srcLeft = location[0].coerceAtLeast(0)
        val srcTop = location[1].coerceAtLeast(0)
        val srcRight = (location[0] + outW).coerceAtMost(wallpaper.width)
        val srcBottom = (location[1] + outH).coerceAtMost(wallpaper.height)
        if (srcRight <= srcLeft || srcBottom <= srcTop) return out

        val dstLeft = (srcLeft - location[0]).coerceAtLeast(0)
        val dstTop = (srcTop - location[1]).coerceAtLeast(0)
        val dst = Rect(
            dstLeft,
            dstTop,
            dstLeft + (srcRight - srcLeft),
            dstTop + (srcBottom - srcTop),
        )
        Canvas(out).drawBitmap(
            wallpaper,
            Rect(srcLeft, srcTop, srcRight, srcBottom),
            dst,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    private fun requireBooleanSuccess(receiver: Any, name: String, arg: Any) {
        val value = invokeNamed(receiver, name, arg)
        if (value is Boolean && !value) error("${receiver.javaClass.name}.$name rejected parameters")
    }

    private fun invokeNamed(receiver: Any, name: String, vararg args: Any?): Any? {
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
            method.name == name && method.parameterCount == args.size &&
                method.parameterTypes.zip(args).all { (type, arg) -> compatible(type, arg) }
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

    private fun enumConstant(enumClass: Class<*>, name: String): Any? =
        enumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == name }

    private fun enumNames(className: String): List<String> = runCatching {
        load(className).enumConstants?.map { (it as Enum<*>).name }.orEmpty()
    }.getOrDefault(emptyList())

    private fun classStatus(className: String): String =
        runCatching { load(className); "available" }.getOrElse { "unavailable:${describe(it)}" }

    private fun assetStatus(name: String): String = runCatching {
        systemUiContext.assets.open(name).use { input ->
            "available:${input.available()}B+"
        }
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun load(className: String): Class<*> = loader.loadClass(className)

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
