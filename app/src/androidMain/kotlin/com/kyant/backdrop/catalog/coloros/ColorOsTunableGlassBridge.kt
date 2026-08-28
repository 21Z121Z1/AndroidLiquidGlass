package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.kyant.backdrop.catalog.ColorOsHiddenApiAccess
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.WeakHashMap
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.max

internal data class TunableColor4(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
)

/**
 * Research controls for the *installed* ColorOS 17 keyguard glass shader.
 *
 * The shader source is extracted at runtime from the installed
 * com.oplus.keyguard.personality.clocks APK. The repository does not bundle a
 * copied vendor shader. Constants which the shipping optimized shader bakes at
 * compile time are converted to RuntimeShader uniforms in memory so the demo
 * can explore the same algorithm continuously.
 */
internal data class TunableGlassParams(
    // SDF / soft-field input.
    val blurRadiusX: Float = 10f,
    val blurRadiusY: Float = 10f,

    // Public / existing runtime uniforms.
    val alpha: Float = 1f,
    val glassMix: Float = 1f,
    val stateMix: Float = 1f,
    val maskColorProgress: Float = 1f,
    val animMix: Float = 0f,
    val hasColonMix: Float = 0f,
    val effectLightEnabled: Boolean = true,
    val showSdf: Boolean = false,
    val onlyViewContext: Boolean = false,

    // Refraction / dispersion. Shipping optimized defaults recovered from the
    // current ColorOS 17 shader.
    val refractionIntensityScale: Float = 0.55f,
    val refractionRangeX: Float = 0.55f,
    val refractionRangeY: Float = 0.55f,
    val dispersionIntensityScale: Float = 0.20f,
    val maxGradient: Float = 0.05f,

    // Directional edge light.
    val glowPower: Float = 2.2f,
    val glowDirScaleX: Float = 2f,
    val glowDirScaleY: Float = 1f,
    val glowOffsetX: Float = 0f,
    val glowOffsetY: Float = 0f,
    val glowExposure: Float = 0.05f,
    val glowColor: TunableColor4 = TunableColor4(1f, 1f, 1f, 1f),
    val glowLightDirX: Float = -1f,
    val glowLightDirY: Float = 4f,
    val glowLightFocus: Float = 6.5f,
    val glowIntensityMode0: Float = 0.2f,
    val glowIntensityMode1: Float = 0f,

    // Stroke.
    val strokePower: Float = 1f,
    val strokeDirScaleX: Float = 1f,
    val strokeDirScaleY: Float = 1f,
    val strokeExposure: Float = 0.5f,
    val strokeIntensity: Float = 0.5f,

    // Inner shadow.
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val shadowDistance: Float = 1f,
    val shadowSoftness: Float = 1f,
    val innerShadowColor: TunableColor4 = TunableColor4(0f, 0f, 0f, 0.06f),

    // Noise.
    val noiseDensity: Float = 0.06f,
    val noiseScale: Float = 1f,

    // Shipping material colour recipes. They are normally compile-time
    // constants in the optimized AGSL and are exposed here only for research.
    val onePlusTop: TunableColor4 = TunableColor4(1f, 0f, 0f, 0.8f),
    val onePlusMiddle: TunableColor4 = TunableColor4(1f, 0f, 0f, 0.5f),
    val onePlusBottom: TunableColor4 = TunableColor4(0.8549f, 0.8549f, 0.8549f, 0.25f),
    val noColonLightTop: TunableColor4 = TunableColor4(0.2f, 0.2f, 0.2f, 0.4f),
    val noColonLightBottom: TunableColor4 = TunableColor4(0.10196f, 0.10196f, 0.10196f, 0.75f),
    val noColonLightBottomAlt: TunableColor4 = TunableColor4(0.10196f, 0.10196f, 0.10196f, 0.6f),
    val noColonDarkTop: TunableColor4 = TunableColor4(1f, 1f, 1f, 0.2f),
    val noColonDarkMiddle: TunableColor4 = TunableColor4(0.8549f, 0.8549f, 0.8549f, 0.45f),
    val noColonDarkMiddleAlt: TunableColor4 = TunableColor4(0.8549f, 0.8549f, 0.8549f, 0.35f),
    val noColonDarkBottom: TunableColor4 = TunableColor4(0.4f, 0.4f, 0.4f, 0.6f),
    val hasColonLightTop: TunableColor4 = TunableColor4(0.2f, 0.2f, 0.2f, 0.4f),
    val hasColonLightBottom: TunableColor4 = TunableColor4(0.10196f, 0.10196f, 0.10196f, 0.6f),
    val hasColonDarkTop: TunableColor4 = TunableColor4(1f, 1f, 1f, 0.2f),
    val hasColonDarkMiddle: TunableColor4 = TunableColor4(0.8549f, 0.8549f, 0.8549f, 0.35f),
    val hasColonDarkBottom: TunableColor4 = TunableColor4(0.4f, 0.4f, 0.4f, 0.6f),
)

