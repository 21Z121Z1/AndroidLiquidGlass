package com.kyant.backdrop.catalog.coloros

/**
 * Separates the high-recall SystemUI graphics scan into the ColorOS material/Liquid-Glass core
 * and merely adjacent graphics infrastructure. The exhaustive catalog deliberately over-scans;
 * this classifier prevents generic ripple/shadow/animation classes from inflating or failing the
 * material coverage gate.
 *
 * CORE_MATERIAL coverage is intentionally stricter than a textual mapping: every core row must
 * also resolve to a ColorOsKyantParityContract made from concrete Kyant primitives.
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
    )

    data class ScopedSummary(
        val total: Int,
        val core: Int,
        val adjacent: Int,
        val coreMapped: Int,
        val coreUnmapped: Int,
        val coreContracted: Int,
        val coreContractMissing: Int,
        val coreAvailable: Int,
        val coreDirect: Int,
        val coreHostBound: Int,
        val parityMechanism: Int,
        val parityComposite: Int,
        val parityNearestOnly: Int,
        val parityHostLifecycle: Int,
        val adjacentMapped: Int,
        val missingContracts: List<String>,
    ) {
        val coreComplete: Boolean get() = coreUnmapped == 0 && coreContractMissing == 0
        val coreCoveragePercent: Float
            get() = if (core == 0) 100f else minOf(coreMapped, coreContracted) * 100f / core
    }

    data class Classified(
        val mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
        val scope: Scope,
        val reason: String,
        val parityContract: ColorOsKyantParityContract.Contract?,
    )

    fun classify(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Classified {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        val coreReason = when {
            impl.startsWith("com.oplus.posteffect.") -> "ColorOS PostEffect 核心"
            impl.startsWith("com.oplusos.systemui.common.blurability.") -> "ColorOS SystemUI blurability 核心"
            impl.startsWith("com.oplusos.systemui.common.adapter.MixColor") -> "SystemUI shipping 材质预设适配器"
            impl == "com.oplusos.systemui.common.util.QSBlurConfigProvider" -> "QS shipping blur/mix 配方入口"
            impl == "com.oplusos.systemui.common.util.ShaderBlendParamHelper" -> "SystemUI shader blend 参数更新器"
            impl == "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView" -> "锁屏渐变模糊遮罩；材质核心但不是折射"
            impl == "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar" -> "QS 真实业务 View；onDraw 进入 QsSeekBarBlurManager"
            impl == "com.oplus.systemui.volume.OplusVolumeSeekBar" -> "音量真实业务 View；构造链进入 VolumeBarMaterialHost/StrokeRenderer"
            ".notification.blur." in impl -> "通知材质 blur/mix 子系统"
            ".notification.material." in impl -> "通知材质子系统"
            ".notification.lockscreen.capsule." in impl &&
                listOf("stroke", "metaball", "spotlight", "material", "blur").any(lower::contains) ->
                "锁屏通知胶囊材质子系统"
            ".qs." in impl && listOf(
                "blur", "material", "spotlight", "stroke", "metaball", "multilight", "progressive",
            ).any(lower::contains) -> "控制中心/QS 材质子系统"
            ".volume." in impl && listOf(
                "blur", "material", "spotlight", "stroke", "metaball", "geometry",
            ).any(lower::contains) -> "音量面板材质子系统"
            ".wallpaperblur." in impl -> "壁纸模糊输入子系统"
            ".biometrics.material." in impl -> "生物识别材质子系统"
            ".panelanimation.platformblur." in impl -> "全局面板平台模糊子系统"
            impl.startsWith("com.oplusos.systemui.common.shader.") && "metaball" in lower -> "Metaball 材质光照"
            isCoreShaderAsset(impl, lower) -> "SystemUI shipping 材质着色器"
            else -> null
        }

        return if (coreReason != null) {
            Classified(
                mapping = mapping,
                scope = Scope.CORE_MATERIAL,
                reason = coreReason,
                parityContract = ColorOsKyantParityContract.resolve(mapping),
            )
        } else {
            Classified(
                mapping = mapping,
                scope = Scope.ADJACENT_GRAPHICS,
                reason = when {
                    impl.startsWith("com.android.systemui.") -> "AOSP/SystemUI 相邻图形或动画设施"
                    "ripple" in lower -> "ripple 相邻图形效果"
                    "shadow" in lower -> "通用 shadow，相邻但未证明属于 ColorOS 材质管线"
                    "gradient" in lower -> "通用 gradient，相邻但未证明属于材质管线"
                    "shader" in lower -> "通用 shader，相邻能力"
                    else -> "高召回扫描命中，但缺少 ColorOS 材质调用链证据"
                },
                parityContract = null,
            )
        }
    }

    fun effectiveExecution(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
    ): ColorOsSystemUiLiquidGlassCatalog.ExecutionMode =
        if (mapping.systemUiImplementation in DIRECT_EXECUTION_OVERRIDES) {
            ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW
        } else {
            mapping.executionMode
        }

    fun classifyAll(
        rows: List<ColorOsSystemUiLiquidGlassCatalog.Mapping>,
    ): List<Classified> = rows.map(::classify)

    fun summary(rows: List<ColorOsSystemUiLiquidGlassCatalog.Mapping>): ScopedSummary {
        val classified = classifyAll(rows)
        val core = classified.filter { it.scope == Scope.CORE_MATERIAL }
        val adjacent = classified.filter { it.scope == Scope.ADJACENT_GRAPHICS }
        val coreMapped = core.count { isTextMapped(it.mapping) }
        val contracted = core.filter { it.parityContract != null }
        val missingContracts = core.filter { it.parityContract == null }
        val adjacentMapped = adjacent.count { isTextMapped(it.mapping) }
        return ScopedSummary(
            total = classified.size,
            core = core.size,
            adjacent = adjacent.size,
            coreMapped = coreMapped,
            coreUnmapped = core.size - coreMapped,
            coreContracted = contracted.size,
            coreContractMissing = missingContracts.size,
            coreAvailable = core.count { it.mapping.status.startsWith("available") },
            coreDirect = core.count {
                val mode = effectiveExecution(it.mapping)
                mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW ||
                    mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.GL_PIPELINE
            },
            coreHostBound = core.count {
                val mode = effectiveExecution(it.mapping)
                mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST ||
                    mode == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL
            },
            parityMechanism = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.MECHANISM },
            parityComposite = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.COMPOSITE },
            parityNearestOnly = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.NEAREST_ONLY },
            parityHostLifecycle = contracted.count { it.parityContract?.kind == ColorOsKyantParityContract.Kind.HOST_LIFECYCLE },
            adjacentMapped = adjacentMapped,
            missingContracts = missingContracts.map { it.mapping.systemUiImplementation },
        )
    }

    private fun isTextMapped(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Boolean =
        mapping.kyantCounterpart.isNotBlank() &&
            !mapping.kyantCounterpart.startsWith("UNMAPPED", ignoreCase = true)

    private fun isCoreShaderAsset(impl: String, lower: String): Boolean {
        if (!impl.startsWith("assets/") && !impl.startsWith("res/raw/")) return false
        return listOf(
            "chromatic",
            "barglow",
            "metaball",
            "blur_down",
            "blur_up",
            "gaussian_blur",
            "display_fragment",
            "display_vertex",
            "stroke",
            "optic",
            "material",
        ).any(lower::contains)
    }
}
