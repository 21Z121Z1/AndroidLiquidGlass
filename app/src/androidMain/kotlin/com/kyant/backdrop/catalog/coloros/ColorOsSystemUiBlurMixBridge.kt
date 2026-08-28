package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Executes the shipping SystemUI blur/mix recipes instead of reconstructing their colors or
 * blend modes in the demo.
 *
 * The execution path is intentionally narrow:
 *  1. obtain a real BlurMixConfig from QSBlurConfigProvider or NotificationPlatFormBlurParamsManager;
 *  2. when the config owns RuntimeShader blend params, run ShaderBlendParamHelper exactly as
 *     SystemUI does for the requested blur amount;
 *  3. inject the resulting ArrayList<ShaderBlendParam> into a real BlendDrawable.
 *
 * Motion/platform-only configs that do not expose shader params are reported as HOST_ONLY.
 * They are not approximated with a hand-written shader.
 */
internal class ColorOsSystemUiBlurMixBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val QS_PROVIDER = "com.oplusos.systemui.common.util.QSBlurConfigProvider"
        private const val SHADER_HELPER = "com.oplusos.systemui.common.util.ShaderBlendParamHelper"
        private const val NOTIFICATION_MANAGER = "com.oplus.systemui.notification.blur.NotificationPlatFormBlurParamsManager"
        private const val NOTIFICATION_MODE = "com.oplus.systemui.notification.row.material.NotificationBlurMode"
        private const val NOTIFICATION_COLORS = "com.oplus.systemui.notification.blur.NotificationBlurMixColorParams"
        private const val BLEND_DRAWABLE = "com.oplus.posteffect.drawable.BlendDrawable"
        private const val BLEND_PARAM = "com.oplus.posteffect.BlendParam"
        private const val CORNER_PARAMS = "com.oplus.posteffect.params.CornerParams"
        private const val CORNER_TYPE = "com.oplus.posteffect.params.CornerType"

        private val QS_BOOL_FLOAT_METHODS = listOf(
            "getActiveBlurConfig",
            "getDialogPlatformBlurConfig",
            "getInactiveBlurConfig",
            "getScenarioInactiveBlurConfig",
            "getSeekBarActiveBlurConfig",
            "getSeekBarInactiveBlurConfig",
            "getSepActiveBlurConfig",
            "getSepEmptyHolderBlurConfig",
            "getSepInactiveBlurConfig",
            "getStdActiveBlurConfig",
            "getStdInactiveBlurConfig",
        )

        private val QS_BOOL_METHODS = listOf(
            "getDialogMotionBlurConfig",
            "getPrivacyMotionBlurConfig",
        )
    }

    enum class Source { QS, NOTIFICATION }
    enum class Execution { DIRECT_SHADER, HOST_ONLY }

    data class Recipe(
        val id: String,
        val source: Source,
        val label: String,
        val kyantCounterpart: String,
        val dark: Boolean,
        val executionHint: Execution,
        internal val spec: Spec,
    )

    data class Evaluation(
        val configClass: String,
        val execution: Execution,
        val details: String,
    )

    internal sealed interface Spec {
        data class QsBoolFloat(val method: String, val dark: Boolean) : Spec
        data class QsBool(val method: String, val dark: Boolean) : Spec
        data class NotificationNormal(val mode: String, val darken: Boolean) : Spec
        data class NotificationKeyguardStack(val stack: Int, val mode: String) : Spec
        data class NotificationCloseAll(val mode: String) : Spec
    }

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader = systemUiContext.classLoader

    private val recipesCache by lazy { discoverRecipes() }

    fun recipes(): List<Recipe> = recipesCache

    fun defaultQsBlurRadius(): Result<Int> = runCatching {
        val clazz = load(QS_PROVIDER)
        val method = clazz.getDeclaredMethod("getDefaultBlurRadius", Context::class.java).apply { isAccessible = true }
        method.invoke(null, systemUiContext) as Int
    }

    fun notificationResolvedColors(): Result<List<String>> = runCatching {
        val clazz = load(NOTIFICATION_COLORS)
        buildList {
            clazz.declaredMethods
                .asSequence()
                .filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.returnType == Int::class.javaPrimitiveType &&
                        method.name.startsWith("resolve") &&
                        method.name != "resolveColor" &&
                        method.parameterTypes.all { it == Boolean::class.javaPrimitiveType }
                }
                .sortedBy(Method::getName)
                .forEach { method ->
                    method.isAccessible = true
                    if (method.parameterCount == 0) {
                        val value = method.invoke(null) as Int
                        add("${method.name}=#${value.toUInt().toString(16).padStart(8, '0')}")
                    } else {
                        val light = method.invoke(null, false) as Int
                        val dark = method.invoke(null, true) as Int
                        add("${method.name}(false)=#${light.toUInt().toString(16).padStart(8, '0')}")
                        add("${method.name}(true)=#${dark.toUInt().toString(16).padStart(8, '0')}")
                    }
                }
        }
    }

    fun evaluate(recipe: Recipe, amount: Float): Result<Evaluation> = runCatching {
        val config = resolveConfig(recipe.spec, amount.coerceIn(0f, 1f))
        val execution = executionFor(config)
        Evaluation(
            configClass = config.javaClass.name,
            execution = execution,
            details = config.toString(),
        )
    }

    fun createDrawable(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        cornerRadiusPx: Float,
        recipe: Recipe,
        amount: Float,
    ): Result<Drawable> = runCatching {
        require(width > 0 && height > 0)
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)

        val blurAmount = amount.coerceIn(0f, 1f)
        val config = resolveConfig(recipe.spec, blurAmount)
        val execution = executionFor(config)
        require(execution == Execution.DIRECT_SHADER) {
            "${config.javaClass.name} is a motion/platform-host config without direct shader params"
        }

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

        val shaderParams = updateAndExtractShaderParams(config, blurAmount)
        require(shaderParams is java.util.ArrayList<*>) {
            "shader param list is ${shaderParams?.javaClass?.name}"
        }
        val setShaderParams = drawableClass.declaredMethods.firstOrNull {
            it.name == "setShaderBlendParams" && it.parameterCount == 1
        } ?: error("BlendDrawable.setShaderBlendParams(ArrayList) not found")
        setShaderParams.isAccessible = true
        setShaderParams.invoke(drawable, shaderParams)

        val placeholder = invokeOptional(config, "getPlaceHolderColor") as? Int
        if (placeholder != null) invokeOptional(drawable, "setPlaceHolderColor", placeholder)

        drawable.bounds = Rect(0, 0, width, height)
        drawable
    }

    private fun discoverRecipes(): List<Recipe> = buildList {
        QS_BOOL_FLOAT_METHODS.forEach { method ->
            listOf(false, true).forEach { dark ->
                add(
                    Recipe(
                        id = "QS:$method:$dark",
                        source = Source.QS,
                        label = "$method · ${if (dark) "dark" else "light"}",
                        kyantCounterpart = "blur() + vibrancy()/colorControls + surface tint",
                        dark = dark,
                        executionHint = Execution.DIRECT_SHADER,
                        spec = Spec.QsBoolFloat(method, dark),
                    ),
                )
            }
        }
        QS_BOOL_METHODS.forEach { method ->
            listOf(false, true).forEach { dark ->
                add(
                    Recipe(
                        id = "QS:$method:$dark",
                        source = Source.QS,
                        label = "$method · ${if (dark) "dark" else "light"}",
                        kyantCounterpart = "blur() + surface tint；motion host 可能无 RuntimeShader 对应",
                        dark = dark,
                        executionHint = Execution.HOST_ONLY,
                        spec = Spec.QsBool(method, dark),
                    ),
                )
            }
        }

        val modes = enumNames(NOTIFICATION_MODE).filter { it != "UNINITIATED" }
        modes.forEach { mode ->
            val dark = mode.contains("DARK")
            listOf(false, true).forEach { darken ->
                add(
                    Recipe(
                        id = "NOTIF:normal:$mode:$darken",
                        source = Source.NOTIFICATION,
                        label = "normalCardPlatformMixConfig · $mode · darken=$darken",
                        kyantCounterpart = "blur() + notification material tint / shader blend",
                        dark = dark,
                        executionHint = Execution.DIRECT_SHADER,
                        spec = Spec.NotificationNormal(mode, darken),
                    ),
                )
            }
            listOf(1, 2).forEach { stack ->
                add(
                    Recipe(
                        id = "NOTIF:stack:$stack:$mode",
                        source = Source.NOTIFICATION,
                        label = "keyguardStackingPlatformMixConfig · stack=$stack · $mode",
                        kyantCounterpart = "blur() + keyguard stack material tint",
                        dark = dark,
                        executionHint = Execution.DIRECT_SHADER,
                        spec = Spec.NotificationKeyguardStack(stack, mode),
                    ),
                )
            }
            add(
                Recipe(
                    id = "NOTIF:closeAll:$mode",
                    source = Source.NOTIFICATION,
                    label = "closeAllPlatformMixConfig · $mode",
                    kyantCounterpart = "blur() + notification close-all material tint",
                    dark = dark,
                    executionHint = Execution.DIRECT_SHADER,
                    spec = Spec.NotificationCloseAll(mode),
                ),
            )
        }
    }

    private fun resolveConfig(spec: Spec, amount: Float): Any = when (spec) {
        is Spec.QsBoolFloat -> invokeStaticRequired(QS_PROVIDER, spec.method, spec.dark, amount)
            ?: error("${spec.method} returned null")
        is Spec.QsBool -> invokeStaticRequired(QS_PROVIDER, spec.method, spec.dark)
            ?: error("${spec.method} returned null")
        is Spec.NotificationNormal -> {
            val manager = singleton(NOTIFICATION_MANAGER)
            val mode = enumConstant(NOTIFICATION_MODE, spec.mode)
            invokeRequired(manager, "normalCardPlatformMixConfig", mode, spec.darken, "ColorOsLiquidGlassLab")
                ?: error("normalCardPlatformMixConfig returned null")
        }
        is Spec.NotificationKeyguardStack -> {
            val manager = singleton(NOTIFICATION_MANAGER)
            val mode = enumConstant(NOTIFICATION_MODE, spec.mode)
            invokeRequired(manager, "keyguardStackingPlatformMixConfig", spec.stack, mode)
                ?: error("keyguardStackingPlatformMixConfig returned null")
        }
        is Spec.NotificationCloseAll -> {
            val manager = singleton(NOTIFICATION_MANAGER)
            val mode = enumConstant(NOTIFICATION_MODE, spec.mode)
            invokeRequired(manager, "closeAllPlatformMixConfig", mode)
                ?: error("closeAllPlatformMixConfig returned null")
        }
    }

    private fun executionFor(config: Any): Execution = when {
        hasZeroArgMethod(config, "getMixMultiShaderParams") -> Execution.DIRECT_SHADER
        hasZeroArgMethod(config, "getMixSingleShaderParams") -> Execution.DIRECT_SHADER
        hasZeroArgMethod(config, "getMixOverlayColorShaderParams") -> Execution.DIRECT_SHADER
        else -> Execution.HOST_ONLY
    }

    private fun updateAndExtractShaderParams(config: Any, amount: Float): Any? {
        val helper = singleton(SHADER_HELPER)
        return when {
            hasZeroArgMethod(config, "getMixMultiShaderParams") -> {
                invokeCompatibleOptional(helper, "updateMixMultiShaderParams", config, amount)
                invokeRequired(config, "getMixMultiShaderParams")
            }
            hasZeroArgMethod(config, "getMixSingleShaderParams") -> {
                invokeCompatibleOptional(helper, "updateMixSingleShaderParams", config, amount)
                invokeRequired(config, "getMixSingleShaderParams")
            }
            hasZeroArgMethod(config, "getMixOverlayColorShaderParams") -> {
                invokeCompatibleOptional(helper, "updateOverlayColorShaderParams", config, 0xFFFFFFFF.toInt())
                invokeRequired(config, "getMixOverlayColorShaderParams")
            }
            else -> error("${config.javaClass.name} does not expose shader params")
        }
    }

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
        if (result is Boolean && !result) error("setCornerParams rejected G2 corner")
    }

    private fun enumNames(className: String): List<String> =
        load(className).enumConstants?.map { (it as Enum<*>).name }.orEmpty()

    private fun enumConstant(className: String, name: String): Any =
        load(className).enumConstants?.firstOrNull { (it as Enum<*>).name == name }
            ?: error("$className.$name unavailable")

    private fun singleton(className: String): Any {
        val clazz = load(className)
        val field = clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }
        return field.get(null) ?: error("$className.INSTANCE is null")
    }

    private fun invokeStaticRequired(className: String, name: String, vararg args: Any?): Any? {
        val clazz = load(className)
        val method = findCompatible(clazz, name, args, requireStatic = true)
            ?: error("$className.$name(${args.size}) not found")
        method.isAccessible = true
        return method.invoke(null, *args)
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

    private fun invokeCompatibleOptional(receiver: Any, name: String, vararg args: Any?): Any? =
        invokeOptional(receiver, name, *args)

    private fun hasZeroArgMethod(receiver: Any, name: String): Boolean =
        receiver.javaClass.methods.any { it.name == name && it.parameterCount == 0 } ||
            receiver.javaClass.declaredMethods.any { it.name == name && it.parameterCount == 0 }

    private fun findCompatible(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        requireStatic: Boolean,
    ): Method? {
        val candidates = (clazz.declaredMethods.asSequence() + clazz.methods.asSequence()).distinct()
        return candidates.firstOrNull { method ->
            method.name == name &&
                method.parameterCount == args.size &&
                (!requireStatic || Modifier.isStatic(method.modifiers)) &&
                method.parameterTypes.zip(args).all { (type, arg) -> compatible(type, arg) }
        }
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

    private fun load(name: String): Class<*> = loader.loadClass(name)
}
