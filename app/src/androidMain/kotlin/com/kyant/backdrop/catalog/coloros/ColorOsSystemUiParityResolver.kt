package com.kyant.backdrop.catalog.coloros

/** Precision resolver for infrastructure, external primitives and composite SystemUI families. */
internal object ColorOsSystemUiParityResolver {
    private const val GLASS_BUILDER =
        "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"

    fun resolve(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): ColorOsKyantParityContract.Contract? {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        when (impl) {
            "com.oplus.graphics.OplusRenderEffect" -> return contract(
                ColorOsKyantParityContract.Kind.MECHANISM,
                ColorOsKyantParityContract.Recipe.RUNTIME_EFFECT_GRAPH,
                ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                ColorOsKyantParityContract.Primitive.BLUR,
                rationale = "OplusRenderEffect 是框架侧 RenderEffect 工厂/组合原语；Kyant 对照到 BackdropEffectScope 的 RenderEffect 图与 blur/runtime shader effect。",
            )
            "com.oplus.view.OplusViewBackgroundRenderEffect" -> return contract(
                ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                rationale = "OplusViewBackgroundRenderEffect 把 vendor background RenderEffect 挂到真实 View；对应 Kyant Backdrop attach/lifecycle。",
            )
            "com.oplus.view.material.OplusMaterialUtil" -> return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                rationale = "OplusMaterialUtil 是 edge/shadow/caustic 等框架材质参数下沉层。",
            )

            "com.coui.appcompat.COUIMaterialBlurEffect" -> return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BLUR,
                ColorOsKyantParityContract.Primitive.VIBRANCY,
                ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                rationale = "COUIMaterialBlurEffect 是真实背景模糊 + 双层 BlendMode 颜色混合；Kyant 对照 blur + vibrancy/colorControls + surface tint。",
            )
            "com.coui.appcompat.COUIMaterialStrokeEffect" -> return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.STROKE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                rationale = "COUI stroke 同时包含方向性 edge 与 shadow fade；Kyant 用 Shape/SDF + Highlight + Shadow 组合对照。",
            )
            "com.coui.appcompat.spotlight.COUISpotLightEffect" -> return contract(
                ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                ColorOsKyantParityContract.Recipe.SPOTLIGHT,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
                rationale = "COUISpotLightEffect 有真实触摸/热点驱动光照；Kyant 没有 1:1 spotlight 宿主，只能由应用层驱动 Highlight。",
            )
            "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate" -> return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.BLUR,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                rationale = "Toolbar delegate 是 blur + stroke + spotlight + caustic/shadow 的 shipping 组合宿主。",
            )
            "com.coui.appcompat.toolbar.AppBarBlurHelper" -> return contract(
                ColorOsKyantParityContract.Kind.MECHANISM,
                ColorOsKyantParityContract.Recipe.PROGRESSIVE_BLUR,
                ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BLUR,
                rationale = "AppBarBlurHelper 调用 OplusRenderEffect.createGradientBlurEffect；Kyant 对照为进度/空间变化的 blur。",
            )
            GLASS_BUILDER -> return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.CHROMATIC_REFRACTION,
                ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                ColorOsKyantParityContract.Primitive.LENS_REFRACTION,
                ColorOsKyantParityContract.Primitive.LENS_CHROMATIC_ABERRATION,
                ColorOsKyantParityContract.Primitive.BLUR,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                rationale = "GlassEffectBuilder 是已验证的锁屏真实折射/色散 RenderEffect；Kyant 以 lens(chromaticAberration=true) + blur/highlight 做机制组合对照，不宣称采样曲线或色散路数相同。",
            )
        }

        if (isPostEffectInfrastructure(impl)) {
            return contract(
                ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                rationale = "该 PostEffect 类负责配置、连接、资源/状态管理或接口契约；Kyant 在 Backdrop 生命周期/effect graph 层对照。",
            )
        }

        if (impl.startsWith("com.oplusos.systemui.common.util.")) {
            when {
                "gradientstrokelineadapter" in lower || "stroke" in lower -> return contract(
                    ColorOsKyantParityContract.Kind.MECHANISM,
                    ColorOsKyantParityContract.Recipe.STROKE,
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                    ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    rationale = "SystemUI 公共渐变描边 adapter 是参数层；Kyant 对照 shape + Highlight。",
                )
                "blur" in lower -> return contract(
                    ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.BLUR,
                    rationale = "公共平台模糊扩展负责 blur transport/policy；Kyant 对照 Backdrop 生命周期 + blur。",
                )
            }
        }

        if (impl.startsWith("com.oplus.systemui.keyguard.")) {
            return when {
                "gradientmask" in lower || "multilayerblur" in lower -> contract(
                    ColorOsKyantParityContract.Kind.COMPOSITE,
                    ColorOsKyantParityContract.Recipe.PROGRESSIVE_BLUR,
                    ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.BLUR,
                    rationale = "锁屏 gradient-mask/multi-layer blur 是空间或状态驱动模糊；不是折射。",
                )
                "material" in lower -> contract(
                    ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    rationale = "锁屏 material state 属于材质状态/宿主策略。",
                )
                else -> null
            }
        }

        if (impl.startsWith("com.oplus.systemui.blur.")) {
            return if ("color" in lower) contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                ColorOsKyantParityContract.Primitive.BLUR,
                ColorOsKyantParityContract.Primitive.VIBRANCY,
                ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                rationale = "SystemUI blur/color 管理器的颜色调制对应 Kyant blur + color controls。",
            ) else contract(
                ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                ColorOsKyantParityContract.Primitive.BLUR,
                rationale = "SystemUI blur module/startable/utility 负责模糊后端与生命周期。",
            )
        }

        if (impl.startsWith("com.oplus.systemui.notification.")) {
            if ("strokeinnershadow" in lower) return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                rationale = "AOD 通知 StrokeInnerShadow 同时控制描边和内阴影。",
            )
            if ("materialcolor" in lower || lower.endsWith("materialcolormanager")) return contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                ColorOsKyantParityContract.Primitive.VIBRANCY,
                ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                rationale = "通知 MaterialColor* 只负责颜色/模式调制，不额外声称它负责模糊采样。",
            )
        }

        if ("oplusqsdialogblur" in lower) return contract(
            ColorOsKyantParityContract.Kind.COMPOSITE,
            ColorOsKyantParityContract.Recipe.BACKDROP_BLUR,
            ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
            ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
            ColorOsKyantParityContract.Primitive.BLUR,
            ColorOsKyantParityContract.Primitive.SURFACE_TINT,
            rationale = "ColorOS QS dialog blur 对应 Kyant dialog Backdrop blur + surface tint。",
        )

        return ColorOsKyantParityContract.resolve(mapping)
    }

    private fun contract(
        kind: ColorOsKyantParityContract.Kind,
        recipe: ColorOsKyantParityContract.Recipe,
        vararg primitives: ColorOsKyantParityContract.Primitive,
        rationale: String,
    ) = ColorOsKyantParityContract.Contract(kind, recipe, primitives.toSet(), rationale)

    private fun isPostEffectInfrastructure(impl: String): Boolean {
        if (!impl.startsWith("com.oplus.posteffect.")) return false
        return listOf(".config.", ".contract.", ".interfaces.", ".manager.", ".image.", ".util.").any(impl::contains) ||
            impl.endsWith("ConfigurationObserver") || impl.endsWith("ConfigurationObserverImpl")
    }
}
