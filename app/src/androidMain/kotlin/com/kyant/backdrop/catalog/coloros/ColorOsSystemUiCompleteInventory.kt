package com.kyant.backdrop.catalog.coloros

import android.content.Context

/**
 * Strict inventory = SystemUI runtime scan + proven business entries + framework/library/plugin
 * primitives actually consumed by the system UI material stack + high-recall external-package scan.
 */
internal class ColorOsSystemUiCompleteInventory(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val UX_PACKAGE = "com.oplus.uxdesign"
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
    }

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val systemUiContextResult = runCatching {
        appContext.createPackageContext(SYSTEM_UI_PACKAGE, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
    }

    @Suppress("DEPRECATION")
    private val uxContextResult = runCatching {
        appContext.createPackageContext(UX_PACKAGE, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
    }

    @Suppress("DEPRECATION")
    private val clockContextResult = runCatching {
        appContext.createPackageContext(CLOCK_PACKAGE, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
    }

    private val systemUiLoader: ClassLoader get() = systemUiContextResult.getOrThrow().classLoader
    private val uxLoader: ClassLoader get() = uxContextResult.getOrThrow().classLoader
    private val clockLoader: ClassLoader get() = clockContextResult.getOrThrow().classLoader
    private val runtimeCatalog = ColorOsSystemUiLiquidGlassCatalog(context)
    private val externalCatalog = ColorOsExternalLiquidGlassCatalog(context)

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> {
        val runtime = runtimeCatalog.mappings()
        val runtimeNames = runtime.mapTo(hashSetOf()) { it.systemUiImplementation }

        // Precise required mappings outrank generic external auto-discovery. This is important for
        // known executable COUI/Glass entry points: an auto CAPABILITY_ONLY row must never suppress
        // the already-proven DIRECT_VIEW route.
        val required = requiredMappings().filter { it.systemUiImplementation !in runtimeNames }
        val known = (runtime + required).mapTo(hashSetOf()) { it.systemUiImplementation }
        val external = externalCatalog.mappings().filter { it.systemUiImplementation !in known }

        return (runtime + required + external).sortedWith(
            compareBy<ColorOsSystemUiLiquidGlassCatalog.Mapping> { strictGroupRank(it.group) }
                .thenBy { it.group }
                .thenBy { it.systemUiImplementation },
        )
    }

    private fun requiredMappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = listOf(
        direct(
            "控制中心/QS · 强制入口",
            "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
            "drawBackdrop + blur + colorControls/vibrancy + Highlight",
            "真实业务 View；onDraw 进入 QsSeekBarBlurManager。",
        ),
        direct(
            "音量面板 · 强制入口",
            "com.oplus.systemui.volume.OplusVolumeSeekBar",
            "drawBackdrop + shape + blur + Highlight + InnerShadow",
            "真实业务 View；构造链进入 OplusVolumeBarMaterialHost/OplusVolumeStrokeRenderer。",
        ),
        direct(
            "控制中心/QS · 强制入口",
            "com.oplus.systemui.qs.media.ProgressiveBlurOverlay",
            "drawPlainBackdrop + progress-driven blur()",
            "普通 View 直接执行 setBlurProgress。",
        ),
        direct(
            "通知 · 强制入口",
            "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer",
            "drawPlainBackdrop + masked/progressive blur nearest mechanism",
            "普通 View 直接执行 setMaterialBlur。",
        ),
        direct(
            "锁屏/SystemUI · 强制入口",
            "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView",
            "LayerBackdrop + progress-driven blur()",
            "锁屏渐变模糊遮罩可直接构造并调用 showBlurMask；不是折射。",
        ),
        direct(
            "控制中心/QS · 强制入口",
            "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams",
            "Shape/SDF + Highlight nearest mechanism",
            "直接获取 shipping RuntimeShader；Kyant 没有 1:1 多光源模型。",
        ),
        direct(
            "壁纸 · 强制入口",
            "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable",
            "LayerBackdrop/source bitmap + blur surface tint",
            "已有普通 Drawable 执行桥。",
        ),

        capability(
            "外部框架原语 · 强制入口",
            "com.oplus.graphics.OplusRenderEffect",
            "BackdropEffectScope RenderEffect graph + blur/progressive blur",
            "SystemUI/COUI 使用的 Oplus RenderEffect 工厂。",
        ),
        capability(
            "外部框架原语 · 强制入口",
            "com.oplus.view.OplusViewBackgroundRenderEffect",
            "Backdrop attach/lifecycle + RenderEffect application",
            "把 vendor background RenderEffect 挂到真实 View。",
        ),
        capability(
            "外部框架原语 · 强制入口",
            "com.oplus.view.material.OplusMaterialUtil",
            "Shape/SDF + Highlight + InnerShadow/Shadow material layer",
            "edge/shadow/caustic 等材质参数的框架下沉层。",
        ),

        direct(
            "外部 COUI 材质原语 · 强制入口",
            "com.coui.appcompat.COUIMaterialBlurEffect",
            "blur() + vibrancy/colorControls + surface tint",
            "直接调用设备安装的 COUIMaterialBlurEffect preset。",
        ),
        direct(
            "外部 COUI 材质原语 · 强制入口",
            "com.coui.appcompat.COUIMaterialStrokeEffect",
            "Shape/SDF + Highlight + Shadow",
            "方向性 edge + shadow shipping stroke family。",
        ),
        direct(
            "外部 COUI 材质原语 · 强制入口",
            "com.coui.appcompat.spotlight.COUISpotLightEffect",
            "Highlight nearest mechanism + pointer state",
            "触摸/热点驱动的 shipping spotlight family。",
        ),
        direct(
            "外部 COUI 材质原语 · 强制入口",
            "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate",
            "blur + Highlight/stroke + spotlight + Shadow/caustic composite",
            "Toolbar 组合材质宿主，可同时启用 blur/stroke/spotlight/caustic。",
        ),
        direct(
            "外部 COUI 材质原语 · 强制入口",
            "com.coui.appcompat.toolbar.AppBarBlurHelper",
            "progress-driven / gradient blur()",
            "调用 OplusRenderEffect.createGradientBlurEffect 的渐进模糊宿主。",
        ),

        direct(
            "锁屏插件真折射 · 强制入口",
            "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder",
            "lens(refraction + chromaticAberration) + blur + Highlight",
            "直接加载 personality.clocks 的 GlassEffectBuilder；返回真实 android.graphics.RenderEffect。",
        ),
    )

    private fun direct(group: String, className: String, kyant: String, note: String) = mapping(
        group, className, kyant, ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW, note,
    )

    private fun capability(group: String, className: String, kyant: String, note: String) = mapping(
        group, className, kyant, ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY, note,
    )

    private fun mapping(
        group: String,
        className: String,
        kyant: String,
        mode: ColorOsSystemUiLiquidGlassCatalog.ExecutionMode,
        note: String,
    ) = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = group,
        systemUiImplementation = className,
        kyantCounterpart = kyant,
        executionMode = mode,
        status = classStatus(className),
        note = note,
    )

    private fun classStatus(className: String): String = runCatching {
        loaderFor(className).loadClass(className)
        "available:required-entry"
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun loaderFor(className: String): ClassLoader = when {
        className.startsWith("com.coui.") -> uxLoader
        className.startsWith("com.oplus.keyguard.clock.") -> clockLoader
        else -> systemUiLoader
    }

    private fun strictGroupRank(group: String): Int = when {
        group.startsWith("核心后处理") -> 0
        group.startsWith("SystemUI 着色器") -> 1
        group.startsWith("SystemUI GL") -> 2
        group.startsWith("外部框架原语") -> 3
        group.startsWith("外部 COUI") -> 4
        group.startsWith("公共模糊") -> 5
        group.startsWith("通知") -> 6
        group.startsWith("控制中心") -> 7
        group.startsWith("音量") -> 8
        group.startsWith("锁屏插件") -> 9
        group.startsWith("锁屏") -> 10
        group.startsWith("壁纸") -> 11
        group.startsWith("生物识别") -> 12
        group.startsWith("全局面板") -> 13
        group.startsWith("Metaball") -> 14
        group.startsWith("自动发现 · 外部") -> 18
        group.startsWith("自动发现") -> 20
        else -> 25
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
