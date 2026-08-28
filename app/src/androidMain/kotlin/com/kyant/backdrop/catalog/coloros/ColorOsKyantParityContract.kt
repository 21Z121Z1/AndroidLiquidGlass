package com.kyant.backdrop.catalog.coloros

/**
 * Strongly typed parity contract between every ColorOS SystemUI material implementation and the
 * actual Kyant primitives used as the comparison reference.
 *
 * This deliberately does not treat a free-form description as coverage. A CORE_MATERIAL row only
 * passes the parity gate when it resolves to one of these concrete contracts. Where ColorOS owns a
 * business/lifecycle mechanism that Kyant does not model 1:1, the contract remains explicit and is
 * marked NEAREST_ONLY or HOST_LIFECYCLE rather than silently claiming equivalence.
 */
internal object ColorOsKyantParityContract {
    enum class Primitive(val api: String) {
        DRAW_BACKDROP("Modifier.drawBackdrop(...)"),
        DRAW_PLAIN_BACKDROP("Modifier.drawPlainBackdrop(...)"),
        BACKDROP_CAPTURE("rememberLayerBackdrop() / Modifier.layerBackdrop(...)"),
        BACKDROP_LIFECYCLE("Backdrop + exported LayerBackdrop lifecycle"),
        BLUR("BackdropEffectScope.blur(radius)"),
        LENS_REFRACTION("BackdropEffectScope.lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration=false)"),
        LENS_CHROMATIC_ABERRATION("BackdropEffectScope.lens(..., chromaticAberration=true)"),
        VIBRANCY("BackdropEffectScope.vibrancy()"),
        COLOR_CONTROLS("BackdropEffectScope.colorControls(brightness, contrast, saturation)"),
        HIGHLIGHT("Highlight / Modifier.drawBackdrop(highlight=...)"),
        INTERACTIVE_HIGHLIGHT("Highlight driven by pointer/gesture position"),
        INNER_SHADOW("InnerShadow / Modifier.drawBackdrop(innerShadow=...)"),
        OUTER_SHADOW("Shadow / Modifier.drawBackdrop(shadow=...)"),
        SHAPE_SDF("RoundedRectangularShape / RoundedRectangle shape field"),
        RUNTIME_SHADER_EFFECT("BackdropEffectScope runtime RenderEffect chain"),
        SURFACE_TINT("Modifier.drawBackdrop(onDrawSurface=...)"),
    }

    enum class Kind {
        /** Same rendering mechanism exists on both sides, though parameters need not be identical. */
        MECHANISM,

        /** ColorOS implementation is a composition of several Kyant primitives. */
        COMPOSITE,

        /** Kyant only has a nearest visual/mechanical reference; not a 1:1 implementation. */
        NEAREST_ONLY,

        /** ColorOS row mainly owns host/capture/lifecycle policy, while Kyant comparison is transport-level. */
        HOST_LIFECYCLE,
    }

    enum class Recipe {
        MATERIAL_SURFACE,
        BACKDROP_BLUR,
        BLUR_COLOR_MIX,
        REFRACTION,
        CHROMATIC_REFRACTION,
        EDGE_OPTICS,
        STROKE,
        INNER_SHADOW,
        SPOTLIGHT,
        METABALL_NEAREST,
        SHAPE,
        RUNTIME_EFFECT_GRAPH,
        PROGRESSIVE_BLUR,
        BACKDROP_HOST,
    }

    data class Contract(
        val kind: Kind,
        val recipe: Recipe,
        val primitives: Set<Primitive>,
        val rationale: String,
    ) {
        val apiSummary: String get() = primitives.joinToString(" + ") { it.api }
    }

