package com.kyant.backdrop.catalog.coloros

/**
 * Separates the high-recall SystemUI graphics scan into the ColorOS material/Liquid-Glass core
 * and merely adjacent graphics infrastructure. The exhaustive catalog deliberately over-scans;
 * this classifier prevents generic ripple/shadow/animation classes from inflating or failing the
 * material coverage gate.
 */
internal object ColorOsSystemUiAuditScope {
    enum class Scope { CORE_MATERIAL, ADJACENT_GRAPHICS }

    private val DIRECT_EXECUTION_OVERRIDES = setOf(
        "com.oplus.systemui.qs.media.ProgressiveBlurOverlay",
        "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer",
        "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView",
        "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams",
    )

    data class ScopedSummary(
        val total: Int,
        val core: Int,
        val adjacent: Int,
        val coreMapped: Int,
        val coreUnmapped: Int,
        val coreAvailable: Int,
        val coreDirect: Int,
        val coreHostBound: Int,
        val adjacentMapped: Int,
    ) {
        val coreComplete: Boolean get() = coreUnmapped == 0
        val coreCoveragePercent: Float
            get() = if (core == 0) 100f else coreMapped * 100f / core
    }

    data class Classified(
        val mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
        val scope: Scope,
        val reason: String,
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
            Classified(mapping, Scope.CORE_MATERIAL, coreReason)
        } else {
            Classified(
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
        val coreMapped = core.count { isMapped(it.mapping) }
        val adjacentMapped = adjacent.count { isMapped(it.mapping) }
        return ScopedSummary(
            total = classified.size,
            core = core.size,
            adjacent = adjacent.size,
            coreMapped = coreMapped,
            coreUnmapped = core.size - coreMapped,
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
            adjacentMapped = adjacentMapped,
        )
    }

    private fun isMapped(mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping): Boolean =
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