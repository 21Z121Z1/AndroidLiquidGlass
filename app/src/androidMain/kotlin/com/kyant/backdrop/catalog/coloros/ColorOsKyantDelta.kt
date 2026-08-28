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
            when {
                "metaball" in lower -> add("ColorOS 拥有 Metaball 形状融合/光照或 SurfaceControl 组合；Kyant 没有 1:1 Metaball 求并原语")
                "spotlight" in lower -> add("ColorOS 有业务触摸/指针驱动的聚光宿主与 shipping preset")
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
            if ("metaball" in lower) add("只能用 Shape/SDF + Highlight 做最近参考，不能宣称几何融合等价")
            if ("spotlight" in lower) add("库本身没有 COUISpotLightEffect 那种指针聚光宿主；需要应用层驱动 Highlight")
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