    fun resolve(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Contract? {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        // Shipping shader resources are matched first because their path is more precise than the
        // generic class-family rules below.
        if (impl.startsWith("assets/") || impl.startsWith("res/raw/")) {
            return shaderResourceContract(lower)
        }

        if (impl == "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView") {
            return c(
                Kind.MECHANISM,
                Recipe.PROGRESSIVE_BLUR,
                Primitive.DRAW_PLAIN_BACKDROP,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BLUR,
                rationale = "锁屏渐变模糊遮罩对应 Kyant Backdrop 上的空间/进度模糊参考；不是折射。",
            )
        }
        if (impl == "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar") {
            return c(
                Kind.COMPOSITE,
                Recipe.MATERIAL_SURFACE,
                Primitive.DRAW_BACKDROP,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BLUR,
                Primitive.VIBRANCY,
                Primitive.HIGHLIGHT,
                Primitive.SURFACE_TINT,
                rationale = "真实 QS 滑杆 onDraw 进入 QsSeekBarBlurManager；Kyant 侧用滑杆形状上的 blur + color + highlight 组合。",
            )
        }
        if (impl == "com.oplus.systemui.volume.OplusVolumeSeekBar") {
            return c(
                Kind.COMPOSITE,
                Recipe.MATERIAL_SURFACE,
                Primitive.DRAW_BACKDROP,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BLUR,
                Primitive.HIGHLIGHT,
                Primitive.INNER_SHADOW,
                Primitive.SURFACE_TINT,
                rationale = "真实音量滑杆构造链进入 VolumeBarMaterialHost/StrokeRenderer；Kyant 以相同材质组成层作参考。",
            )
        }

        if (impl.startsWith("com.oplus.posteffect.")) return postEffectContract(lower)
        if (impl.startsWith("com.oplusos.systemui.common.blurability.")) return blurabilityContract(lower)
        if (impl.startsWith("com.oplusos.systemui.common.adapter.MixColor")) {
            return blurColorContract("SystemUI shipping MixColor preset adapter 对应 Kyant 的颜色控制/表面着色机制。")
        }
        if (impl == "com.oplusos.systemui.common.util.QSBlurConfigProvider") {
            return c(
                Kind.COMPOSITE,
                Recipe.BLUR_COLOR_MIX,
                Primitive.DRAW_PLAIN_BACKDROP,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BLUR,
                Primitive.VIBRANCY,
                Primitive.COLOR_CONTROLS,
                Primitive.SURFACE_TINT,
                rationale = "QSBlurConfigProvider 产出 shipping blur/mix 配方；Kyant 对照是 blur + colorControls/vibrancy + surface tint。",
            )
        }
        if (impl == "com.oplusos.systemui.common.util.ShaderBlendParamHelper") {
            return c(
                Kind.MECHANISM,
                Recipe.BLUR_COLOR_MIX,
                Primitive.RUNTIME_SHADER_EFFECT,
                Primitive.VIBRANCY,
                Primitive.COLOR_CONTROLS,
                Primitive.SURFACE_TINT,
                rationale = "ShaderBlendParamHelper 更新 SystemUI RuntimeShader 混色参数；Kyant 对照是 RenderEffect 颜色控制链。",
            )
        }

        if (".notification." in impl) return notificationContract(lower)
        if (".qs." in impl) return qsContract(lower)
        if (".volume." in impl) return volumeContract(lower)
        if (".wallpaperblur." in impl) {
            return c(
                Kind.HOST_LIFECYCLE,
                Recipe.BACKDROP_HOST,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BACKDROP_LIFECYCLE,
                Primitive.BLUR,
                Primitive.SURFACE_TINT,
                rationale = "WallpaperBlur 子系统主要负责输入位图/生命周期；Kyant 对照到 LayerBackdrop 捕获和 blur surface。",
            )
        }
        if (".biometrics.material." in impl) {
            return c(
                Kind.COMPOSITE,
                Recipe.MATERIAL_SURFACE,
                Primitive.DRAW_BACKDROP,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BLUR,
                Primitive.HIGHLIGHT,
                Primitive.INTERACTIVE_HIGHLIGHT,
                Primitive.SURFACE_TINT,
                rationale = "生物识别材质管理器组合模糊、描边/高光与交互光照。",
            )
        }
        if (".panelanimation.platformblur." in impl) {
            return c(
                Kind.HOST_LIFECYCLE,
                Recipe.BACKDROP_HOST,
                Primitive.BACKDROP_CAPTURE,
                Primitive.BACKDROP_LIFECYCLE,
                Primitive.BLUR,
                rationale = "PlatformBlur/MaterialBlurState 负责模糊刷新策略和生命周期；Kyant 对照到 Backdrop 捕获/更新。",
            )
        }
        if (impl.startsWith("com.oplusos.systemui.common.shader.") && "metaball" in lower) {
            return metaballContract("SystemUI Metaball 光照没有 Kyant 1:1 Metaball 求并；以形状场 + Highlight 作为最近机制参考。")
        }

        return null
    }

    private fun postEffectContract(lower: String): Contract = when {
        "continuousblurdrawable" in lower -> c(
            Kind.COMPOSITE,
            Recipe.BACKDROP_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BACKDROP_LIFECYCLE,
            Primitive.BLUR,
            rationale = "ContinuousBlurDrawable 是 live backdrop transport + blur；Kyant 由 LayerBackdrop + blur 对照。",
        )
        "metaballblurdrawable" in lower -> c(
            Kind.NEAREST_ONLY,
            Recipe.METABALL_NEAREST,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SHAPE_SDF,
            Primitive.HIGHLIGHT,
            rationale = "ColorOS 合并 live blur 与 Metaball；Kyant 没有 1:1 Metaball，保留最近的 SDF/highlight 参考。",
        )
        "corner" in lower || "customclip" in lower -> c(
            Kind.MECHANISM,
            Recipe.SHAPE,
            Primitive.SHAPE_SDF,
            rationale = "ColorOS 圆角/裁剪场对应 Kyant 的 RoundedRectangularShape/Shape 边界场。",
        )
        "optic" in lower -> c(
            Kind.NEAREST_ONLY,
            Recipe.EDGE_OPTICS,
            Primitive.DRAW_BACKDROP,
            Primitive.SHAPE_SDF,
            Primitive.HIGHLIGHT,
            rationale = "ColorOS Optics 是 SDF 边缘光学覆盖层，当前证据不是背景坐标折射；Kyant 用 Highlight 作最近参考。",
        )
        "gradientstroke" in lower || "stroke" in lower -> strokeContract("ColorOS 后处理渐变描边对应 Kyant Highlight 边缘层。")
        "innershadow" in lower -> innerShadowContract("ColorOS 内阴影参数对应 Kyant InnerShadow。")
        "metaball" in lower -> metaballContract("DrawableShader Metaball 没有 Kyant 1:1 求并实现。")
        "shaderblend" in lower || "foregroundblur" in lower || "blendparam" in lower -> blurColorContract(
            "ColorOS shader blend/foreground color 层对应 Kyant RenderEffect 颜色控制与 surface tint。",
        )
        "drawableshader" in lower -> c(
            Kind.COMPOSITE,
            Recipe.RUNTIME_EFFECT_GRAPH,
            Primitive.DRAW_BACKDROP,
            Primitive.RUNTIME_SHADER_EFFECT,
            Primitive.SHAPE_SDF,
            Primitive.HIGHLIGHT,
            Primitive.INNER_SHADOW,
            Primitive.SURFACE_TINT,
            rationale = "DrawableShader 是 ColorOS 后处理 effect graph；Kyant 对照到 drawBackdrop + RenderEffect/shape/highlight/shadow 组合。",
        )
        "blenddrawable" in lower || "basedrawable" in lower -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.RUNTIME_SHADER_EFFECT,
            Primitive.SURFACE_TINT,
            rationale = "BlendDrawable 是 ColorOS 后处理容器；Kyant 对照为 drawBackdrop 的背景输入、effect chain 和 surface。",
        )
        else -> c(
            Kind.COMPOSITE,
            Recipe.RUNTIME_EFFECT_GRAPH,
            Primitive.DRAW_BACKDROP,
            Primitive.RUNTIME_SHADER_EFFECT,
            rationale = "未单独命名的 PostEffect 核心类仍属于 ColorOS effect graph；对照到 Kyant Backdrop RenderEffect 图。",
        )
    }

