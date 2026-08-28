package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import com.kyant.backdrop.catalog.ColorOsHiddenApiAccess
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * Strict runtime bridge to the ColorOS 17 lock-screen glass implementation.
 *
 * This class intentionally contains no imitation shader. A surface is reported
 * as native only after the installed vendor package creates and returns a real
 * android.graphics.RenderEffect. API mismatches fail closed.
 */
internal class ColorOsClockGlassBridge(context: Context) {
    companion object {
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
        private const val BUILDER_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"
        private const val MULTI_INPUT_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.RenderEffectMultiInput"
        private const val VEC2_CLASS = "$BUILDER_CLASS\$Vec2"
        private const val VEC4_CLASS = "$BUILDER_CLASS\$Vec4"
        private const val PARA_TABLE_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassParaTable"
        private const val REGION_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassRegion"
    }

    private val hostContext = context.applicationContext
    private val retained = WeakHashMap<View, Any>()

    init {
        // Keep this defense in depth for callers that construct the bridge
        // without going through MainActivity. This runs before the first
        // vendor helper class is initialized, avoiding its cached null method.
        ColorOsHiddenApiAccess.enable()
    }

    @Suppress("DEPRECATION")
    private val vendorContextResult = runCatching {
        hostContext.createPackageContext(
            CLOCK_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        )
    }

    private val vendorContext: Context get() = vendorContextResult.getOrThrow()
    private val loader: ClassLoader get() = vendorContext.classLoader

    fun diagnostics(): List<String> = buildList {
        add("package=$CLOCK_PACKAGE")
        add("sdk=${Build.VERSION.SDK_INT}")
        add(ColorOsHiddenApiAccess.diagnostics())
        add("packageContext=${vendorContextResult.fold({ "loaded:${it.packageName}" }, { "failed:${describeThrowable(it)}" })}")
        if (vendorContextResult.isSuccess) {
            add(classStatus(BUILDER_CLASS))
            add(classStatus(MULTI_INPUT_CLASS))
            add(classStatus(VEC2_CLASS))
            add(classStatus(VEC4_CLASS))
            add(classStatus(PARA_TABLE_CLASS))
            add(classStatus(REGION_CLASS))
            runCatching {
                val c = loader.loadClass(BUILDER_CLASS)
                c.declaredMethods
                    .filter {
                        it.name in setOf(
                            "init",
                            "setEffectSize",
                            "setWallpaperBg",
                            "setClockRect",
                            "setConfig",
                            "setGlass",
                            "setMixProgress",
                            "setMaskColorProgress",
                            "setEffectLightEnabled",
                            "setOnlyViewContext",
                            "buildRenderEffect",
                            "getRenderEffect",
                            "getRenderEffectParamsDumpInfo",
                            "release"
                        )
                    }
                    .sortedBy { it.name }
                    .forEach { add(signature(it)) }
            }
            addAll(hiddenMultiInputDiagnostics())
        }
    }

    /**
     * ColorOS itself supplies the marker color used by the clock shader to
     * identify a material region. No private marker value is hard-coded here.
     */
    fun locationColor(): Result<Int> = runCatching {
        val tableClass = loader.loadClass(PARA_TABLE_CLASS)
        val regionClass = loader.loadClass(REGION_CLASS)
        val regions = regionClass.enumConstants ?: error("GlassRegion is not an enum")
        val preferred = listOf("HOUR_TENS", "HOUR_ONES", "MINUTE_TENS", "MINUTE_ONES", "TIME_SEPARATOR")
        val region = preferred.asSequence()
            .mapNotNull { name -> regions.firstOrNull { (it as Enum<*>).name == name } }
            .firstOrNull() ?: regions.firstOrNull() ?: error("GlassRegion has no constants")
        val method = tableClass.getDeclaredMethod("locationColorFor", regionClass).apply { isAccessible = true }
        check(Modifier.isStatic(method.modifiers)) {
            "GlassParaTable.locationColorFor is no longer static"
        }
        val value = method.invoke(null, region)
        (value as? Number)?.toInt() ?: error("locationColorFor returned ${value?.javaClass?.name}")
    }

