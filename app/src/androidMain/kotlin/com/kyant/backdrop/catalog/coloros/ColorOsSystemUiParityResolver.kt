package com.kyant.backdrop.catalog.coloros

/**
 * Precision layer in front of ColorOsKyantParityContract.
 *
 * The base contract covers visual families broadly. This resolver intercepts infrastructure,
 * host-policy, framework primitives and extra SystemUI material families so they are compared to
 * Kyant at the correct abstraction level instead of being mislabeled as standalone pixel shaders.
 */
internal object ColorOsSystemUiParityResolver {
    fun resolve(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): ColorOsKyantParityContract.Contract? {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        when (impl) {
            "com.oplus.graphics.OplusRenderEffect" -> return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.MECHANISM,
                recipe = ColorOsKyantParityContract.Recipe.RUNTIME_EFFECT_GRAPH,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                    ColorOsKyantParityContract.Primitive.BLUR,
                ),
                rationale = "OplusRenderEffect 是框架侧 RenderEffect 工厂/组合原语；Kyant 对照到 BackdropEffectScope 的 RenderEffect 图与 blur/runtime shader effect。",
            )
            "com.oplus.view.OplusViewBackgroundRenderEffect" -> return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                recipe = ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                ),
                rationale = "OplusViewBackgroundRenderEffect 负责把 vendor background RenderEffect 挂到真实 View；Kyant 对照的是 drawBackdrop/Backdrop attach 与生命周期层。",
            )
            "com.oplus.view.material.OplusMaterialUtil" -> return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                recipe = ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                    ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                    ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                ),
                rationale = "OplusMaterialUtil 是 edge/shadow/caustic 等框架材质参数的下沉层；Kyant 以 Shape/SDF + Highlight + Inner/Outer Shadow 组合对照。",
            )
        }

        if (isPostEffectInfrastructure(impl)) {
            return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                recipe = ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                ),
                rationale = "该 PostEffect 类负责配置、连接、资源/状态管理或接口契约；Kyant 应在 Backdrop 捕获/生命周期/effect graph 层对照，而不是伪装成独立像素效果。",
            )
        }

        if (impl.startsWith("com.oplusos.systemui.common.util.")) {
            when {
                "gradientstrokelineadapter" in lower || "stroke" in lower -> return ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.MECHANISM,
                    recipe = ColorOsKyantParityContract.Recipe.STROKE,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                        ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                        ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    ),
                    rationale = "SystemUI 公共渐变描边 adapter 是参数层；Kyant 对照到 shape + Highlight 边缘机制，不把 adapter 当 shader。",
                )
                "blur" in lower -> return ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    recipe = ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                        ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                        ColorOsKyantParityContract.Primitive.BLUR,
                    ),
                    rationale = "Notifi/QS 公共平台模糊扩展负责 blur transport/policy；Kyant 对照到 Backdrop 生命周期 + blur。",
                )
            }
        }

        if (impl.startsWith("com.oplus.systemui.keyguard.")) {
            return when {
                "gradientmask" in lower || "multilayerblur" in lower -> ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                    recipe = ColorOsKyantParityContract.Recipe.PROGRESSIVE_BLUR,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                        ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                        ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                        ColorOsKyantParityContract.Primitive.BLUR,
                    ),
                    rationale = "锁屏 gradient-mask/multi-layer blur 是空间或状态驱动的模糊层；对应 Kyant LayerBackdrop + progress-driven blur，而非 lens 折射。",
                )
                "material" in lower -> ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    recipe = ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                        ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                        ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    ),
                    rationale = "锁屏 material state 属于材质状态/宿主策略；Kyant 以 drawBackdrop surface 与生命周期作为同层级对照。",
                )
                else -> null
            }
        }

        if (impl.startsWith("com.oplus.systemui.blur.")) {
            return when {
                "color" in lower -> ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                    recipe = ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.BLUR,
                        ColorOsKyantParityContract.Primitive.VIBRANCY,
                        ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                        ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    ),
                    rationale = "SystemUI blur/color 管理器的颜色调制对应 Kyant blur + vibrancy/colorControls + surface tint。",
                )
                else -> ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    recipe = ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                        ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                        ColorOsKyantParityContract.Primitive.BLUR,
                    ),
                    rationale = "SystemUI blur module/startable/utility 负责模糊后端与生命周期，Kyant 对照到 Backdrop transport + blur。",
                )
            }
        }

        if (impl.startsWith("com.oplus.systemui.notification.")) {
            if ("strokeinnershadow" in lower) {
                return ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                    recipe = ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                        ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                        ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                        ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                    ),
                    rationale = "AOD 通知 StrokeInnerShadow 同时控制描边和内阴影；Kyant 必须组合 Highlight + InnerShadow，而不是只映射其中一半。",
                )
            }
            if ("materialcolor" in lower || lower.endsWith("materialcolormanager")) {
                return ColorOsKyantParityContract.Contract(
                    kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                    recipe = ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                    primitives = setOf(
                        ColorOsKyantParityContract.Primitive.VIBRANCY,
                        ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                        ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    ),
                    rationale = "通知 MaterialColor* 负责材质颜色/模式调制；Kyant 对照到 colorControls/vibrancy + surface tint，不额外声称它负责模糊采样。",
                )
            }
        }

        if ("oplusqsdialogblur" in lower) {
            return ColorOsKyantParityContract.Contract(
                kind = ColorOsKyantParityContract.Kind.COMPOSITE,
                recipe = ColorOsKyantParityContract.Recipe.BACKDROP_BLUR,
                primitives = setOf(
                    ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BLUR,
                    ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                ),
                rationale = "ColorOS QS dialog blur background 对应 Kyant dialog Backdrop blur + surface tint。",
            )
        }

        return ColorOsKyantParityContract.resolve(mapping)
    }

    private fun isPostEffectInfrastructure(impl: String): Boolean {
        if (!impl.startsWith("com.oplus.posteffect.")) return false
        return listOf(
            ".config.",
            ".contract.",
            ".interfaces.",
            ".manager.",
            ".image.",
            ".util.",
        ).any(impl::contains) ||
            impl.endsWith("ConfigurationObserver") ||
            impl.endsWith("ConfigurationObserverImpl")
    }
}
