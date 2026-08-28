package com.kyant.backdrop.catalog.coloros

/** Precision resolver for infrastructure, external primitives and composite SystemUI families. */
internal object ColorOsSystemUiParityResolver {
    private const val GLASS_BUILDER =
        "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"

    fun resolve(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): ColorOsKyantParityContract.Contract? {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        couiPresetContract(impl)?.let { return it }
        systemUiShippingContract(mapping)?.let { return it }

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

        if (mapping.group.startsWith("自动发现 · 外部")) return externalDiscoveredContract(mapping)

        if (isPostEffectInfrastructure(impl)) return contract(
            ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
            ColorOsKyantParityContract.Recipe.BACKDROP_HOST,
            ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
            ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
            ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
            rationale = "该 PostEffect 类负责配置、连接、资源/状态管理或接口契约；Kyant 在 Backdrop 生命周期/effect graph 层对照。",
        )

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

    private fun systemUiShippingContract(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
    ): ColorOsKyantParityContract.Contract? {
        val impl = mapping.systemUiImplementation
        if (impl.startsWith(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX)) {
            val id = impl.removePrefix(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX)
            return when {
                ":OPTICS:" in id -> contract(
                    ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                    ColorOsKyantParityContract.Recipe.EDGE_OPTICS,
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                    ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    rationale = "该行是一个 exact SystemUI Optics getter 返回值；ColorOS 执行真实参数对象，Kyant 只以 SDF/Highlight 作最近边缘光学参考，不把它误标成背景折射。",
                )
                ":INNER_SHADOW:" in id -> contract(
                    ColorOsKyantParityContract.Kind.MECHANISM,
                    ColorOsKyantParityContract.Recipe.INNER_SHADOW,
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                    rationale = "该行是一个 exact SystemUI InnerShadow getter 返回值；Kyant 以 InnerShadow 对照同类机制，参数曲线不硬编码复刻。",
                )
                ":STROKE:" in id -> contract(
                    ColorOsKyantParityContract.Kind.MECHANISM,
                    ColorOsKyantParityContract.Recipe.STROKE,
                    ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                    ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                    ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                    rationale = "该行是一个 exact SystemUI GradientStrokeLine getter 返回值；Kyant 对照 Shape/SDF + Highlight 的边缘机制。",
                )
                else -> contract(
                    ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                    ColorOsKyantParityContract.Recipe.RUNTIME_EFFECT_GRAPH,
                    ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                    rationale = "未知 shipping material recipe family；保留 effect-graph 最近参考并在 ColorOS 侧执行原参数对象。",
                )
            }
        }

        if (impl.startsWith(ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX)) {
            return if (mapping.executionMode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST) {
                contract(
                    ColorOsKyantParityContract.Kind.HOST_LIFECYCLE,
                    ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BACKDROP_LIFECYCLE,
                    ColorOsKyantParityContract.Primitive.BLUR,
                    ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    rationale = "该 exact BlurMix recipe 是 shipping host-only 配方：没有可直接注入的 shader params；Kyant 只在 backdrop/blur/tint 生命周期层对照，不伪造 direct shader。",
                )
            } else {
                contract(
                    ColorOsKyantParityContract.Kind.COMPOSITE,
                    ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
                    ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                    ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                    ColorOsKyantParityContract.Primitive.BLUR,
                    ColorOsKyantParityContract.Primitive.VIBRANCY,
                    ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
                    ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                    rationale = "该行是一个 exact SystemUI BlurMix direct-shader recipe；ColorOS 运行真实 Config → ShaderBlendParamHelper → BlendDrawable，Kyant 对照 blur + color mix 组成机制。",
                )
            }
        }
        return null
    }

    private fun couiPresetContract(impl: String): ColorOsKyantParityContract.Contract? = when {
        impl.startsWith(ColorOsCouiPresetInventory.BLUR_PREFIX) -> contract(
            ColorOsKyantParityContract.Kind.COMPOSITE,
            ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX,
            ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
            ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
            ColorOsKyantParityContract.Primitive.BLUR,
            ColorOsKyantParityContract.Primitive.VIBRANCY,
            ColorOsKyantParityContract.Primitive.COLOR_CONTROLS,
            ColorOsKyantParityContract.Primitive.SURFACE_TINT,
            rationale = "该行是当前固件一个精确 BlurEffectType preset；Kyant 用 blur + vibrancy/colorControls + surface tint 对照其组成机制，参数不硬编码复刻。",
        )
        impl.startsWith(ColorOsCouiPresetInventory.STROKE_PREFIX) -> contract(
            ColorOsKyantParityContract.Kind.COMPOSITE,
            ColorOsKyantParityContract.Recipe.STROKE,
            ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
            ColorOsKyantParityContract.Primitive.SHAPE_SDF,
            ColorOsKyantParityContract.Primitive.HIGHLIGHT,
            ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
            rationale = "该行是当前固件一个精确 StrokeEffectType preset；Kyant 对照 Shape/SDF + Highlight + Shadow，不声称 edge/shadow 参数曲线一致。",
        )
        impl.startsWith(ColorOsCouiPresetInventory.SPOTLIGHT_PREFIX) -> contract(
            ColorOsKyantParityContract.Kind.NEAREST_ONLY,
            ColorOsKyantParityContract.Recipe.SPOTLIGHT,
            ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
            ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
            rationale = "该行是当前固件一个精确 SpotLightType preset；ColorOS 侧执行真实 hotspot/MotionEvent，Kyant 只能以交互 Highlight 做最近机制参考。",
        )
        impl.startsWith(ColorOsCouiPresetInventory.TOOLBAR_PREFIX) -> contract(
            ColorOsKyantParityContract.Kind.COMPOSITE,
            ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
            ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
            ColorOsKyantParityContract.Primitive.BLUR,
            ColorOsKyantParityContract.Primitive.HIGHLIGHT,
            ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
            ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
            ColorOsKyantParityContract.Primitive.SURFACE_TINT,
            rationale = "该行是当前固件一个精确 Toolbar ViewCategory；ColorOS 侧在该 category 上运行真实 delegate，Kyant 以 blur/stroke/interactive highlight/shadow 组合对照。",
        )
        else -> null
    }

    private fun externalDiscoveredContract(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
    ): ColorOsKyantParityContract.Contract {
        val semantic = (mapping.systemUiImplementation + " " + mapping.kyantCounterpart).lowercase()
        return when {
            listOf("refract", "dispersion", "chromatic", "glass").any(semantic::contains) -> contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.CHROMATIC_REFRACTION,
                ColorOsKyantParityContract.Primitive.DRAW_PLAIN_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.LENS_REFRACTION,
                ColorOsKyantParityContract.Primitive.LENS_CHROMATIC_ABERRATION,
                ColorOsKyantParityContract.Primitive.BLUR,
                rationale = "外部包运行时发现的 glass/refraction/dispersion 实现；Kyant 只在 lens + blur 的机制层建立对照，未知 shader/参数仍保持审计态。",
            )
            "spotlight" in semantic -> contract(
                ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                ColorOsKyantParityContract.Recipe.SPOTLIGHT,
                ColorOsKyantParityContract.Primitive.INTERACTIVE_HIGHLIGHT,
                rationale = "外部 COUI spotlight 辅助类/资源只能映射到 Kyant 交互 Highlight 最近机制；没有证明可独立执行前不声称 1:1。",
            )
            "caustic" in semantic -> contract(
                ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                ColorOsKyantParityContract.Recipe.EDGE_OPTICS,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                rationale = "发现 caustic 命名只证明 shipping 材质层存在；Kyant 以 Highlight/Shadow 作最近参考，不宣称物理焦散等价。",
            )
            "innershadow" in semantic -> contract(
                ColorOsKyantParityContract.Kind.MECHANISM,
                ColorOsKyantParityContract.Recipe.INNER_SHADOW,
                ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                rationale = "外部包 inner-shadow 实现对应 Kyant InnerShadow 机制。",
            )
            "stroke" in semantic || "edge" in semantic -> contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.STROKE,
                ColorOsKyantParityContract.Primitive.SHAPE_SDF,
                ColorOsKyantParityContract.Primitive.HIGHLIGHT,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                rationale = "外部包 stroke/edge 实现对照到 Shape/SDF + Highlight/Shadow；参数曲线保持 vendor-owned。",
            )
            "blur" in semantic || "backdrop" in semantic -> contract(
                ColorOsKyantParityContract.Kind.MECHANISM,
                ColorOsKyantParityContract.Recipe.BACKDROP_BLUR,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.BLUR,
                rationale = "外部包 blur/backdrop 实现对应 Kyant Backdrop + blur 机制；未知捕获后端和采样核不作等价承诺。",
            )
            "shadow" in semantic -> contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.INNER_SHADOW,
                ColorOsKyantParityContract.Primitive.OUTER_SHADOW,
                rationale = "外部材质 shadow 实现以 Kyant Inner/Outer Shadow 组合对照。",
            )
            "material" in semantic -> contract(
                ColorOsKyantParityContract.Kind.COMPOSITE,
                ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE,
                ColorOsKyantParityContract.Primitive.DRAW_BACKDROP,
                ColorOsKyantParityContract.Primitive.BACKDROP_CAPTURE,
                ColorOsKyantParityContract.Primitive.SURFACE_TINT,
                rationale = "运行时发现的外部 material 类/资源先对照 Kyant material surface 组合；专用执行器未证明前保持参数审计。",
            )
            else -> contract(
                ColorOsKyantParityContract.Kind.NEAREST_ONLY,
                ColorOsKyantParityContract.Recipe.RUNTIME_EFFECT_GRAPH,
                ColorOsKyantParityContract.Primitive.RUNTIME_SHADER_EFFECT,
                rationale = "外部包高召回扫描发现相关 effect/shader，但缺少更精确语义；保留 RuntimeEffect 邻接对照并要求运行时证据。",
            )
        }
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