    fun apply(
        view: View,
        wallpaper: Bitmap,
        glass: Float,
        mixProgress: Float,
        maskColorProgress: Float,
        lightEnabled: Boolean
    ): Result<String> = runCatching {
        check(Build.VERSION.SDK_INT >= 31) { "RenderEffect requires Android 12+" }
        check(view.width > 0 && view.height > 0) { "view is not laid out" }
        check(!wallpaper.isRecycled && wallpaper.width > 0 && wallpaper.height > 0) { "wallpaper bitmap is unusable" }

        // The vendor helper performs its first hidden RenderEffect lookup from
        // RenderEffectMultiInput. Exempt that exact class before constructing
        // GlassEffectBuilder, otherwise it permanently caches a null method.
        val multiInputClass = Class.forName(MULTI_INPUT_CLASS, false, loader)
        ColorOsHiddenApiAccess.enable(multiInputClass, ColorOsClockGlassBridge::class.java).getOrThrow()

        val builderClass = loader.loadClass(BUILDER_CLASS)
        val builder = builderClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        try {
            // GlassEffectHelper in ColorOS initializes the builder with the
            // effect View's measured size, not the physical display size.
            invokeRequired(builder, "init", view.width, view.height, lightEnabled)
            invokeRequired(builder, "setEffectSize", view.width, view.height)

            // A static comparison has identical from/to wallpapers. This keeps
            // the vendor state-mix machinery active without introducing a
            // background difference between the two material states.
            invokeRequired(builder, "setWallpaperBg", wallpaper, wallpaper)
            applyWallpaperGeometry(builder, wallpaper, view)

            invokeRequired(builder, "setGlass", glass.coerceIn(0f, 1f))
            invokeRequired(builder, "setMixProgress", mixProgress.coerceIn(0f, 1f))
            invokeRequired(builder, "setMaskColorProgress", maskColorProgress.coerceIn(0f, 1f))
            invokeRequired(builder, "setEffectLightEnabled", lightEnabled)

            // This must remain false. The ColorOS full shader short-circuits to
            // sampleClock() when u_OnlyViewContext == 1, bypassing its glass,
            // SDF, refraction and dispersion path entirely.
            invokeRequired(builder, "setOnlyViewContext", false)

            invokeRequired(builder, "buildRenderEffect")
            val effect = invokeRequired(builder, "getRenderEffect") as? RenderEffect
            if (effect == null) {
                error(
                    "getRenderEffect() returned null. ${builderFailureDiagnostics(builder).joinToString(" | ")}"
                )
            }
            view.setRenderEffect(effect)

            retained.put(view, builder)?.let { old -> runCatching { invokeOptional(old, "release") } }
            "native builder=${builderClass.name}; effect=${effect.javaClass.name}"
        } catch (t: Throwable) {
            runCatching { invokeOptional(builder, "release") }
            throw t
        }
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        retained.remove(view)?.let { runCatching { invokeOptional(it, "release") } }
    }

    /**
     * Exact ColorOS 17 signature verified from the supplied 17.0.30 APK:
     * setClockRect(Vec2 wallpaperResolution, Vec4 wallpaperCropRect,
     *              int wallpaperContentRotation, float contentDisplayScale)
     *
     * The comparison bitmap is normalized to the physical display coordinate
     * space. Unlike ColorOS's lock-screen root mount view, our host is a small
     * card inside a scrolling column, so the crop must be the host View's
     * screen-space rectangle. Passing a full-screen crop here would apply a
     * non-uniform view-to-wallpaper scale and squeeze the entire portrait
     * wallpaper into the card.
     */
    private fun applyWallpaperGeometry(builder: Any, wallpaper: Bitmap, view: View) {
        val screenLocation = IntArray(2)
        view.getLocationOnScreen(screenLocation)
        val vec2Class = loader.loadClass(VEC2_CLASS)
        val vec4Class = loader.loadClass(VEC4_CLASS)
        val resolution = vec2Class
            .getDeclaredConstructor(java.lang.Float.TYPE, java.lang.Float.TYPE)
            .apply { isAccessible = true }
            .newInstance(wallpaper.width.toFloat(), wallpaper.height.toFloat())
        val cropRect = vec4Class
            .getDeclaredConstructor(
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE
            )
            .apply { isAccessible = true }
            .newInstance(
                screenLocation[0].toFloat(),
                screenLocation[1].toFloat(),
                view.width.toFloat(),
                view.height.toFloat()
            )
        val method = builder.javaClass.getDeclaredMethod(
            "setClockRect",
            vec2Class,
            vec4Class,
            java.lang.Integer.TYPE,
            java.lang.Float.TYPE
        ).apply { isAccessible = true }
        method.invoke(builder, resolution, cropRect, 0, 1f)
    }

