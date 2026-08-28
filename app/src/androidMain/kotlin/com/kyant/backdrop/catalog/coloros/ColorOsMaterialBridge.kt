package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * Runtime bridge to ColorOS 17 COUI/Oplus material effects from the installed
 * com.oplus.uxdesign package. No look-alike shader is bundled here.
 */
internal class ColorOsMaterialBridge(appContext: Context) {
    companion object {
        private const val UX_PACKAGE = "com.oplus.uxdesign"
        private const val BLUR_CLASS = "com.coui.appcompat.COUIMaterialBlurEffect"
        private const val BLUR_ENUM = "com.coui.appcompat.COUIMaterialBlurEffect\$BlurEffectType"
        private const val STROKE_CLASS = "com.coui.appcompat.COUIMaterialStrokeEffect"
        private const val STROKE_ENUM = "com.coui.appcompat.COUIMaterialStrokeEffect\$StrokeEffectType"
        private const val SPOTLIGHT_CLASS = "com.coui.appcompat.spotlight.COUISpotLightEffect"
        private const val SPOTLIGHT_ENUM = "com.coui.appcompat.spotlight.COUISpotLightEffect\$SpotLightType"
        private const val SPOTLIGHT_DRAWABLE = "com.coui.appcompat.spotlight.a"
        private const val TOOLBAR_DELEGATE = "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate"
        private const val TOOLBAR_CATEGORY = "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate\$ViewCategory"
        private const val APP_BAR_BLUR_HELPER = "com.coui.appcompat.toolbar.AppBarBlurHelper"
        private const val GRADIENT_BLUR_CONFIG = "com.coui.appcompat.toolbar.AppBarBlurHelper\$GradientBlurConfig"
    }

    private val hostContext = appContext.applicationContext

