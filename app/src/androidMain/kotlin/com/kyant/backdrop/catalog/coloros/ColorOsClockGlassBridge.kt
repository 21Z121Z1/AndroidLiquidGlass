package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.os.Build
import android.view.View
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
        private const val VEC2_CLASS = "$BUILDER_CLASS\$Vec2"
        private const val VEC4_CLASS = "$BUILDER_CLASS\$Vec4"
        private const val PARA_TABLE_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassParaTable"
        private const val REGION_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassRegion"
    }

    private val hostContext = context.applicationContext
    private val retained = WeakHashMap<View, Any>()

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
        add("packageContext=${vendorContextResult.fold({ "loaded:${it.packageName}" }, { "failed:${it.javaClass.simpleName}:${it.message}" })}")
        if (vendorContextResult.isSuccess) {
            add(classStatus(BUILDER_CLASS))
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
                            "release"
                        )
                    }
                    .sortedBy { it.name }
                    .forEach { add(signature(it)) }
            }
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
            applyWallpaperGeometry(builder, wallpaper)

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
                ?: error("getRenderEffect() did not return android.graphics.RenderEffect")
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
     * The comparison bitmap is already normalized to display orientation, so
     * it has a full-frame crop, zero content rotation and unit display scale.
     */
    private fun applyWallpaperGeometry(builder: Any, wallpaper: Bitmap) {
        val vec2Class = loader.loadClass(VEC2_CLASS)
        val vec4Class = loader.loadClass(VEC4_CLASS)
        val resolution = vec2Class
            .getDeclaredConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(wallpaper.width.toFloat(), wallpaper.height.toFloat())
        val cropRect = vec4Class
            .getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            .apply { isAccessible = true }
            .newInstance(0f, 0f, wallpaper.width.toFloat(), wallpaper.height.toFloat())
        val method = builder.javaClass.getDeclaredMethod(
            "setClockRect",
            vec2Class,
            vec4Class,
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        method.invoke(builder, resolution, cropRect, 0, 1f)
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

    private fun classStatus(name: String): String = runCatching {
        val c = loader.loadClass(name)
        "class=$name loader=${c.classLoader}"
    }.getOrElse { "class=$name failed=${it.javaClass.simpleName}:${it.message}" }

    private fun signature(method: Method): String = buildString {
        append(method.name).append('(')
        append(method.parameterTypes.joinToString { it.simpleName })
        append(") -> ").append(method.returnType.simpleName)
    }
}