    /**
     * The ColorOS full glass path does not call the public two-argument
     * RenderEffect.createRuntimeShaderEffect(RuntimeShader, String). Its own
     * RenderEffectMultiInput helper reflectively searches for a hidden overload
     * accepting two RenderEffect inputs (raw clock + 10px blurred clock).
     *
     * These probes deliberately do not bypass hidden-API policy. They expose
     * exactly where an ordinary app process loses access while keeping the
     * native-vs-generic experiment fail-closed.
     */
    private fun hiddenMultiInputDiagnostics(): List<String> = buildList {
        if (Build.VERSION.SDK_INT < 33) {
            add("multiInput=not-applicable-sdk<33")
            return@buildList
        }

        add(probeFrameworkMultiInputMethod(includeRadius = true))
        add(probeFrameworkMultiInputMethod(includeRadius = false))

        runCatching {
            val helperClass = loader.loadClass(MULTI_INPUT_CLASS)
            val instance = helperClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                ?: error("INSTANCE is null")
            val resolve = helperClass.getDeclaredMethod("resolveCreateMethod").apply { isAccessible = true }
            val resolved = resolve.invoke(instance) as? Method
            add("vendorMultiInput.resolve=${resolved?.toGenericString() ?: "null"}")
            add(multiInputState(helperClass))
        }.onFailure {
            add("vendorMultiInput.probe=failed:${describeThrowable(it)}")
        }
    }

    private fun probeFrameworkMultiInputMethod(includeRadius: Boolean): String {
        val types = if (includeRadius) {
            arrayOf(
                RuntimeShader::class.java,
                Array<String>::class.java,
                Array<RenderEffect>::class.java,
                java.lang.Float.TYPE
            )
        } else {
            arrayOf(
                RuntimeShader::class.java,
                Array<String>::class.java,
                Array<RenderEffect>::class.java
            )
        }
        val label = if (includeRadius) "frameworkMultiInput4" else "frameworkMultiInput3"
        return runCatching {
            val method = RenderEffect::class.java.getDeclaredMethod("createRuntimeShaderEffect", *types)
            val access = runCatching {
                method.isAccessible = true
                "accessible"
            }.getOrElse { "access-failed:${describeThrowable(it)}" }
            "$label=found:$access:${method.toGenericString()}"
        }.getOrElse {
            "$label=lookup-failed:${describeThrowable(it)}"
        }
    }

    private fun builderFailureDiagnostics(builder: Any): List<String> = buildList {
        val lowAnim = runCatching { invokeOptional(builder, "isLowAnimLevelOrBelow") }
            .fold({ it?.toString() ?: "null" }, { "failed:${describeThrowable(it)}" })
        add("lowAnim=$lowAnim")
        add(fieldSummary(builder, "runtimeShader"))
        add(fieldSummary(builder, "simpleRuntimeShader"))
        add(fieldSummary(builder, "blurEffect"))
        add(fieldSummary(builder, "renderEffect"))
        runCatching { invokeOptional(builder, "getRenderEffectParamsDumpInfo") }
            .onSuccess { add("params=${it?.toString()?.replace('\n', ' ')}") }
            .onFailure { add("params=failed:${describeThrowable(it)}") }
        runCatching {
            val helperClass = loader.loadClass(MULTI_INPUT_CLASS)
            add(multiInputState(helperClass))
        }.onFailure {
            add("multiInputState=failed:${describeThrowable(it)}")
        }
        add(probeFrameworkMultiInputMethod(includeRadius = true))
        add(probeFrameworkMultiInputMethod(includeRadius = false))
    }

    private fun multiInputState(helperClass: Class<*>): String {
        val attempted = helperClass.getDeclaredField("resolveAttempted").apply { isAccessible = true }.getBoolean(null)
        val cached = helperClass.getDeclaredField("cachedMethod").apply { isAccessible = true }.get(null)
        return "vendorMultiInput.state=resolveAttempted:$attempted,cachedMethod:${cached ?: "null"}"
    }

    private fun fieldSummary(receiver: Any, fieldName: String): String = runCatching {
        val field = receiver.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        val value = field.get(receiver)
        "$fieldName=${value?.javaClass?.name ?: "null"}"
    }.getOrElse { "$fieldName=failed:${describeThrowable(it)}" }

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

    private fun classStatus(name: String): String = runCatching {
        val c = loader.loadClass(name)
        "class=$name loader=${c.classLoader}"
    }.getOrElse { "class=$name failed=${describeThrowable(it)}" }

    private fun signature(method: Method): String = buildString {
        append(method.name).append('(')
        append(method.parameterTypes.joinToString { it.simpleName })
        append(") -> ").append(method.returnType.simpleName)
    }

    private fun describeThrowable(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