    private fun blurabilityContract(lower: String): Contract = when {
        "innershadow" in lower -> innerShadowContract("SystemUI blurability InnerShadowGroup 对应 Kyant InnerShadow。")
        "stroke" in lower -> strokeContract("SystemUI blurability StrokeLineGroup 对应 Kyant Highlight 边缘层。")
        "mixcolor" in lower || "blurmix" in lower -> blurColorContract("SystemUI blur/mix 配置对应 Kyant blur + color controls/surface tint。")
        "viewblurproxy" in lower || "autoblur" in lower || "platformblur" in lower ||
            "staticblur" in lower || "motionblur" in lower || "staticblurmanager" in lower -> c(
            Kind.HOST_LIFECYCLE,
            Recipe.BACKDROP_HOST,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BACKDROP_LIFECYCLE,
            Primitive.BLUR,
            rationale = "该 blurability 类主要负责 Backdrop 后端选择、捕获或刷新生命周期。",
        )
        "maskblur" in lower -> c(
            Kind.COMPOSITE,
            Recipe.BACKDROP_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SHAPE_SDF,
            rationale = "MaskBlurDrawable 对应 Kyant shape-clipped Backdrop blur。",
        )
        "wallpaper" in lower -> c(
            Kind.HOST_LIFECYCLE,
            Recipe.BLUR_COLOR_MIX,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BACKDROP_LIFECYCLE,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "壁纸 blurability 将壁纸输入继续送入 blur/mix；Kyant 以 LayerBackdrop + blur/surface 对照。",
        )
        "blur" in lower -> c(
            Kind.MECHANISM,
            Recipe.BACKDROP_BLUR,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            rationale = "BlurConfig/blurability 基础模型对应 Kyant BackdropEffectScope.blur。",
        )
        else -> c(
            Kind.HOST_LIFECYCLE,
            Recipe.BACKDROP_HOST,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BACKDROP_LIFECYCLE,
            rationale = "blurability 未细分辅助类默认只映射 Backdrop transport/lifecycle，不宣称像素级等价。",
        )
    }

