package com.kyant.backdrop.catalog.coloros

/**
 * Explicit difference model for a ColorOS SystemUI material implementation versus its Kyant
 * reference. This prevents a parity contract from being read as a pixel-identical claim.
 */
internal object ColorOsKyantDelta {
    enum class Grade {
        EXACT_MECHANISM,
        COMPOSITE_EQUIVALENT,
        NEAREST_ONLY,
        HOST_ONLY,
    }

    data class Delta(
        val grade: Grade,
        val colorOsSpecific: List<String>,
        val kyantLimit: List<String>,
        val note: String,
    )

    private const val GLASS_BUILDER =
        "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"

    fun resolve(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
        contract: ColorOsKyantParityContract.Contract?,
        route: ColorOsSystemUiExecutionRegistry.Route?,
    ): Delta? {
        contract ?: return null
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        val grade = when (contract.kind) {
            ColorOsKyantParityContract.Kind.MECHANISM -> Grade.EXACT_MECHANISM
            ColorOsKyantParityContract.Kind.COMPOSITE -> Grade.COMPOSITE_EQUIVALENT
            ColorOsKyantParityContract.Kind.NEAREST_ONLY -> Grade.NEAREST_ONLY
            ColorOsKyantParityContract.Kind.HOST_LIFECYCLE -> Grade.HOST_ONLY
        }

        val colorOsSpecific = buildList {
            when (impl) {
                "com.coui.appcompat.COUIMaterialBlurEffect" -> add(
                    "COUI blur 使用 shipping BlurEffectParams 与两层 BlendMode 颜色混合，并通过 OplusViewBackgroundRenderEffect 挂接真实后景。",
                )
                "com.coui.appcompat.COUIMaterialStrokeEffect" -> add(
                    "COUI stroke 同时拥有方向性 edge 参数和 shadow fade；不是单一静态描边。",
                )
                "com.coui.appcompat.spotlight.COUISpotLightEffect" -> add(
                    "COUI spotlight 由真实 MotionEvent/hotspot 与 pressed state 驱动 shipping 前景光照。",
                )
                "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate" -> add(
                    "Toolbar delegate 在同一业务宿主中编排 blur、stroke、spotlight 与 caustic-shadow 开关。",
                )
                "com.coui.appcompat.toolbar.AppBarBlurHelper" -> add(
                    "AppBarBlurHelper 下沉到 OplusRenderEffect.createGradientBlurEffect 的 vendor 渐进模糊实现。",
                )
                GLASS_BUILDER -> {
                    add("锁屏 GlassEffectBuilder 使用由 clock alpha 经 10px blur 形成的软场作为额外 RuntimeShader 输入。")
                    add("完整玻璃 shader 对 wallpaper 坐标做位移采样，并对 RGB 通道执行分离采样形成色散。")
                    add("该路径依赖 ColorOS 隐藏的多输入 RenderEffect.createRuntimeShaderEffect 重载，并由 personality-clocks 自己构建 RenderEffect。")
                }
            }

            when {
                "metaball" in lower -> add("ColorOS 拥有 Metaball 形状融合/光照或 SurfaceControl 组合；Kyant 没有 1:1 Metaball 求并原语")
                "spotlight" in lower && impl != "com.coui.appcompat.spotlight.COUISpotLightEffect" ->
                    add("ColorOS 有业务触摸/指针驱动的聚光宿主与 shipping preset")
                "multilight" in lower -> add("ColorOS 有独立多光源 RuntimeShader 参数模型")
                "chromatic" in lower -> add("SystemUI chromatic.agsl 是独立偏移多路采样后处理，不等于完整玻璃 lens")
                "barglow" in lower -> add("SystemUI bar glow 将距离场发光与色散组合在同一 shipping shader")
                "optic" in lower -> add("ColorOS Optics 是 SDF 边缘光学覆盖层；现有证据不支持把它标成背景坐标折射")
            }
            if (
                route?.kind == ColorOsSystemUiExecutionRegistry.Kind.SURFACE_CONTROL_BOUND ||
                "continuousblur" in lower || "platformblur" in lower
            ) {
                add("ColorOS 路径可依赖 SurfaceControl/窗口级 live backdrop transport")
            }
            if ("gradientstroke" in lower || "strokeshader" in lower) {
                add("ColorOS shipping 描边具有业务专用 near/far、圆角场和渐变参数")
            }
            if ("blurmix" in lower || "mixcolor" in lower || "materialcolor" in lower) {
                add("ColorOS 颜色/混色由 SystemUI shipping 配方和 BlendMode 组合驱动")
            }
        }

        val kyantLimit = buildList {
            when (impl) {
                "com.coui.appcompat.COUIMaterialBlurEffect" -> add(
                    "Kyant 用 blur + vibrancy/colorControls + surface tint 对照组成机制，不复刻 COUI 每个 BlendMode preset 的像素曲线。",
                )
                "com.coui.appcompat.COUIMaterialStrokeEffect" -> add(
                    "Kyant Highlight + Shadow 能对照边缘/阴影组成，但不是 COUI OplusMaterialEdgeParams/ShadowParams 的同一参数模型。",
                )
                "com.coui.appcompat.spotlight.COUISpotLightEffect" -> add(
                    "Kyant 没有 COUI foreground drawable 的 1:1 spotlight 宿主；需要应用层把 pointer state 映射到 Highlight。",
                )
                "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate" -> add(
                    "Kyant 可组合对应视觉原语，但没有 ToolbarMaterialEffectDelegate 的业务 enable/reapply 生命周期和 vendor caustic-shadow contract。",
                )
                "com.coui.appcompat.toolbar.AppBarBlurHelper" -> add(
                    "Kyant progressive blur 只对照渐进模糊机制，不保证 vendor gradient profile、采样核或半径响应一致。",
                )
                GLASS_BUILDER -> {
                    add("Kyant lens 使用自己的形状场、折射函数与色散实现；不能解释为复刻 ColorOS GlassEffectBuilder shader。")
                    add("Kyant 不依赖 ColorOS GlassRegion marker、personality-clocks 参数表或隐藏多输入 RenderEffect 工厂。")
                }
            }

            if ("metaball" in lower) add("只能用 Shape/SDF + Highlight 做最近参考，不能宣称几何融合等价")
            if ("spotlight" in lower && impl != "com.coui.appcompat.spotlight.COUISpotLightEffect") {
                add("库本身没有 COUISpotLightEffect 那种指针聚光宿主；需要应用层驱动 Highlight")
            }
            if ("multilight" in lower) add("Highlight/InnerShadow 只能对照边缘照明结果，不能复现 ColorOS 多光源模型")
            if ("chromatic" in lower) add("Kyant 色散位于 lens 管线内；此处只比较偏移多路采样机制")
            if ("optic" in lower) add("Kyant lens 的背景坐标折射不能被拿来证明 ColorOS Optics 也做折射")
            if (route?.kind == ColorOsSystemUiExecutionRegistry.Kind.HOST_BOUND) add("第三方 Demo 无法复现完整 SystemUI 业务生命周期，只执行 Kyant transport/reference")
            if (route?.kind == ColorOsSystemUiExecutionRegistry.Kind.SURFACE_CONTROL_BOUND) add("普通第三方 View 无法获得该 SystemUI SurfaceControl transport；不做替代 shader")
            if (route?.kind == ColorOsSystemUiExecutionRegistry.Kind.PARAMETER_EXECUTOR) add("该 ColorOS 项是参数/配置层，Kyant reference 是消费这些语义后的视觉原语，不是类级 1:1")
        }

        val note = when (grade) {
            Grade.EXACT_MECHANISM -> "两边存在同类渲染机制；只保证机制层对照，不保证参数、采样核或视觉曲线相同。"
            Grade.COMPOSITE_EQUIVALENT -> "ColorOS 实现由多个材质模块组成，Kyant 使用对应原语组合建立对照。"
            Grade.NEAREST_ONLY -> "Kyant 缺少 ColorOS 的某个核心原语，只能展示最近机制；不能解释为等价实现。"
            Grade.HOST_ONLY -> "主要差异在后景捕获、系统宿主或生命周期；像素效果只作为 transport 层参考。"
        }
        return Delta(grade, colorOsSpecific.distinct(), kyantLimit.distinct(), note)
    }
}