internal class ColorOsTunableGlassBridge(context: Context) {
    companion object {
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
        private const val BUILDER_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"
        private const val MULTI_INPUT_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.RenderEffectMultiInput"
        private const val VEC2_CLASS = "$BUILDER_CLASS\$Vec2"
        private const val VEC4_CLASS = "$BUILDER_CLASS\$Vec4"
        private const val PARA_TABLE_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassParaTable"
        private const val REGION_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassRegion"

        private val tunableConstantTypes = linkedMapOf(
            "GLOW_POWER" to "float",
            "GLOW_DIR_SCALE" to "float2",
            "GLOW_OFFSET" to "float2",
            "GLOW_EXPOSURE_INTENSITY" to "float",
            "GLOW_COLOR" to "float4",
            "GLOW_LIGHT_DIR" to "float2",
            "GLOW_LIGHT_FOCUS" to "float",
            "GLOW_INTENSITY_MODE0" to "float",
            "GLOW_INTENSITY_MODE1" to "float",
            "REFRACTION_INTENSITY_SCALE" to "float",
            "REFRACTION_RANGE" to "float2",
            "DISPERSION_INTENSITY_SCALE" to "float",
            "SHADOW_OFFSET" to "float2",
            "SHADOW_DISTANCE" to "float",
            "SHADOW_SOFTNESS" to "float",
            "INNER_SHADOW_COLOR" to "float4",
            "STROKE_POWER" to "float",
            "STROKE_DIR_SCALE" to "float2",
            "STROKE_EXPOSURE_INTENSITY" to "float",
            "STROKE_INTENSITY" to "float",
            "NOISE_DENSITY" to "float",
            "NOISE_SCALE" to "float",
            "ONE_PLUS_TOP" to "float4",
            "ONE_PLUS_MIDDLE" to "float4",
            "ONE_PLUS_BOT" to "float4",
            "NO_COLON_LIGHT_TOP" to "float4",
            "NO_COLON_LIGHT_BOT" to "float4",
            "NO_COLON_LIGHT_BOT_ALT" to "float4",
            "NO_COLON_DARK_TOP" to "float4",
            "NO_COLON_DARK_MIDDLE" to "float4",
            "NO_COLON_DARK_MIDDLE_ALT" to "float4",
            "NO_COLON_DARK_BOT" to "float4",
            "HAS_COLON_LIGHT_TOP" to "float4",
            "HAS_COLON_LIGHT_BOT" to "float4",
            "HAS_COLON_DARK_TOP" to "float4",
            "HAS_COLON_DARK_MIDDLE" to "float4",
            "HAS_COLON_DARK_BOT" to "float4",
        )
    }

    private val hostContext = context.applicationContext
    private val sessions = WeakHashMap<View, Session>()

    init {
        ColorOsHiddenApiAccess.enable()
    }

