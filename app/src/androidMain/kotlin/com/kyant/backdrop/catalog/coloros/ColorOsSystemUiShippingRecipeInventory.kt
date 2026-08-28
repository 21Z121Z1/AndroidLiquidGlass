package com.kyant.backdrop.catalog.coloros

import android.content.Context

/**
 * Expands every shipping SystemUI material preset/config exposed by the installed build into an
 * independently auditable row.
 *
 * This complements class-level DEX discovery: one adapter/provider class can return dozens of
 * distinct material states. DIRECT_SHADER blur/mix configs and PostEffect parameter presets are
 * executable in an ordinary View; motion/platform configs remain SYSTEM_UI_HOST and are never
 * silently replaced by a hand-written shader.
 */
internal class ColorOsSystemUiShippingRecipeInventory(context: Context) {
    companion object {
        const val MATERIAL_PREFIX = "recipe://systemui/material/"
        const val BLUR_MIX_PREFIX = "recipe://systemui/blurmix/"
    }

    private val presetBridge = runCatching { ColorOsSystemUiPresetBridge(context.applicationContext) }
    private val blurMixBridge = runCatching { ColorOsSystemUiBlurMixBridge(context.applicationContext) }

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = buildList {
        presetBridge.getOrNull()?.presets()?.forEach { preset ->
            add(
                ColorOsSystemUiLiquidGlassCatalog.Mapping(
                    group = "SystemUI shipping recipe · ${preset.family}",
                    systemUiImplementation = "$MATERIAL_PREFIX${preset.id}",
                    kyantCounterpart = preset.kyantCounterpart,
                    executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW,
                    status = "available:runtime-systemui-preset",
                    note = "${preset.adapterClass}.${preset.methodName}() → ${preset.returnClass}; 统一 A/B 把这个 exact shipping 参数对象重新注入真实 BlendDrawable。",
                ),
            )
        }

        blurMixBridge.getOrNull()?.recipes()?.forEach { recipe ->
            val direct = recipe.executionHint == ColorOsSystemUiBlurMixBridge.Execution.DIRECT_SHADER
            add(
                ColorOsSystemUiLiquidGlassCatalog.Mapping(
                    group = "SystemUI shipping recipe · BlurMix · ${recipe.source}",
                    systemUiImplementation = "$BLUR_MIX_PREFIX${recipe.id}",
                    kyantCounterpart = recipe.kyantCounterpart,
                    executionMode = if (direct) {
                        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW
                    } else {
                        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST
                    },
                    status = if (direct) "available:runtime-direct-shader-recipe" else "available:runtime-host-only-recipe",
                    note = if (direct) {
                        "${recipe.label}; exact BlurMixConfig → ShaderBlendParamHelper → BlendDrawable，普通 View 可执行。"
                    } else {
                        "${recipe.label}; shipping config 不暴露直接 shader params，保持 SystemUI host-only 边界，不做仿制。"
                    },
                ),
            )
        }
    }.distinctBy { it.systemUiImplementation }
}
