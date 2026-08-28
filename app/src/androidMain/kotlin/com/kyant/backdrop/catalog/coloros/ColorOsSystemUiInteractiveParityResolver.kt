package com.kyant.backdrop.catalog.coloros

/** Precise Kyant contracts for directly executable interactive SystemUI material recipes/classes. */
internal object ColorOsSystemUiInteractiveParityResolver {
    fun resolve(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
    ): ColorOsKyantParityContract.Contract? {
        val impl = mapping.systemUiImplementation

        val spotlight =
            impl.startsWith(ColorOsSystemUiInteractiveRecipeInventory.NOTIFICATION_SPOTLIGHT_PREFIX) ||
                impl.startsWith(ColorOsSystemUiInteractiveRecipeInventory.QS_MEDIA_SPOTLIGHT_PREFIX) ||
                impl == ColorOsSystemUiInteractiveRecipeInventory.VOLUME_SETTINGS_SPOTLIGHT ||
                impl == ColorOsSystemUiInteractiveEffectBridge.NOTIFICATION_SPOTLIGHT ||
                impl == ColorOsSystemUiInteractiveEffectBridge.QS_MEDIA_SPOTLIGHT ||
                impl == ColorOsSystemUiInteractiveEffectBridge.VOLUME_SETTINGS_SPOTLIGHT
        if (spotlight) {
            return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                recipe = ColorOsKyantParityContract.Recipe.SPOTLIGHT,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
                ),
                rationale = "ColorOS 侧直接执行当前 SystemUI 的 shipping spotlight drawable、Canvas 绘制与 MotionEvent 状态；Kyant 没有这些业务 SpotLightType/ClipShape/按压状态机，只能由指针状态驱动 Highlight 作最近机制参考。",
            )
        }

        val metaball =
            impl == ColorOsSystemUiInteractiveRecipeInventory.SCENARIO_METABALL_LIGHT ||
                impl == ColorOsSystemUiInteractiveEffectBridge.METABALL_LIGHT_RENDERER ||
                impl == ColorOsSystemUiInteractiveEffectBridge.SCENARIO_LIGHT_DRAWABLE
        if (metaball) {
            return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                recipe = ColorOsKyantParityContract.Recipe.METABALL_NEAREST,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                    ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                ),
                rationale = "ColorOS 侧通过 shipping ScenarioLightBackgroundDrawable 创建真实 MetaballLightConfig/MetaballLightRenderer，并保留其纹理、动画与光照着色；Kyant 没有 1:1 Metaball 光照 renderer，只能用 Shape/SDF + Highlight 作最近参考。",
            )
        }

        return null
    }
}