    @Suppress("DEPRECATION")
    private val vendorContextResult = runCatching {
        hostContext.createPackageContext(
            CLOCK_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val vendorContext: Context get() = vendorContextResult.getOrThrow()
    private val loader: ClassLoader get() = vendorContext.classLoader

    private val shaderSourceResult: Result<String> by lazy {
        runCatching { patchShippingShader(extractShippingShader()) }
    }

    fun locationColor(): Result<Int> = runCatching {
        val tableClass = loader.loadClass(PARA_TABLE_CLASS)
        val regionClass = loader.loadClass(REGION_CLASS)
        val regions = regionClass.enumConstants ?: error("GlassRegion is not an enum")
        val preferred = listOf("HOUR_TENS", "HOUR_ONES", "MINUTE_TENS", "MINUTE_ONES", "TIME_SEPARATOR")
        val region = preferred.asSequence()
            .mapNotNull { name -> regions.firstOrNull { (it as Enum<*>).name == name } }
            .firstOrNull() ?: regions.firstOrNull() ?: error("GlassRegion has no constants")
        val method = tableClass.getDeclaredMethod("locationColorFor", regionClass).apply { isAccessible = true }
        val receiver = if (Modifier.isStatic(method.modifiers)) null else tableClass.getDeclaredConstructor().newInstance()
        (method.invoke(receiver, region) as? Number)?.toInt()
            ?: error("GlassParaTable.locationColorFor returned a non-number")
    }

    fun diagnostics(): List<String> = buildList {
        add("package=$CLOCK_PACKAGE")
        add("sdk=${Build.VERSION.SDK_INT}")
        add("packageContext=${vendorContextResult.fold({ "loaded" }, { "failed:${describe(it)}" })}")
        add("shader=${shaderSourceResult.fold({ "extracted+patched:${it.length} chars" }, { "failed:${describe(it)}" })}")
        if (vendorContextResult.isSuccess) {
            add("builder=${runCatching { loader.loadClass(BUILDER_CLASS).name }.getOrElse { "failed:${describe(it)}" }}")
            add("multiInput=${runCatching { loader.loadClass(MULTI_INPUT_CLASS).name }.getOrElse { "failed:${describe(it)}" }}")
        }
    }

    /**
     * Creates (or replaces) a live session. The installed vendor Builder still
     * performs wallpaper/config setup; only the optimized AGSL constants and
     * the soft-field blur node are made tunable.
     */
    fun attach(
        view: View,
        wallpaper: Bitmap,
        params: TunableGlassParams,
    ): Result<String> = runCatching {
        require(Build.VERSION.SDK_INT >= 33) { "RuntimeShader requires Android 13+" }
        require(view.width > 0 && view.height > 0) { "view is not laid out" }
        require(!wallpaper.isRecycled) { "wallpaper is recycled" }

        clear(view)

        val multiInputClass = Class.forName(MULTI_INPUT_CLASS, false, loader)
        ColorOsHiddenApiAccess.enable(multiInputClass, ColorOsTunableGlassBridge::class.java).getOrThrow()

        val source = shaderSourceResult.getOrThrow()
        val shader = RuntimeShader(source)
        val builderClass = loader.loadClass(BUILDER_CLASS)
        val builder = builderClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

        // Replace the Builder's full RuntimeShader before init(), so all of the
        // vendor's normal initialization and wallpaper binding is replayed into
        // the dynamically extracted/tunable shader.
        val runtimeField = builderClass.getDeclaredField("runtimeShader").apply { isAccessible = true }
        runtimeField.set(builder, shader)

        invokeRequired(builder, "init", view.width, view.height, params.effectLightEnabled)
        invokeRequired(builder, "setEffectSize", view.width, view.height)
        invokeRequired(builder, "setWallpaperBg", wallpaper, wallpaper)
        applyWallpaperGeometry(builder, wallpaper, view)
        applyBuilderControls(builder, params)

        // Let vendor code populate all shipping uniforms/input shaders once.
        // We ignore its hard-coded 10px multi-input effect and build an
        // equivalent graph below with user-controlled X/Y blur radii.
        invokeRequired(builder, "buildRenderEffect")

        val session = Session(
            view = view,
            wallpaper = wallpaper,
            builder = builder,
            shader = shader,
            multiInputClass = multiInputClass,
            maxSampleRadius = conservativeMaxSampleRadius(wallpaper),
        )
        sessions[view] = session
        session.update(params, forceEffectRebuild = true)
        "tunable vendor shader attached; source=${source.length} chars"
    }

    fun update(view: View, params: TunableGlassParams): Result<Unit> = runCatching {
        val session = sessions[view] ?: error("No tunable glass session for View")
        session.update(params, forceEffectRebuild = false)
    }

    fun updateGeometry(view: View): Result<Unit> = runCatching {
        val session = sessions[view] ?: return@runCatching
        applyWallpaperGeometry(session.builder, session.wallpaper, view)
        view.invalidate()
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        sessions.remove(view)?.let { session ->
            runCatching { invokeOptional(session.builder, "release") }
        }
    }

    private inner class Session(
        val view: View,
        val wallpaper: Bitmap,
        val builder: Any,
        val shader: RuntimeShader,
        val multiInputClass: Class<*>,
        val maxSampleRadius: Float,
    ) {
        private var lastBlurX = Float.NaN
        private var lastBlurY = Float.NaN

        fun update(params: TunableGlassParams, forceEffectRebuild: Boolean) {
            applyBuilderControls(builder, params)
            applyShaderControls(shader, params)

            val blurChanged = forceEffectRebuild ||
                params.blurRadiusX != lastBlurX || params.blurRadiusY != lastBlurY
            if (blurChanged) {
                lastBlurX = params.blurRadiusX
                lastBlurY = params.blurRadiusY
                val blur = createBlurEffect(params.blurRadiusX, params.blurRadiusY)
                val effect = createVendorMultiInputEffect(
                    multiInputClass = multiInputClass,
                    runtimeShader = shader,
                    blurEffect = blur,
                    maxSampleRadius = maxSampleRadius,
                )
                view.setRenderEffect(effect)
            }
            view.invalidate()
        }
    }

    private fun applyBuilderControls(builder: Any, p: TunableGlassParams) {
        invokeRequired(builder, "setGlass", p.glassMix.coerceIn(0f, 1f))
        invokeRequired(builder, "setMixProgress", p.stateMix.coerceIn(0f, 1f))
        invokeRequired(builder, "setMaskColorProgress", p.maskColorProgress.coerceIn(0f, 1f))
        invokeRequired(builder, "setEffectLightEnabled", p.effectLightEnabled)
        invokeRequired(builder, "setOnlyViewContext", p.onlyViewContext)
    }

    private fun applyShaderControls(shader: RuntimeShader, p: TunableGlassParams) {
        shader.setFloatUniform("u_Alpha", p.alpha.coerceIn(0f, 1f))
        shader.setFloatUniform("u_RefractMix", p.glassMix.coerceIn(0f, 1f))
        shader.setFloatUniform("u_StateMix", p.stateMix.coerceIn(0f, 1f))
        shader.setFloatUniform("u_AnimMix", p.animMix.coerceIn(0f, 1f))
        shader.setFloatUniform("u_HasColonMix", p.hasColonMix.coerceIn(0f, 1f))
        shader.setFloatUniform("u_ShowSDF", if (p.showSdf) 1f else 0f)
        shader.setFloatUniform("u_OnlyViewContext", if (p.onlyViewContext) 1f else 0f)

        shader.setFloatUniform("REFRACTION_INTENSITY_SCALE", p.refractionIntensityScale.coerceAtLeast(0f))
        shader.setFloatUniform("REFRACTION_RANGE", p.refractionRangeX.coerceAtLeast(0.001f), p.refractionRangeY.coerceAtLeast(0.001f))
        shader.setFloatUniform("DISPERSION_INTENSITY_SCALE", p.dispersionIntensityScale.coerceAtLeast(0f))
        shader.setFloatUniform("MAX_GRADIENT", p.maxGradient.coerceAtLeast(0.001f))

        shader.setFloatUniform("GLOW_POWER", p.glowPower.coerceAtLeast(0.01f))
        shader.setFloatUniform("GLOW_DIR_SCALE", p.glowDirScaleX.coerceAtLeast(0.001f), p.glowDirScaleY.coerceAtLeast(0.001f))
        shader.setFloatUniform("GLOW_OFFSET", p.glowOffsetX, p.glowOffsetY)
        shader.setFloatUniform("GLOW_EXPOSURE_INTENSITY", p.glowExposure)
        setColor(shader, "GLOW_COLOR", p.glowColor)
        shader.setFloatUniform("GLOW_LIGHT_DIR", p.glowLightDirX, p.glowLightDirY)
        shader.setFloatUniform("GLOW_LIGHT_FOCUS", p.glowLightFocus.coerceAtLeast(0.01f))
        shader.setFloatUniform("GLOW_INTENSITY_MODE0", p.glowIntensityMode0.coerceAtLeast(0f))
        shader.setFloatUniform("GLOW_INTENSITY_MODE1", p.glowIntensityMode1.coerceAtLeast(0f))

        shader.setFloatUniform("STROKE_POWER", p.strokePower.coerceAtLeast(0.01f))
        shader.setFloatUniform("STROKE_DIR_SCALE", p.strokeDirScaleX.coerceAtLeast(0.001f), p.strokeDirScaleY.coerceAtLeast(0.001f))
        shader.setFloatUniform("STROKE_EXPOSURE_INTENSITY", p.strokeExposure)
        shader.setFloatUniform("STROKE_INTENSITY", p.strokeIntensity.coerceAtLeast(0f))

        shader.setFloatUniform("SHADOW_OFFSET", p.shadowOffsetX, p.shadowOffsetY)
        shader.setFloatUniform("SHADOW_DISTANCE", p.shadowDistance)
        shader.setFloatUniform("SHADOW_SOFTNESS", p.shadowSoftness.coerceAtLeast(0.001f))
        setColor(shader, "INNER_SHADOW_COLOR", p.innerShadowColor)

        shader.setFloatUniform("NOISE_DENSITY", p.noiseDensity.coerceAtLeast(0f))
        shader.setFloatUniform("NOISE_SCALE", p.noiseScale.coerceAtLeast(0f))

        setColor(shader, "ONE_PLUS_TOP", p.onePlusTop)
        setColor(shader, "ONE_PLUS_MIDDLE", p.onePlusMiddle)
        setColor(shader, "ONE_PLUS_BOT", p.onePlusBottom)
        setColor(shader, "NO_COLON_LIGHT_TOP", p.noColonLightTop)
        setColor(shader, "NO_COLON_LIGHT_BOT", p.noColonLightBottom)
        setColor(shader, "NO_COLON_LIGHT_BOT_ALT", p.noColonLightBottomAlt)
        setColor(shader, "NO_COLON_DARK_TOP", p.noColonDarkTop)
        setColor(shader, "NO_COLON_DARK_MIDDLE", p.noColonDarkMiddle)
        setColor(shader, "NO_COLON_DARK_MIDDLE_ALT", p.noColonDarkMiddleAlt)
        setColor(shader, "NO_COLON_DARK_BOT", p.noColonDarkBottom)
        setColor(shader, "HAS_COLON_LIGHT_TOP", p.hasColonLightTop)
        setColor(shader, "HAS_COLON_LIGHT_BOT", p.hasColonLightBottom)
        setColor(shader, "HAS_COLON_DARK_TOP", p.hasColonDarkTop)
        setColor(shader, "HAS_COLON_DARK_MIDDLE", p.hasColonDarkMiddle)
        setColor(shader, "HAS_COLON_DARK_BOT", p.hasColonDarkBottom)
    }

    private fun setColor(shader: RuntimeShader, name: String, c: TunableColor4) {
        shader.setFloatUniform(name, c.r, c.g, c.b, c.a)
    }

    private fun createBlurEffect(radiusX: Float, radiusY: Float): RenderEffect? {
        val x = radiusX.coerceIn(0f, 40f)
        val y = radiusY.coerceIn(0f, 40f)
        if (x <= 0.0001f && y <= 0.0001f) return null
        return RenderEffect.createBlurEffect(
            max(x, 0.001f),
            max(y, 0.001f),
            // The keyguard implementation normally blurs masks that are inset
            // inside a larger transparent layer. Our generic material preview
            // draws a rounded rectangle flush to the View bounds. CLAMP would
            // repeat opaque edge pixels outside that layer, flattening the
            // alpha gradient on all straight edges and leaving visible
            // refraction only at corners. Kyant's analytic rounded-rect SDF has
            // explicit normals for every edge; DECAL gives ColorOS's blurred
            // soft field the equivalent transparent exterior boundary while
            // keeping the vendor refraction/dispersion/lighting algorithm.
            Shader.TileMode.DECAL,
        )
    }

    private fun createVendorMultiInputEffect(
        multiInputClass: Class<*>,
        runtimeShader: RuntimeShader,
        blurEffect: RenderEffect?,
        maxSampleRadius: Float,
    ): RenderEffect {
        val receiver = runCatching {
            multiInputClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        }.getOrNull()
        val methods = multiInputClass.declaredMethods.filter { it.name == "create" }
        val four = methods.firstOrNull { method ->
            val p = method.parameterTypes
            p.size == 4 && p[0] == RuntimeShader::class.java && p[1].isArray && p[2].isArray &&
                (p[3] == java.lang.Float.TYPE || p[3] == java.lang.Float::class.java)
        }
        val three = methods.firstOrNull { method ->
            val p = method.parameterTypes
            p.size == 3 && p[0] == RuntimeShader::class.java && p[1].isArray && p[2].isArray
        }
        val method = (four ?: three ?: error("RenderEffectMultiInput.create overload not found")).apply { isAccessible = true }
        val actualReceiver = if (Modifier.isStatic(method.modifiers)) null else receiver
            ?: error("RenderEffectMultiInput.INSTANCE is null")
        val names = arrayOf("u_BlurClockTex", "u_ClockTex")
        val inputs = arrayOf<RenderEffect?>(blurEffect, null)
        val value = if (method.parameterCount == 4) {
            method.invoke(actualReceiver, runtimeShader, names, inputs, maxSampleRadius)
        } else {
            method.invoke(actualReceiver, runtimeShader, names, inputs)
        }
        return value as? RenderEffect ?: error("RenderEffectMultiInput.create returned ${value?.javaClass?.name ?: "null"}")
    }

    private fun conservativeMaxSampleRadius(wallpaper: Bitmap): Float {
        // Covers the whole UI range without rebuilding the effect graph while
        // dragging refraction/dispersion sliders. The shipping implementation
        // uses a much smaller bound; this research page intentionally trades a
        // wider bound for continuous tuning.
        val screenMax = max(wallpaper.width, wallpaper.height).toFloat()
        val refMax = screenMax * 1.25f * 0.10f
        val dispMax = wallpaper.width * 0.60f * 0.10f
        return max(256f, refMax + dispMax + 56f)
    }

    private fun applyWallpaperGeometry(builder: Any, wallpaper: Bitmap, view: View) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val vec2Class = loader.loadClass(VEC2_CLASS)
        val vec4Class = loader.loadClass(VEC4_CLASS)
        val resolution = vec2Class.getDeclaredConstructor(java.lang.Float.TYPE, java.lang.Float.TYPE)
            .apply { isAccessible = true }
            .newInstance(wallpaper.width.toFloat(), wallpaper.height.toFloat())
        val crop = vec4Class.getDeclaredConstructor(
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
        ).apply { isAccessible = true }.newInstance(
            location[0].toFloat(),
            location[1].toFloat(),
            view.width.toFloat(),
            view.height.toFloat(),
        )
        val method = builder.javaClass.getDeclaredMethod(
            "setClockRect",
            vec2Class,
            vec4Class,
            java.lang.Integer.TYPE,
            java.lang.Float.TYPE,
        ).apply { isAccessible = true }
        method.invoke(builder, resolution, crop, 0, 1f)
    }

    private fun extractShippingShader(): String {
        val info = @Suppress("DEPRECATION") hostContext.packageManager.getApplicationInfo(CLOCK_PACKAGE, 0)
        val apkPaths = buildList {
            add(info.sourceDir)
            info.splitSourceDirs?.let { addAll(it) }
        }
        apkPaths.forEach { apkPath ->
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.matches(Regex("classes(\\d+)?\\.dex"))) continue
                    val dex = zip.getInputStream(entry).use { it.readBytes() }
                    findShaderInDex(dex)?.let { return it }
                }
            }
        }
        error("Optimized ColorOS glass RuntimeShader string not found in installed APK")
    }

    private fun findShaderInDex(dex: ByteArray): String? {
        if (dex.size < 0x70 || !dex.copyOfRange(0, 3).contentEquals(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte()))) {
            return null
        }
        val bb = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.getInt(0x38)
        val idsOffset = bb.getInt(0x3c)
        if (count <= 0 || idsOffset < 0 || idsOffset + count.toLong() * 4L > dex.size) return null
        for (i in 0 until count) {
            val dataOffset = bb.getInt(idsOffset + i * 4)
            if (dataOffset <= 0 || dataOffset >= dex.size) continue
            var pos = skipUleb128(dex, dataOffset) ?: continue
            val endLimit = minOf(dex.size, pos + 120_000)
            var end = pos
            while (end < endLimit && dex[end] != 0.toByte()) end++
            if (end == endLimit) continue
            val length = end - pos
            if (length < 8_000) continue
            val text = String(dex, pos, length, Charsets.UTF_8)
            if (
                text.contains("uniform shader u_BlurClockTex;") &&
                text.contains("calculateLiquidGlassRefraction") &&
                text.contains("REFRACTION_INTENSITY_SCALE")
            ) {
                return text
            }
        }
        return null
    }

    private fun skipUleb128(bytes: ByteArray, start: Int): Int? {
        var p = start
        repeat(5) {
            if (p >= bytes.size) return null
            val b = bytes[p++].toInt() and 0xff
            if (b and 0x80 == 0) return p
        }
        return null
    }

    private fun patchShippingShader(original: String): String {
        var source = original
        tunableConstantTypes.forEach { (name, type) ->
            val declaration = Regex("const\\s+$type\\s+$name\\s*=\\s*[^;]+;")
            require(declaration.containsMatchIn(source)) { "Shipping shader constant not found: $name" }
            source = declaration.replaceFirst(source, "uniform $type $name;")
        }
        val screenUniform = "uniform float2 u_ScreenResolution;"
        require(source.contains(screenUniform)) { "u_ScreenResolution declaration not found" }
        source = source.replaceFirst(screenUniform, "$screenUniform\nuniform float MAX_GRADIENT;")
        val gradientClamp = "min(gradLen, 0.05)"
        require(source.contains(gradientClamp)) { "Shipping gradient clamp expression not found" }
        source = source.replace(gradientClamp, "min(gradLen, MAX_GRADIENT)")
        return source
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

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
