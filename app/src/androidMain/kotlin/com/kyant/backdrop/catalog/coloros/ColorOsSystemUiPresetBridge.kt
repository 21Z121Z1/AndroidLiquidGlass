package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import java.lang.reflect.Method

/**
 * Executes the shipping SystemUI material preset adapters instead of approximating their
 * values in the demo.
 *
 * ColorOS keeps optics, inner-shadow and gradient-stroke recipes in three singleton adapters:
 * - MixColorTileOpticsAdapter
 * - MixColorTileInnerShadowAdapter
 * - MixColorTileStrokeLineAdapter
 *
 * Each zero-argument getter returns the real parameter object used by SystemUI for a concrete
 * state (tile active/inactive, dialog, seekbar, simple header, media output, ...). This bridge
 * discovers every getter at runtime and injects the returned object into a real BlendDrawable.
 */
internal class ColorOsSystemUiPresetBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val BLEND_DRAWABLE = "com.oplus.posteffect.drawable.BlendDrawable"
        private const val BLEND_PARAM = "com.oplus.posteffect.BlendParam"
        private const val CORNER_PARAMS = "com.oplus.posteffect.params.CornerParams"
        private const val CORNER_TYPE = "com.oplus.posteffect.params.CornerType"

        private val SPECS = listOf(
            AdapterSpec(
                family = Family.OPTICS,
                adapterClass = "com.oplusos.systemui.common.adapter.MixColorTileOpticsAdapter",
                returnClass = "com.oplus.posteffect.params.OpticsParams",
                drawableSetter = "setOpticsParams",
                kyant = "Highlight / edge optics（无严格 1:1）",
            ),
            AdapterSpec(
                family = Family.INNER_SHADOW,
                adapterClass = "com.oplusos.systemui.common.adapter.MixColorTileInnerShadowAdapter",
                returnClass = "com.oplus.posteffect.params.InnerShadowParams",
                drawableSetter = "setInnerShadowParams",
                kyant = "InnerShadow",
            ),
            AdapterSpec(
                family = Family.STROKE,
                adapterClass = "com.oplusos.systemui.common.adapter.MixColorTileStrokeLineAdapter",
                returnClass = "com.oplus.posteffect.params.GradientStrokeLineParams",
                drawableSetter = "setGradientStrokeLineParams",
                kyant = "Highlight / stroke",
            ),
        )
    }

    enum class Family { OPTICS, INNER_SHADOW, STROKE }

    data class Preset(
        val id: String,
        val family: Family,
        val methodName: String,
        val adapterClass: String,
        val returnClass: String,
        val drawableSetter: String,
        val kyantCounterpart: String,
        val dark: Boolean,
    ) {
        val displayName: String
            get() = methodName.removePrefix("get").replace("Params", "")
    }

    private data class AdapterSpec(
        val family: Family,
        val adapterClass: String,
        val returnClass: String,
        val drawableSetter: String,
        val kyant: String,
    )

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader = systemUiContext.classLoader

    private val presetCache: List<Preset> by lazy { discoverPresets() }

    fun presets(): List<Preset> = presetCache

    fun summary(): String {
        val all = presetCache
        return "shipping presets=${all.size}; optics=${all.count { it.family == Family.OPTICS }}; " +
            "innerShadow=${all.count { it.family == Family.INNER_SHADOW }}; stroke=${all.count { it.family == Family.STROKE }}"
    }

    fun createPresetDrawable(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        cornerRadiusPx: Float,
        preset: Preset,
    ): Result<Drawable> = runCatching {
        require(width > 0 && height > 0)
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)

        val drawableClass = load(BLEND_DRAWABLE)
        val drawable = drawableClass
            .getDeclaredConstructor(Context::class.java, Boolean::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(systemUiContext, true)
        require(drawable is Drawable) { "$BLEND_DRAWABLE is not Drawable" }

        val blendParam = load(BLEND_PARAM).getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val setBitmapDefault = drawableClass.declaredMethods.firstOrNull {
            it.name == "setBitmap\$default" && it.parameterCount == 3
        } ?: error("BlendDrawable.setBitmap\$default not found")
        setBitmapDefault.isAccessible = true
        setBitmapDefault.invoke(null, drawable, bitmap, blendParam)

        invokeOptional(drawable, "setEnableShader", true)
        invokeOptional(drawable, "setEnableBlurShader", true)
        applyCorner(drawable, cornerRadiusPx)

        val adapterClass = load(preset.adapterClass)
        val instanceField = adapterClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val adapter = instanceField.get(null)
            ?: error("${preset.adapterClass}.INSTANCE is null")
        val expectedReturn = load(preset.returnClass)
        val getter = adapterClass.declaredMethods.firstOrNull {
            it.name == preset.methodName && it.parameterCount == 0 && expectedReturn.isAssignableFrom(it.returnType)
        } ?: error("${preset.adapterClass}.${preset.methodName}() not found")
        getter.isAccessible = true
        val params = getter.invoke(adapter)
            ?: error("${preset.methodName} returned null")

        val result = invokeRequired(drawable, preset.drawableSetter, params)
        if (result is Boolean && !result) error("${preset.drawableSetter} rejected ${preset.methodName}")

        drawable.bounds = Rect(0, 0, width, height)
        drawable
    }

    private fun discoverPresets(): List<Preset> = buildList {
        SPECS.forEach { spec ->
            runCatching {
                val adapter = load(spec.adapterClass)
                val returnType = load(spec.returnClass)
                adapter.declaredMethods
                    .asSequence()
                    .filter { method ->
                        method.parameterCount == 0 &&
                            method.name.startsWith("get") &&
                            returnType.isAssignableFrom(method.returnType)
                    }
                    .sortedBy(Method::getName)
                    .forEach { method ->
                        add(
                            Preset(
                                id = "${spec.family}:${method.name}",
                                family = spec.family,
                                methodName = method.name,
                                adapterClass = spec.adapterClass,
                                returnClass = spec.returnClass,
                                drawableSetter = spec.drawableSetter,
                                kyantCounterpart = spec.kyant,
                                dark = method.name.contains("Dark", ignoreCase = true),
                            ),
                        )
                    }
            }
        }
    }.distinctBy(Preset::id)

    private fun applyCorner(drawable: Any, radiusPx: Float) {
        val cornerTypeClass = load(CORNER_TYPE)
        val g2 = cornerTypeClass.enumConstants
            ?.firstOrNull { (it as Enum<*>).name == "G2" }
            ?: error("CornerType.G2 unavailable")
        val corner = load(CORNER_PARAMS)
            .getDeclaredConstructor(
                cornerTypeClass,
                Float::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!,
            )
            .apply { isAccessible = true }
            .newInstance(g2, radiusPx.coerceAtLeast(0f), 1f)
        val result = invokeRequired(drawable, "setCornerParams", corner)
        if (result is Boolean && !result) error("setCornerParams rejected preset corner")
    }

    private fun invokeRequired(receiver: Any, name: String, arg: Any): Any? {
        val method = findCompatible(receiver.javaClass, name, arg)
            ?: error("${receiver.javaClass.name}.$name(${arg.javaClass.name}) not found")
        method.isAccessible = true
        return method.invoke(receiver, arg)
    }

    private fun invokeOptional(receiver: Any, name: String, arg: Any): Any? {
        val method = findCompatible(receiver.javaClass, name, arg) ?: return null
        method.isAccessible = true
        return method.invoke(receiver, arg)
    }

    private fun findCompatible(clazz: Class<*>, name: String, arg: Any): Method? =
        clazz.declaredMethods.firstOrNull { method ->
            method.name == name && method.parameterCount == 1 && box(method.parameterTypes[0]).isAssignableFrom(arg.javaClass)
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

    private fun load(name: String): Class<*> = loader.loadClass(name)
}
