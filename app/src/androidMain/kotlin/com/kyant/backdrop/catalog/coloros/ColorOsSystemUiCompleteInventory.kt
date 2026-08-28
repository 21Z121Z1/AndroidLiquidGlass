package com.kyant.backdrop.catalog.coloros

import android.content.Context

/**
 * Complete strict-audit inventory = high-recall runtime catalog + explicitly proven material
 * entry points whose class names are not guaranteed to match the catalog's keyword sweep.
 *
 * This is intentionally additive. The DEX/resource scan remains the mechanism for discovering
 * new firmware implementations, while the required rows make it impossible for already-proven
 * business Views/direct probes to disappear merely because their class name lacks "blur",
 * "material", "shader", etc.
 */
internal class ColorOsSystemUiCompleteInventory(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    @Suppress("DEPRECATION")
    private val systemUiContextResult = runCatching {
        context.applicationContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val loader: ClassLoader get() = systemUiContextResult.getOrThrow().classLoader
    private val runtimeCatalog = ColorOsSystemUiLiquidGlassCatalog(context)

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> {
        val runtime = runtimeCatalog.mappings()
        val seen = runtime.mapTo(hashSetOf()) { it.systemUiImplementation }
        val required = requiredMappings().filter { it.systemUiImplementation !in seen }
        return (runtime + required).sortedWith(
            compareBy<ColorOsSystemUiLiquidGlassCatalog.Mapping> { strictGroupRank(it.group) }
                .thenBy { it.group }
                .thenBy { it.systemUiImplementation },
        )
    }

    private fun requiredMappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = listOf(
        direct(
            group = "控制中心/QS · 强制入口",
            className = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
            kyant = "drawBackdrop + blur + colorControls/vibrancy + Highlight",
            note = "真实业务 View；onDraw 进入 QsSeekBarBlurManager。类名本身可能逃过高召回关键词，因此强制纳入。",
        ),
        direct(
            group = "音量面板 · 强制入口",
            className = "com.oplus.systemui.volume.OplusVolumeSeekBar",
            kyant = "drawBackdrop + shape + blur + Highlight + InnerShadow",
            note = "真实业务 View；构造链进入 OplusVolumeBarMaterialHost/OplusVolumeStrokeRenderer。",
        ),
        direct(
            group = "控制中心/QS · 强制入口",
            className = "com.oplus.systemui.qs.media.ProgressiveBlurOverlay",
            kyant = "drawPlainBackdrop + progress-driven blur()",
            note = "普通 View 直接执行 setBlurProgress；即使未来扫描规则改变也不得从严格审计消失。",
        ),
        direct(
            group = "通知 · 强制入口",
            className = "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer",
            kyant = "drawPlainBackdrop + masked/progressive blur nearest mechanism",
            note = "普通 View 直接执行 setMaterialBlur；保持在严格 direct-entry 清单。",
        ),
        direct(
            group = "锁屏/SystemUI · 强制入口",
            className = "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView",
            kyant = "LayerBackdrop + progress-driven blur()",
            note = "锁屏渐变模糊遮罩可直接构造并调用 showBlurMask；不是折射。",
        ),
        direct(
            group = "控制中心/QS · 强制入口",
            className = "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams",
            kyant = "Shape/SDF + Highlight nearest mechanism",
            note = "直接获取 shipping RuntimeShader；Kyant 没有 1:1 多光源模型。",
        ),
        direct(
            group = "壁纸 · 强制入口",
            className = "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable",
            kyant = "LayerBackdrop/source bitmap + blur surface tint",
            note = "已有 createWallpaperBlurDrawable() 普通 Drawable 执行桥，严格审计按 DIRECT_VIEW 处理。",
        ),
    )

    private fun direct(
        group: String,
        className: String,
        kyant: String,
        note: String,
    ) = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = group,
        systemUiImplementation = className,
        kyantCounterpart = kyant,
        executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW,
        status = classStatus(className),
        note = note,
    )

    private fun classStatus(className: String): String = runCatching {
        loader.loadClass(className)
        "available:required-entry"
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun strictGroupRank(group: String): Int = when {
        group.startsWith("核心后处理") -> 0
        group.startsWith("SystemUI 着色器") -> 1
        group.startsWith("SystemUI GL") -> 2
        group.startsWith("公共模糊") -> 3
        group.startsWith("通知") -> 4
        group.startsWith("控制中心") -> 5
        group.startsWith("音量") -> 6
        group.startsWith("锁屏") -> 7
        group.startsWith("壁纸") -> 8
        group.startsWith("生物识别") -> 9
        group.startsWith("全局面板") -> 10
        group.startsWith("Metaball") -> 11
        else -> 20
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