    @Suppress("DEPRECATION")
    private val materialContextResult = runCatching {
        hostContext.createPackageContext(
            UX_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val materialContext: Context get() = materialContextResult.getOrThrow()
    private val loader: ClassLoader get() = materialContext.classLoader
    private val retained = WeakHashMap<View, Any>()

    data class RuntimeCatalog(
        val blur: List<String>,
        val stroke: List<String>,
        val spotLight: List<String>,
        val toolbarCategories: List<String>,
    )

    fun catalog(): RuntimeCatalog = RuntimeCatalog(
        blur = enumNames(BLUR_ENUM),
        stroke = enumNames(STROKE_ENUM),
        spotLight = enumNames(SPOTLIGHT_ENUM),
        toolbarCategories = enumNames(TOOLBAR_CATEGORY),
    )

    fun diagnostics(): List<String> = buildList {
        add("sourcePackage=$UX_PACKAGE")
        add("packageContext=${materialContextResult.fold({ "loaded:${it.packageName}" }, { "failed:${describe(it)}" })}")
        if (materialContextResult.isSuccess) {
            add("blur=${status(BLUR_CLASS)}")
            add("gradientBlur=${status(APP_BAR_BLUR_HELPER)}")
            add("stroke=${status(STROKE_CLASS)}")
            add("spotLight=${status(SPOTLIGHT_CLASS)}")
            add("toolbar=${status(TOOLBAR_DELEGATE)}")
            add("OplusMaterialUtil=${status("com.oplus.view.material.OplusMaterialUtil")}")
            add("OplusRenderEffect=${status("com.oplus.graphics.OplusRenderEffect")}")
            add("OplusViewBackgroundRenderEffect=${status("com.oplus.view.OplusViewBackgroundRenderEffect")}")
        }
    }

    fun applyBlur(view: View, typeName: String): Result<Unit> =
        applyCompanionPreset(BLUR_CLASS, BLUR_ENUM, view, typeName)

    fun applyStroke(view: View, typeName: String): Result<Unit> =
        applyCompanionPreset(STROKE_CLASS, STROKE_ENUM, view, typeName)

    fun applyGradientBlur(view: View, fraction: Float): Result<Unit> = runCatching {
        retained.remove(view)?.let { old -> clearRetained(view, old) }
        val helperClass = load(APP_BAR_BLUR_HELPER)
        val configClass = load(GRADIENT_BLUR_CONFIG)
        val helper = helperClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val config = configClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        invokeExact(helper, "setGradientBlurConfig", arrayOf(View::class.java, configClass), view, config)
        invokeExact(helper, "setUpGradientBlurBackground", arrayOf(View::class.java), view)
        invokeExact(
            helper,
            "updateGradientBlurFraction",
            arrayOf(View::class.java, Float::class.javaPrimitiveType!!),
            view,
            fraction.coerceIn(0f, 1f),
        )
        retained[view] = helper
    }

    fun updateGradientBlur(view: View, fraction: Float): Result<Unit> = runCatching {
        val helper = retained[view] ?: return@runCatching applyGradientBlur(view, fraction).getOrThrow()
        invokeExact(
            helper,
            "updateGradientBlurFraction",
            arrayOf(View::class.java, Float::class.javaPrimitiveType!!),
            view,
            fraction.coerceIn(0f, 1f),
        )
    }

    fun applySpotLight(view: View, typeName: String): Result<Unit> = runCatching {
        val enumClass = load(SPOTLIGHT_ENUM)
        val constant = enumConstant(enumClass, typeName)
            ?: error("Unknown ColorOS spotlight preset: $typeName")
        val drawableClass = load(SPOTLIGHT_DRAWABLE)
        val drawable = drawableClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        require(drawable is Drawable) { "$SPOTLIGHT_DRAWABLE is not a Drawable" }

        val configure = drawableClass.declaredMethods.firstOrNull { method ->
            val p = method.parameterTypes
            p.size == 2 && Context::class.java.isAssignableFrom(p[0]) && p[1] == enumClass &&
                drawableClass.isAssignableFrom(method.returnType)
        } ?: error("ColorOS spotlight configure method not found")
        configure.isAccessible = true
        val configured = configure.invoke(drawable, materialContext, constant) as? Drawable ?: drawable

        val motionMethod = drawableClass.declaredMethods.firstOrNull { method ->
            val p = method.parameterTypes
            p.size == 1 && p[0] == MotionEvent::class.java && method.returnType == Void.TYPE
        }?.apply { isAccessible = true }

        view.foreground = configured
        view.setOnTouchListener { touchedView, event ->
            configured.setHotspot(event.x, event.y)
            runCatching { motionMethod?.invoke(configured, event) }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touchedView.isPressed = true
                MotionEvent.ACTION_UP -> {
                    touchedView.isPressed = false
                    touchedView.performClick()
                }
                MotionEvent.ACTION_CANCEL -> touchedView.isPressed = false
            }
            false
        }
        retained[view] = configured
    }

    fun applyToolbarStack(
        view: View,
        categoryName: String,
        blur: Boolean,
        stroke: Boolean,
        spotLight: Boolean,
        caustic: Boolean,
        forceEnable: Boolean = true,
    ): Result<Unit> = runCatching {
        retained.remove(view)?.let { old -> clearRetained(view, old) }
        val delegateClass = load(TOOLBAR_DELEGATE)
        val categoryClass = load(TOOLBAR_CATEGORY)
        val category = enumConstant(categoryClass, categoryName)
            ?: error("Unknown ColorOS toolbar category: $categoryName")

        val create = delegateClass.declaredMethods.firstOrNull { method ->
            val p = method.parameterTypes
            method.name == "create" && p.size == 2 && View::class.java.isAssignableFrom(p[0]) && p[1] == categoryClass
        } ?: error("ToolbarMaterialEffectDelegate.create(View, ViewCategory) not found")
        create.isAccessible = true
        val receiver = if (Modifier.isStatic(create.modifiers)) null else {
            delegateClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }
        val delegate = create.invoke(receiver, view, category)
            ?: error("ColorOS toolbar delegate creation returned null")

        if (forceEnable) {
            val forceValue = runCatching {
                delegateClass.getDeclaredField("MATERIAL_EFFECT_FORCE_ENABLE").apply { isAccessible = true }.getInt(null)
            }.getOrDefault(2)
            invokeNamed(delegate, "setUseMaterialEffectAttr", forceValue)
        }
        invokeNamed(delegate, "setEnabled", true)
        invokeNamed(delegate, "setBlurOn", blur)
        invokeNamed(delegate, "setStrokeOn", stroke)
        invokeNamed(delegate, "setSpotLightOn", spotLight)
        invokeNamed(delegate, "setCausticShadowOn", caustic)
        invokeNamed(delegate, "reapplyAll")
        retained[view] = delegate
    }

    fun clear(view: View) {
        runCatching { applyBlur(view, "TYPE_NO_EFFECT").getOrThrow() }
        runCatching { applyStroke(view, "TYPE_NO_EFFECT").getOrThrow() }
        retained.remove(view)?.let { runCatching { clearRetained(view, it) } }
        view.foreground = null
        view.setOnTouchListener(null)
    }

    private fun clearRetained(view: View, retainedObject: Any) {
        retainedObject.javaClass.declaredMethods
            .firstOrNull { it.name == "clearGradientBlur" && it.parameterCount == 1 }
            ?.apply { isAccessible = true }
            ?.invoke(retainedObject, view)
        retainedObject.javaClass.declaredMethods
            .firstOrNull { it.name == "removeAllEffects" && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.invoke(retainedObject)
    }

    private fun applyCompanionPreset(
        effectClassName: String,
        enumClassName: String,
        view: View,
        typeName: String,
    ): Result<Unit> = runCatching {
        val effectClass = load(effectClassName)
        val enumClass = load(enumClassName)
        val constant = enumConstant(enumClass, typeName)
            ?: error("Unknown ColorOS material preset: $typeName")
        val companion = effectClass.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            ?: error("$effectClassName.Companion is null")
        val apply = companion.javaClass.declaredMethods.firstOrNull { method ->
            val p = method.parameterTypes
            p.size == 2 && View::class.java.isAssignableFrom(p[0]) && p[1] == enumClass && method.returnType == Void.TYPE
        } ?: error("Preset apply method not found for $effectClassName")
        apply.isAccessible = true
        apply.invoke(companion, view, constant)
    }

    private fun invokeNamed(receiver: Any, name: String, vararg args: Any) {
        val method = receiver.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterCount == args.size
        } ?: error("${receiver.javaClass.name}.$name not found")
        method.isAccessible = true
        method.invoke(receiver, *args)
    }

    private fun invokeExact(receiver: Any, name: String, types: Array<Class<*>>, vararg args: Any) {
        val method = receiver.javaClass.getDeclaredMethod(name, *types)
        method.isAccessible = true
        method.invoke(receiver, *args)
    }

    private fun enumNames(className: String): List<String> = runCatching {
        load(className).enumConstants?.map { (it as Enum<*>).name }.orEmpty()
    }.getOrDefault(emptyList())

    private fun enumConstant(enumClass: Class<*>, name: String): Any? =
        enumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == name }

    private fun status(className: String): String =
        runCatching { load(className); "available" }.getOrElse { "unavailable:${describe(it)}" }

    private fun load(className: String): Class<*> = loader.loadClass(className)

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
