package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.os.Build
import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal class ColorOsClockGlassBridge(context: Context) {
    companion object {
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
        private const val BUILDER_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"
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
            add(classStatus(PARA_TABLE_CLASS))
            add(classStatus(REGION_CLASS))
            runCatching {
                val c = loader.loadClass(BUILDER_CLASS)
                c.declaredMethods
                    .filter { it.name in setOf("init", "setEffectSize", "setWallpaperBg", "setClockRect", "setConfig", "setGlass", "setMixProgress", "setMaskColorProgress", "setEffectLightEnabled", "setOnlyViewContext", "buildRenderEffect", "getRenderEffect", "release") }
                    .sortedBy { it.name }
                    .forEach { add(signature(it)) }
            }
        }
    }

    fun locationColor(): Result<Int> = runCatching {
        val tableClass = loader.loadClass(PARA_TABLE_CLASS)
        val regionClass = loader.loadClass(REGION_CLASS)
        val regions = regionClass.enumConstants ?: error("GlassRegion is not an enum")
        val preferred = listOf("HOUR_TENS", "HOUR", "MINUTE_TENS", "MINUTE", "COLON")
        val region = preferred.asSequence()
            .mapNotNull { name -> regions.firstOrNull { (it as Enum<*>).name == name } }
            .firstOrNull() ?: regions.firstOrNull() ?: error("GlassRegion has no constants")
        val method = tableClass.declaredMethods.firstOrNull {
            it.name == "locationColorFor" && it.parameterCount == 1 && it.parameterTypes[0] == regionClass
        } ?: error("GlassParaTable.locationColorFor(GlassRegion) not found")
        method.isAccessible = true
        val receiver = if (Modifier.isStatic(method.modifiers)) null else {
            runCatching { tableClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null) }
                .getOrElse { tableClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }
        }
        val value = method.invoke(receiver, region)
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

        val builderClass = loader.loadClass(BUILDER_CLASS)
        val builder = builderClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        try {
            val dm = view.resources.displayMetrics
            invokeRequired(builder, "init", dm.widthPixels, dm.heightPixels, false)
            invokeOptional(builder, "setEffectSize", view.width, view.height)
            invokeRequired(builder, "setWallpaperBg", wallpaper, wallpaper)
            applyClockRect(builder, view)
            invokeOptional(builder, "setGlass", glass.coerceIn(0f, 1f))
            invokeOptional(builder, "setMixProgress", mixProgress.coerceIn(0f, 1f))
            invokeOptional(builder, "setMaskColorProgress", maskColorProgress.coerceIn(0f, 1f))
            invokeOptional(builder, "setEffectLightEnabled", lightEnabled)
            invokeOptional(builder, "setOnlyViewContext", true)
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

    private fun applyClockRect(builder: Any, view: View) {
        val method = builder.javaClass.declaredMethods.firstOrNull { it.name == "setClockRect" } ?: return
        method.isAccessible = true
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)
        val l = xy[0]
        val t = xy[1]
        val r = l + view.width
        val b = t + view.height
        when {
            method.parameterCount == 1 && method.parameterTypes[0] == Rect::class.java -> method.invoke(builder, Rect(l, t, r, b))
            method.parameterCount == 1 && method.parameterTypes[0] == RectF::class.java -> method.invoke(builder, RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()))
            method.parameterCount == 4 -> {
                val values = listOf(l, t, r, b)
                val args = method.parameterTypes.mapIndexed { index, type -> numberFor(type, values[index]) }.toTypedArray()
                method.invoke(builder, *args)
            }
        }
    }

    private fun numberFor(type: Class<*>, value: Int): Any = when (type) {
        java.lang.Float.TYPE, java.lang.Float::class.java -> value.toFloat()
        java.lang.Double.TYPE, java.lang.Double::class.java -> value.toDouble()
        java.lang.Long.TYPE, java.lang.Long::class.java -> value.toLong()
        else -> value
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
