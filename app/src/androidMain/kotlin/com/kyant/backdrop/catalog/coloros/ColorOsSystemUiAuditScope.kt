package com.kyant.backdrop.catalog.coloros

/**
 * Strict SystemUI material audit.
 *
 * A CORE_MATERIAL row is complete only when four independent conditions hold:
 * semantic mapping, concrete Kyant contract, compatible ColorOS execution route and explicit delta.
 */
internal object ColorOsSystemUiAuditScope {
    enum class Scope { CORE_MATERIAL, ADJACENT_GRAPHICS }

    private val DIRECT_EXECUTION_OVERRIDES = setOf(
        "com.oplus.systemui.qs.media.ProgressiveBlurOverlay",
        "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer",
        "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView",
        "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams",
        "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
        "com.oplus.systemui.volume.OplusVolumeSeekBar",
        "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable",
    )

    private val PARAMETER_ONLY_OVERRIDES = setOf(
        "com.oplus.posteffect.agsl.ShaderBlendParam",
        "com.oplus.posteffect.ForegroundBlurParam",
        "com.oplus.posteffect.params.CustomClip",
    )

    private val FRAMEWORK_PRIMITIVES = mapOf(
        "com.oplus.graphics.OplusRenderEffect" to "Oplus 框架 RenderEffect/渐进模糊原语",
        "com.oplus.view.OplusViewBackgroundRenderEffect" to "Oplus View 后景 RenderEffect 挂载原语",
        "com.oplus.view.material.OplusMaterialUtil" to "Oplus 框架 edge/shadow/caustic 材质原语",
    )

    private val COUI_PRIMITIVES = setOf(
        "com.coui.appcompat.COUIMaterialBlurEffect",
        "com.coui.appcompat.COUIMaterialStrokeEffect",
        "com.coui.appcompat.spotlight.COUISpotLightEffect",
        "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate",
        "com.coui.appcompat.toolbar.AppBarBlurHelper",
    )

    private const val GLASS_BUILDER =
        "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder"

    data class ScopedSummary(
        val total: Int,
        val core: Int,
        val adjacent: Int,
        val coreMapped: Int,
        val coreUnmapped: Int,
        val coreContracted: Int,
        val coreContractMissing: Int,
        val coreRouted: Int,
        val coreRouteMissing: Int,
        val coreRouteIncompatible: Int,
        val coreDeltaResolved: Int,
        val coreDeltaMissing: Int,
        val coreAvailable: Int,
        val coreDirect: Int,
        val coreHostBound: Int,
        val parityMechanism: Int,
        val parityComposite: Int,
        val parityNearestOnly: Int,
        val parityHostLifecycle: Int,
        val deltaExactMechanism: Int,
        val deltaCompositeEquivalent: Int,
        val deltaNearestOnly: Int,
        val deltaHostOnly: Int,
        val adjacentMapped: Int,
        val missingContracts: List<String>,
        val missingRoutes: List<String>,
        val incompatibleRoutes: List<String>,
        val missingDeltas: List<String>,
    ) {
        val coreComplete: Boolean
            get() = coreUnmapped == 0 && coreContractMissing == 0 && coreRouteMissing == 0 &&
                coreRouteIncompatible == 0 && coreDeltaMissing == 0
        val coreCoveragePercent: Float
            get() = if (core == 0) 100f else minOf(coreMapped, coreContracted, coreRouted, coreDeltaResolved) * 100f / core
    }

    data class Classified(
        val mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
        val scope: Scope,
        val reason: String,
        val parityContract: ColorOsKyantParityContract.Contract?,
        val executionRoute: ColorOsSystemUiExecutionRegistry.Route?,
        val delta: ColorOsKyantDelta.Delta?,
    )