    private fun notificationContract(lower: String): Contract = when {
        "spotlight" in lower -> spotlightContract("通知按压/触摸聚光对应 Kyant 由手势位置驱动的 Highlight。")
        "metaball" in lower -> metaballContract("通知胶囊 Metaball 没有 Kyant 1:1 求并实现。")
        "stroke" in lower -> strokeContract("通知独立 StrokeShader/adapter 对应 Kyant Highlight 边缘层。")
        "innershadow" in lower -> innerShadowContract("通知 InnerShadowAdapter 对应 Kyant InnerShadow。")
        "blur" in lower || "mixcolor" in lower -> blurColorContract("通知卡片 blur/mix 配方对应 Kyant blur + colorControls/vibrancy + surface。")
        "materialviewwrapper" in lower || ".material." in lower -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "通知材质 wrapper 对应 Kyant drawBackdrop material surface 组合。",
        )
        else -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "通知核心材质辅助类归入通知 Backdrop material composition。",
        )
    }

    private fun qsContract(lower: String): Contract = when {
        "spotlight" in lower -> spotlightContract("QS 聚光/触摸光照对应 Kyant interactive Highlight。")
        "multilight" in lower -> c(
            Kind.NEAREST_ONLY,
            Recipe.SPOTLIGHT,
            Primitive.DRAW_BACKDROP,
            Primitive.SHAPE_SDF,
            Primitive.HIGHLIGHT,
            Primitive.INTERACTIVE_HIGHLIGHT,
            rationale = "MultiLight 是 SystemUI 多光源着色；Kyant 以 Highlight/交互高光作最近机制参考。",
        )
        "metaball" in lower -> metaballContract("QS Metaball 没有 Kyant 1:1 求并实现。")
        "stroke" in lower -> strokeContract("QS GradientStrokeShader/host 对应 Kyant Highlight 边缘层。")
        "progressive" in lower -> c(
            Kind.MECHANISM,
            Recipe.PROGRESSIVE_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            rationale = "QS ProgressiveBlurOverlay 对应 Kyant 以进度驱动 blur 的参考实现。",
        )
        "blur" in lower -> c(
            Kind.COMPOSITE,
            Recipe.BACKDROP_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            rationale = "QS blur manager/config 对应 Kyant Backdrop blur。",
        )
        "material" in lower -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "QS 材质宿主对应 Kyant drawBackdrop material composition。",
        )
        else -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "QS 核心材质辅助类统一落到 QS material composition；不声明业务宿主 1:1。",
        )
    }

    private fun volumeContract(lower: String): Contract = when {
        "spotlight" in lower -> spotlightContract("音量面板聚光对应 Kyant interactive Highlight。")
        "metaball" in lower -> metaballContract("音量 Metaball 没有 Kyant 1:1 求并实现。")
        "stroke" in lower -> strokeContract("VolumeGradientStrokeShader/renderer 对应 Kyant Highlight 边缘层。")
        "blur" in lower -> c(
            Kind.COMPOSITE,
            Recipe.BACKDROP_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            rationale = "VolumeBlurManager 对应 Kyant Backdrop blur。",
        )
        "material" in lower || "geometry" in lower -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.SHAPE_SDF,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.HIGHLIGHT,
            Primitive.SURFACE_TINT,
            rationale = "音量材质/几何宿主对应 Kyant shape + backdrop + blur/highlight/surface composition。",
        )
        else -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            Primitive.HIGHLIGHT,
            rationale = "音量核心材质辅助类归入音量 material composition。",
        )
    }

    private fun shaderResourceContract(lower: String): Contract? = when {
        "chromatic" in lower -> c(
            Kind.MECHANISM,
            Recipe.CHROMATIC_REFRACTION,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.LENS_CHROMATIC_ABERRATION,
            rationale = "SystemUI chromatic shader 是偏移多路采样；Kyant 用 lens(..., chromaticAberration=true) 对照色散子机制。",
        )
        "barglow" in lower || "glow" in lower -> c(
            Kind.NEAREST_ONLY,
            Recipe.EDGE_OPTICS,
            Primitive.HIGHLIGHT,
            Primitive.LENS_CHROMATIC_ABERRATION,
            rationale = "bar glow 同时含距离场发光和色散；Kyant 以 Highlight + chromatic lens 子机制作邻接参考。",
        )
        "metaball" in lower -> metaballContract("SystemUI raw/asset Metaball shader 没有 Kyant 1:1 求并/旋转纹理实现。")
        "blur_down" in lower || "blur_up" in lower || "gaussian" in lower -> c(
            Kind.MECHANISM,
            Recipe.BACKDROP_BLUR,
            Primitive.DRAW_PLAIN_BACKDROP,
            Primitive.BACKDROP_CAPTURE,
            Primitive.BLUR,
            rationale = "SystemUI GL blur pass 与 Kyant blur() 为同类模糊机制，不声称 kernel 相同。",
        )
        "display_fragment" in lower || "display_vertex" in lower || "blend" in lower -> blurColorContract(
            "SystemUI display/blend pass 的 brightness/dither/mix 对照 Kyant colorControls/vibrancy/surface tint。",
        )
        "stroke" in lower -> strokeContract("SystemUI shipping stroke shader 对应 Kyant Highlight 边缘层。")
        "optic" in lower -> c(
            Kind.NEAREST_ONLY,
            Recipe.EDGE_OPTICS,
            Primitive.HIGHLIGHT,
            Primitive.SHAPE_SDF,
            rationale = "SystemUI optics shader 以 SDF 边缘光学为主；Kyant 以 shape + Highlight 作最近参考。",
        )
        "material" in lower -> c(
            Kind.COMPOSITE,
            Recipe.MATERIAL_SURFACE,
            Primitive.DRAW_BACKDROP,
            Primitive.BLUR,
            Primitive.SURFACE_TINT,
            rationale = "SystemUI material shader 资源对照 Kyant material surface composition。",
        )
        else -> null
    }

    private fun blurColorContract(rationale: String) = c(
        Kind.COMPOSITE,
        Recipe.BLUR_COLOR_MIX,
        Primitive.DRAW_PLAIN_BACKDROP,
        Primitive.BACKDROP_CAPTURE,
        Primitive.BLUR,
        Primitive.VIBRANCY,
        Primitive.COLOR_CONTROLS,
        Primitive.SURFACE_TINT,
        rationale = rationale,
    )

    private fun strokeContract(rationale: String) = c(
        Kind.MECHANISM,
        Recipe.STROKE,
        Primitive.DRAW_BACKDROP,
        Primitive.SHAPE_SDF,
        Primitive.HIGHLIGHT,
        rationale = rationale,
    )

    private fun innerShadowContract(rationale: String) = c(
        Kind.MECHANISM,
        Recipe.INNER_SHADOW,
        Primitive.DRAW_BACKDROP,
        Primitive.SHAPE_SDF,
        Primitive.INNER_SHADOW,
        rationale = rationale,
    )

    private fun spotlightContract(rationale: String) = c(
        Kind.MECHANISM,
        Recipe.SPOTLIGHT,
        Primitive.DRAW_BACKDROP,
        Primitive.SHAPE_SDF,
        Primitive.HIGHLIGHT,
        Primitive.INTERACTIVE_HIGHLIGHT,
        rationale = rationale,
    )

    private fun metaballContract(rationale: String) = c(
        Kind.NEAREST_ONLY,
        Recipe.METABALL_NEAREST,
        Primitive.DRAW_BACKDROP,
        Primitive.SHAPE_SDF,
        Primitive.HIGHLIGHT,
        rationale = rationale,
    )

    private fun c(
        kind: Kind,
        recipe: Recipe,
        vararg primitives: Primitive,
        rationale: String,
    ) = Contract(kind, recipe, primitives.toSet(), rationale)
}
