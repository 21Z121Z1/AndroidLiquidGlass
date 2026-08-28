package com.kyant.backdrop.catalog.coloros

import android.content.Context

/**
 * Expands the installed com.oplus.uxdesign material enums into independently executable rows.
 *
 * Class-level coverage is insufficient for a material lab: two enum values of the same COUI
 * effect can encode different radius, blend, edge, shadow or spotlight behavior. Each visual
 * vendor preset therefore gets its own strict mapping and can be selected directly in the A/B UI.
 * TYPE_NO_EFFECT/NONE-style sentinels are excluded because they intentionally disable material
 * rendering rather than describe a Liquid-Glass/material implementation.
 */
internal class ColorOsCouiPresetInventory(context: Context) {
    companion object {
        const val PREFIX = "preset://coui/"
        const val BLUR_PREFIX = "${PREFIX}blur/"
        const val STROKE_PREFIX = "${PREFIX}stroke/"
        const val SPOTLIGHT_PREFIX = "${PREFIX}spotlight/"
        const val TOOLBAR_PREFIX = "${PREFIX}toolbar/"
    }

    private val bridgeResult = runCatching { ColorOsMaterialBridge(context.applicationContext) }

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = bridgeResult.fold(
        onSuccess = { bridge ->
            val catalog = bridge.catalog()
            buildList {
                catalog.blur.filter(::isVisualPreset).forEach { name ->
                    add(
                        mapping(
                            group = "自动发现 · 外部 COUI shipping preset · Blur",
                            implementation = "$BLUR_PREFIX$name",
                            kyant = "blur() + vibrancy/colorControls + surface tint",
                            note = "当前 uxdesign BlurEffectType.$name；统一 A/B 直接调用 COUIMaterialBlurEffect.apply(view, $name)。",
                        ),
                    )
                }
                catalog.stroke.filter(::isVisualPreset).forEach { name ->
                    add(
                        mapping(
                            group = "自动发现 · 外部 COUI shipping preset · Stroke",
                            implementation = "$STROKE_PREFIX$name",
                            kyant = "Shape/SDF + Highlight + Shadow",
                            note = "当前 uxdesign StrokeEffectType.$name；统一 A/B 直接调用 COUIMaterialStrokeEffect.apply(view, $name)。",
                        ),
                    )
                }
                catalog.spotLight.filter(::isVisualPreset).forEach { name ->
                    add(
                        mapping(
                            group = "自动发现 · 外部 COUI shipping preset · Spotlight",
                            implementation = "$SPOTLIGHT_PREFIX$name",
                            kyant = "InteractiveHighlight nearest mechanism",
                            note = "当前 uxdesign SpotLightType.$name；统一 A/B 直接挂载该 shipping spotlight drawable，并保留 MotionEvent/hotspot 驱动。",
                        ),
                    )
                }
                catalog.toolbarCategories.filter(::isVisualPreset).forEach { name ->
                    add(
                        mapping(
                            group = "自动发现 · 外部 COUI shipping preset · Toolbar category",
                            implementation = "$TOOLBAR_PREFIX$name",
                            kyant = "blur + Highlight/stroke + InteractiveHighlight + Shadow/caustic",
                            note = "当前 uxdesign ViewCategory.$name；A/B 测试在该真实 category 上强制启用 blur/stroke/spotlight/caustic，以隔离并验证完整材质栈；不宣称这四个开关都是业务默认值。",
                        ),
                    )
                }
            }.distinctBy { it.systemUiImplementation }
        },
        onFailure = { emptyList() },
    )

    private fun mapping(
        group: String,
        implementation: String,
        kyant: String,
        note: String,
    ) = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = group,
        systemUiImplementation = implementation,
        kyantCounterpart = kyant,
        executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW,
        status = "available:runtime-vendor-preset",
        note = note,
    )

    private fun isVisualPreset(name: String): Boolean =
        !name.contains("NO_EFFECT", ignoreCase = true) &&
            !name.equals("NONE", ignoreCase = true) &&
            !name.contains("DISABLE", ignoreCase = true)
}