    fun classify(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Classified {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()
        val coreReason = when {
            impl in FRAMEWORK_PRIMITIVES -> FRAMEWORK_PRIMITIVES.getValue(impl)
            impl in COUI_PRIMITIVES -> "SystemUI 消费的外部 COUI shipping 材质原语"
            impl == GLASS_BUILDER -> "SystemUI 锁屏插件真实折射/色散 GlassEffectBuilder"
            mapping.group.startsWith("自动发现 · 外部") ->
                "从 SystemUI 实际消费的外部材质包运行时发现；必须进入同一 Kyant/ColorOS 严格闸门"
            impl.startsWith("com.oplus.posteffect.") -> "ColorOS PostEffect 图形/参数/宿主体系"
            impl.startsWith("com.oplusos.systemui.common.blurability.") -> "ColorOS SystemUI blurability 核心"
            impl.startsWith("com.oplusos.systemui.common.adapter.MixColor") -> "SystemUI shipping 材质预设适配器"
            impl == "com.oplusos.systemui.common.util.QSBlurConfigProvider" -> "QS shipping blur/mix 配方入口"
            impl == "com.oplusos.systemui.common.util.ShaderBlendParamHelper" -> "SystemUI shader blend 参数更新器"
            impl.startsWith("com.oplusos.systemui.common.util.") && listOf("blur", "stroke", "material").any(lower::contains) ->
                "SystemUI 公共材质参数/模糊工具"
            impl == "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar" -> "QS 真实业务 View；onDraw 进入 QsSeekBarBlurManager"
            impl == "com.oplus.systemui.volume.OplusVolumeSeekBar" -> "音量真实业务 View；构造链进入 OplusVolumeBarMaterialHost/StrokeRenderer"
            impl.startsWith("com.oplus.systemui.notification.") &&
                listOf("material", "blur", "stroke", "spotlight", "metaball", "optic").any(lower::contains) -> "通知完整材质子系统"
            impl.startsWith("com.oplus.systemui.keyguard.") &&
                listOf("material", "gradientmask", "multilayerblur").any(lower::contains) -> "锁屏材质/渐变模糊子系统"
            impl.startsWith("com.oplus.systemui.blur.") -> "SystemUI Oplus 模糊/颜色基础设施"
            "oplusqsdialogblur" in lower -> "QS 对话框模糊背景"
            ".qs." in impl && listOf("blur", "material", "spotlight", "stroke", "metaball", "multilight", "progressive").any(lower::contains) ->
                "控制中心/QS 材质子系统"
            ".volume." in impl && listOf("blur", "material", "spotlight", "stroke", "metaball", "geometry").any(lower::contains) ->
                "音量面板材质子系统"
            ".wallpaperblur." in impl -> "壁纸模糊输入子系统"
            ".biometrics.material." in impl -> "生物识别材质子系统"
            ".panelanimation.platformblur." in impl -> "全局面板平台模糊子系统"
            impl.startsWith("com.oplusos.systemui.common.shader.") && "metaball" in lower -> "Metaball 材质光照"
            isCoreShaderAsset(impl, lower) -> "SystemUI shipping 材质着色器"
            else -> null
        }

        if (coreReason == null) {
            return Classified(
                mapping,
                Scope.ADJACENT_GRAPHICS,
                when {
                    impl.startsWith("com.android.systemui.") -> "AOSP/SystemUI 相邻图形或动画设施"
                    "ripple" in lower -> "ripple 相邻图形效果"
                    "shadow" in lower -> "通用 shadow，相邻但未证明属于 ColorOS 材质管线"
                    "gradient" in lower -> "通用 gradient，相邻但未证明属于材质管线"
                    "shader" in lower -> "通用 shader，相邻能力"
                    else -> "高召回扫描命中，但缺少 ColorOS 材质调用链证据"
                },
                null,
                null,
                null,
            )
        }

        val effective = effectiveExecution(mapping)
        val contract = ColorOsSystemUiParityResolver.resolve(mapping)
        val route = ColorOsSystemUiExecutionRegistry.resolve(mapping, effective)
        return Classified(
            mapping,
            Scope.CORE_MATERIAL,
            coreReason,
            contract,
            route,
            ColorOsKyantDelta.resolve(mapping, contract, route),
        )
    }

    fun effectiveExecution(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): ColorOsSystemUiLiquidGlassCatalog.ExecutionMode {
        val impl = mapping.systemUiImplementation
        return when {
            impl in DIRECT_EXECUTION_OVERRIDES -> ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW
            impl in PARAMETER_ONLY_OVERRIDES -> ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY
            mapping.executionMode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.GL_PIPELINE && !isExecutableGlResource(impl) ->
                ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY
            else -> mapping.executionMode
        }
    }

    fun classifyAll(rows: List<ColorOsSystemUiLiquidGlassCatalog.Mapping>): List<Classified> = rows.map(::classify)

    fun summary(rows: List<ColorOsSystemUiLiquidGlassCatalog.Mapping>): ScopedSummary {
        val classified = classifyAll(rows)
        val core = classified.filter { it.scope == Scope.CORE_MATERIAL }
        val adjacent = classified.filter { it.scope == Scope.ADJACENT_GRAPHICS }
        val coreMapped = core.count { isTextMapped(it.mapping) }
        val contracted = core.filter { it.parityContract != null }
        val missingContracts = core.filter { it.parityContract == null }
        val routed = core.filter { it.executionRoute != null }
        val missingRoutes = core.filter { it.executionRoute == null }
        val incompatibleRoutes = core.filter { item ->
            val route = item.executionRoute ?: return@filter false
            !ColorOsSystemUiExecutionRegistry.routeIsCompatible(route, effectiveExecution(item.mapping))
        }
        val resolvedDeltas = core.filter { it.delta != null }
        val missingDeltas = core.filter { it.delta == null }
        return ScopedSummary(
            total = classified.size,
            core = core.size,
            adjacent = adjacent.size,
            coreMapped = coreMapped,
            coreUnmapped = core.size - coreMapped,
            coreContracted = contracted.size,
            coreContractMissing = missingContracts.size,
            coreRouted = routed.size,
            coreRouteMissing = missingRoutes.size,
            coreRouteIncompatible = incompatibleRoutes.size,
            coreDeltaResolved = resolvedDeltas.size,
            coreDeltaMissing = missingDeltas.size,
            coreAvailable = core.count { it.mapping.status.startsWith("available") },
            coreDirect = core.count {
                val mode = effectiveExecution(it.mapping)
                mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW || mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.GL_PIPELINE
            },
            coreHostBound = core.count {
                val mode = effectiveExecution(it.mapping)
                mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST || mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL
            },
            parityMechanism = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.MECHANISM },
            parityComposite = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.COMPOSITE },
            parityNearestOnly = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.NEAREST_ONLY },
            parityHostLifecycle = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.HOST_LIFECYCLE },
            deltaExactMechanism = resolvedDeltas.count { it.delta?.grade == ColorOsKyantDelta.Grade.EXACT_MECHANISM },
            deltaCompositeEquivalent = resolvedDeltas.count { it.delta?.grade == ColorOsKyantDelta.Grade.COMPOSITE_EQUIVALENT },
            deltaNearestOnly = resolvedDeltas.count { it.delta?.grade == ColorOsKyantDelta.Grade.NEAREST_ONLY },
            deltaHostOnly = resolvedDeltas.count { it.delta?.grade == ColorOsKyantDelta.Grade.HOST_ONLY },
            adjacentMapped = adjacent.count { isTextMapped(it.mapping) },
            missingContracts = missingContracts.map { it.mapping.systemUiImplementation },
            missingRoutes = missingRoutes.map { it.mapping.systemUiImplementation },
            incompatibleRoutes = incompatibleRoutes.map { "${it.mapping.systemUiImplementation}: ${effectiveExecution(it.mapping)} -> ${it.executionRoute}" },
            missingDeltas = missingDeltas.map { it.mapping.systemUiImplementation },
        )
    }

    private fun isTextMapped(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Boolean =
        mapping.kyantCounterpart.isNotBlank() && !mapping.kyantCounterpart.startsWith("UNMAPPED", ignoreCase = true)

    private fun isExecutableGlResource(impl: String): Boolean {
        if (!impl.startsWith("assets/")) return false
        val lower = impl.lowercase()
        return listOf("blur_down", "blur_up", "gaussian_blur", "display_").any(lower::contains)
    }

    private fun isCoreShaderAsset(impl: String, lower: String): Boolean {
        if (!impl.startsWith("assets/") && !impl.startsWith("res/raw/")) return false
        return listOf(
            "chromatic", "barglow", "metaball", "blur_down", "blur_up", "gaussian_blur",
            "display_fragment", "display_vertex", "stroke", "optic", "material",
        ).any(lower::contains)
    }
}
