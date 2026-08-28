package com.kyant.backdrop.catalog.coloros

import android.content.Context
import dalvik.system.DexFile

/**
 * Exhaustive ColorOS SystemUI Liquid-Glass/material implementation inventory.
 *
 * There are three discovery layers:
 * 1. curated rows whose semantics were reverse-engineered and can be described precisely;
 * 2. a runtime DEX sweep over base + split APKs for ColorOS/SystemUI classes related to
 *    material, blur, stroke, spotlight, Metaball, optics, glass, shader and shadow;
 * 3. a recursive shader-resource sweep over AGSL/GLSL assets and res/raw text shaders.
 *
 * Every discovered implementation is assigned a Kyant counterpart. "No 1:1" is an explicit
 * mapping, not an omission. coverageSummary() therefore provides a real audit gate: anything
 * whose mapping is blank or marked UNMAPPED is counted as a failure.
 */
internal class ColorOsSystemUiLiquidGlassCatalog(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        private val DISCOVERY_PREFIXES = listOf(
            "com.oplus.posteffect.",
            "com.oplus.systemui.",
            "com.oplusos.systemui.common.",
            "com.android.systemui.",
        )

        private val DISCOVERY_KEYWORDS = listOf(
            "posteffect",
            "material",
            "spotlight",
            "blur",
            "stroke",
            "metaball",
            "optic",
            "chromatic",
            "glow",
            "caustic",
            "shader",
            "shadow",
            "gradient",
            "glass",
            "backdrop",
        )

        private val SHADER_PATH_KEYWORDS = listOf(
            "blur",
            "chromatic",
            "metaball",
            "stroke",
            "shadow",
            "glow",
            "optic",
            "glass",
            "blend",
            "display",
            "bar",
            "mask",
        )

        private val SHADER_SOURCE_KEYWORDS = listOf(
            "chromatic",
            "metaball",
            "blur",
            "stroke",
            "shadow",
            "sdf",
            "optic",
            "glow",
            "blendmode",
            "luminosity",
            "colordodge",
            "softlight",
            "smoothstep",
            "distance",
        )

        private val KNOWN_DIRECT_RESOURCES = setOf(
            "assets/chromatic.agsl",
            "assets/barglow.agsl",
            "res/raw/metaball.agsl",
        )
    }

    enum class ExecutionMode {
        DIRECT_VIEW,
        SURFACE_CONTROL,
        SYSTEM_UI_HOST,
        GL_PIPELINE,
        CAPABILITY_ONLY,
    }

    data class Mapping(
        val group: String,
        val systemUiImplementation: String,
        val kyantCounterpart: String,
        val executionMode: ExecutionMode,
        val status: String,
        val note: String,
    )

    data class CoverageSummary(
        val total: Int,
        val available: Int,
        val unavailable: Int,
        val directView: Int,
        val surfaceControl: Int,
        val systemUiHost: Int,
        val glPipeline: Int,
        val capabilityOnly: Int,
        val exactOrMechanismMapped: Int,
        val noOneToOneButExplicitlyMapped: Int,
        val unmapped: Int,
        val unmappedImplementations: List<String>,
    ) {
        val complete: Boolean get() = unmapped == 0
        val coveragePercent: Float get() = if (total == 0) 100f else (total - unmapped) * 100f / total
    }

    @Suppress("DEPRECATION")
    private val packageContextResult = runCatching {
        context.applicationContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val packageContext: Context get() = packageContextResult.getOrThrow()
    private val loader: ClassLoader get() = packageContext.classLoader

    private val mappingCache: List<Mapping> by lazy { buildMappings() }

    fun mappings(): List<Mapping> = mappingCache

    fun coverageSummary(): CoverageSummary {
        val rows = mappingCache
        val unmappedRows = rows.filter {
            it.kyantCounterpart.isBlank() || it.kyantCounterpart.startsWith("UNMAPPED", ignoreCase = true)
        }
        val noOneToOne = rows.count { "无 1:1" in it.kyantCounterpart || "邻接" in it.kyantCounterpart }
        return CoverageSummary(
            total = rows.size,
            available = rows.count { it.status.startsWith("available") },
            unavailable = rows.count { !it.status.startsWith("available") },
            directView = rows.count { it.executionMode == ExecutionMode.DIRECT_VIEW },
            surfaceControl = rows.count { it.executionMode == ExecutionMode.SURFACE_CONTROL },
            systemUiHost = rows.count { it.executionMode == ExecutionMode.SYSTEM_UI_HOST },
            glPipeline = rows.count { it.executionMode == ExecutionMode.GL_PIPELINE },
            capabilityOnly = rows.count { it.executionMode == ExecutionMode.CAPABILITY_ONLY },
            exactOrMechanismMapped = rows.size - noOneToOne - unmappedRows.size,
            noOneToOneButExplicitlyMapped = noOneToOne,
            unmapped = unmappedRows.size,
            unmappedImplementations = unmappedRows.map { it.systemUiImplementation },
        )
    }

    private fun buildMappings(): List<Mapping> {
        val curated = curatedMappings()
        val seen = curated.mapTo(linkedSetOf()) { it.systemUiImplementation }

        val discoveredClasses = discoverGlassClasses()
            .asSequence()
            .filter { it !in seen }
            .map { autoClassMapping(it) }
            .toList()

        val seenAfterClasses = seen + discoveredClasses.map { it.systemUiImplementation }
        val discoveredResources = discoverShaderResources()
            .asSequence()
            .filter { it.implementation !in seenAfterClasses }
            .map { autoResourceMapping(it) }
            .toList()

        return (curated + discoveredClasses + discoveredResources)
            .distinctBy { it.systemUiImplementation }
            .sortedWith(compareBy<Mapping> { groupRank(it.group) }.thenBy { it.group }.thenBy { it.systemUiImplementation })
    }

    private fun curatedMappings(): List<Mapping> = buildList {
        // Core post-effect graph.
        clazz("核心后处理", "com.oplus.posteffect.drawable.BlendDrawable", "Backdrop 输入层 + effects 组合", ExecutionMode.DIRECT_VIEW,
            "位图输入、BlendParam、ForegroundBlurParam 和 DrawableShader 的普通 View 宿主")
        clazz("核心后处理", "com.oplus.posteffect.agsl.DrawableShader", "BackdropEffectScope / runtimeShaderEffect effect graph", ExecutionMode.DIRECT_VIEW,
            "动态组装圆角、混色、Metaball、描边、内阴影、Optics 和自定义裁剪")
        clazz("核心后处理", "com.oplus.posteffect.params.CornerParams", "RoundedRectangle / SDF shape", ExecutionMode.DIRECT_VIEW,
            "ColorOS 提供 G2/FULL/CONIC/NONE 圆角场")
        clazz("核心后处理", "com.oplus.posteffect.params.OpticsParams", "Highlight / edge optics（无 1:1）", ExecutionMode.DIRECT_VIEW,
            "SDF 边缘光学覆盖层；不是背景坐标折射")
        clazz("核心后处理", "com.oplus.posteffect.params.GradientStrokeLineParams", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "方向性近/远端渐变描边")
        clazz("核心后处理", "com.oplus.posteffect.params.InnerShadowParams", "InnerShadow", ExecutionMode.DIRECT_VIEW,
            "无偏移 + 有偏移两组内阴影")
        clazz("核心后处理", "com.oplus.posteffect.params.MetaBallParams", "无 1:1；最近为 Shape/SDF 架构", ExecutionMode.DIRECT_VIEW,
            "DrawableShader 的 Metaball SDF 可在 BlendDrawable 中执行")
        clazz("核心后处理", "com.oplus.posteffect.params.CustomClip", "Shape / clip provider", ExecutionMode.DIRECT_VIEW,
            "可注入 ClipShader；不伪造任一 SystemUI 私有业务裁剪公式")
        clazz("核心后处理", "com.oplus.posteffect.agsl.ShaderBlendParam", "vibrancy / colorControls / surface tint", ExecutionMode.DIRECT_VIEW,
            "DrawableShader 多层着色器混色")
        clazz("核心后处理", "com.oplus.posteffect.ForegroundBlurParam", "foreground tint / color filter", ExecutionMode.DIRECT_VIEW,
            "前景双颜色 + 混合模式")
        clazz("核心后处理", "com.oplus.posteffect.drawable.ContinuousBlurDrawable", "实时 Backdrop blur", ExecutionMode.SURFACE_CONTROL,
            "构造器直接要求 SurfaceControl，并通过 BlurDrawableManager 更新")
        clazz("核心后处理", "com.oplus.posteffect.drawable.MetaBallBlurDrawable", "无 1:1；Metaball shape + live Backdrop", ExecutionMode.SURFACE_CONTROL,
            "完整 MetaBallBlurDrawable 需要 SurfaceControl；几何 AGSL 已另行在普通 BlendDrawable 验证")

        // SystemUI-owned shader assets.
        asset("SystemUI 着色器资产", "chromatic.agsl", "lens(chromaticAberration) 的色散子机制", ExecutionMode.DIRECT_VIEW,
            "中心/正偏移/负偏移三路采样；不是锁屏折射本身")
        asset("SystemUI 着色器资产", "barglow.agsl", "Highlight + 色散视觉子机制（无 1:1）", ExecutionMode.DIRECT_VIEW,
            "抛物线/线段距离场发光，并在发光上附加色散")
        raw("SystemUI 着色器资产", "metaball", "无 1:1；surface texture/light mask", ExecutionMode.DIRECT_VIEW,
            "res/raw/metaball.agsl：圆形范围内旋转纹理遮罩，与 DrawableShader Metaball 几何融合不是同一算法")
        asset("SystemUI GL 模糊", "gaussian_blur_fragment_shader.glsl", "blur()", ExecutionMode.GL_PIPELINE,
            "可分离高斯模糊；Demo 通过 ColorOsSystemUiGlBlurView 直接编译设备 SystemUI shader")
        asset("SystemUI GL 模糊", "blur_down_fragment_shader.glsl", "Backdrop 降采样前级", ExecutionMode.GL_PIPELINE,
            "中心 4 倍权重 + 四邻域降采样；Demo 直接执行")
        asset("SystemUI GL 模糊", "blur_up_fragment_shader.glsl", "Backdrop 上采样后级", ExecutionMode.GL_PIPELINE,
            "8 tap 上采样；Demo 直接执行")
        asset("SystemUI GL 模糊", "display_fragment_shader.glsl", "vibrancy / colorControls / surface tint", ExecutionMode.GL_PIPELINE,
            "亮度、抖动和多种材质混色；Demo 直接执行完整 FBO 链")

        // Common blurability infrastructure.
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.ViewBlurProxy", "Backdrop host/lifecycle", ExecutionMode.SYSTEM_UI_HOST,
            "连接业务 View 与静态、平台或壁纸模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.BlurConfig", "blur() 配置", ExecutionMode.CAPABILITY_ONLY,
            "系统模糊配置模型")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.BlurMixConfig", "blur + vibrancy/color filter", ExecutionMode.CAPABILITY_ONLY,
            "模糊与混色组合配置")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.MixColor", "surface tint / colorFilter", ExecutionMode.CAPABILITY_ONLY,
            "业务混色描述")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.MixColorWithShader", "runtimeShaderEffect / color filter", ExecutionMode.CAPABILITY_ONLY,
            "带着色器的混色描述")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.InnerShadowGroup", "InnerShadow", ExecutionMode.CAPABILITY_ONLY,
            "业务级内阴影参数组")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.StrokeLineGroup", "Highlight stroke", ExecutionMode.CAPABILITY_ONLY,
            "业务级渐变描边参数组")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable", "automatic Backdrop selection", ExecutionMode.SYSTEM_UI_HOST,
            "按环境选择模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable", "masked Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "带形状遮罩的模糊 drawable")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable", "live Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "平台窗口/背景模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.staticblur.StaticBlurDrawable", "captured Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "截图/静态位图模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.motion.MotionBlurHelper", "dynamic Backdrop refresh", ExecutionMode.SYSTEM_UI_HOST,
            "运动中调整模糊刷新节奏")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.screenshot.StaticBlurManager", "Backdrop capture", ExecutionMode.SYSTEM_UI_HOST,
            "截图式静态模糊输入管理")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable", "Backdrop + blur + tint", ExecutionMode.SYSTEM_UI_HOST,
            "壁纸模糊后继续走 BlendDrawable/MixColor")

        // Notification.
        clazz("通知", "com.oplus.systemui.notification.material.NotificationSpotLightDelegate", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "通知卡片按压聚光，底层 COUISpotLightEffectDrawable")
        clazz("通知", "com.oplus.systemui.notification.blur.NotificationBlurMixColorParams", "blur + vibrancy/color filter", ExecutionMode.CAPABILITY_ONLY,
            "普通卡片、装饰卡片、锁屏堆叠、胶囊分别定义混色配方")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.CapsuleMetaBallController", "无 1:1；Shape/SDF 邻接", ExecutionMode.SURFACE_CONTROL,
            "锁屏通知胶囊 Metaball 业务控制器")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.MetaBallBlendManager", "无 1:1；Shape/SDF 邻接", ExecutionMode.SURFACE_CONTROL,
            "管理胶囊间融合")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.MetaBallEarManager", "无 1:1；Shape/SDF 邻接", ExecutionMode.SURFACE_CONTROL,
            "管理胶囊融合耳部形变")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeShader", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "独立 SDF + near/far 渐变描边；Demo 通过 ColorOsNotificationStrokeBridge 直接运行原 RuntimeShader")
        clazz("通知", "com.oplus.systemui.notification.blur.NotificationGradientStrokeLineAdapter", "Highlight stroke", ExecutionMode.CAPABILITY_ONLY,
            "通知/锁屏堆叠描边业务参数适配")
        clazz("通知", "com.oplus.systemui.notification.blur.NotificationInnerShadowAdapter", "InnerShadow", ExecutionMode.CAPABILITY_ONLY,
            "通知内阴影业务参数适配")
        clazz("通知", "com.oplus.systemui.notification.row.NotificationMenuRowMetaBallController", "无 1:1；Shape/SDF 邻接", ExecutionMode.SYSTEM_UI_HOST,
            "通知菜单 Metaball 交互控制器")
        clazz("通知", "com.oplus.systemui.notification.row.material.wrapper.MaterialViewWrapper", "drawBackdrop material wrapper", ExecutionMode.SYSTEM_UI_HOST,
            "通知子 View 材质包装基类")

        // QS / control center.
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "共享 COUISpotLightEffectDrawable 会话/手势管理")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.widget.strokeshader.GradientStrokeShader", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "独立 RuntimeShader，支持 G2/CONIC；Demo 直接运行")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.widget.strokeshader.StrokeShaderProxy", "Highlight host", ExecutionMode.SYSTEM_UI_HOST,
            "布局、Path 与 GradientStrokeShader 宿主")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.util.QsSeekBarBlurManager", "blur() for slider", ExecutionMode.SYSTEM_UI_HOST,
            "亮度/滑杆模糊管理")
        clazz("控制中心/QS", "com.oplus.systemui.qs.media.ProgressiveBlurOverlay", "progressive blur", ExecutionMode.SYSTEM_UI_HOST,
            "媒体卡片渐进模糊覆盖层")
        clazz("控制中心/QS", "com.oplus.systemui.qs.media.QsMediaSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "媒体区域聚光")

        // Volume.
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.VolumeGradientStrokeShader", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "独立 RuntimeShader，支持 FULL/SMOOTH；Demo 直接运行")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.OplusVolumeStrokeShaderHost", "Highlight host", ExecutionMode.SYSTEM_UI_HOST,
            "音量胶囊渐变描边布局宿主")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.OplusVolumeBarMaterialHost", "drawBackdrop material surface", ExecutionMode.SYSTEM_UI_HOST,
            "音量条材质宿主")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.VolumeBlurManager", "blur() + captured Backdrop", ExecutionMode.SYSTEM_UI_HOST,
            "音量条/面板背景模糊")
        clazz("音量面板", "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSeekBarSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "音量滑杆聚光")
        clazz("音量面板", "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSettingsButtonSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "设置按钮聚光")

        // Other SystemUI material consumers.
        clazz("壁纸", "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable", "Backdrop bitmap surface", ExecutionMode.SYSTEM_UI_HOST,
            "包装已经生成的壁纸模糊位图与颜色/alpha")
        clazz("壁纸", "com.oplus.systemui.wallpaperblur.WallpaperBlurManager", "Backdrop source manager", ExecutionMode.SYSTEM_UI_HOST,
            "刷新并维护壁纸模糊输入")
        clazz("生物识别", "com.oplus.systemui.biometrics.material.OplusBiometricMaterialManager", "blur + Highlight + InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "PIN/面板材质模糊、描边和聚光状态")
        clazz("全局面板", "com.android.systemui.panelanimation.platformblur.PlatformBlurManager", "Backdrop refresh policy", ExecutionMode.SYSTEM_UI_HOST,
            "AUTO/MOTION/MOTION_120/STATIC 模糊刷新模式")
        clazz("全局面板", "com.oplus.systemui.panelanimation.platformblur.MaterialBlurStateManager", "Backdrop lifecycle", ExecutionMode.SYSTEM_UI_HOST,
            "ColorOS 材质模糊状态管理")
        clazz("Metaball 光照", "com.oplusos.systemui.common.shader.MetaballLightRenderer", "Highlight/lighting（无 1:1）", ExecutionMode.SYSTEM_UI_HOST,
            "Metaball 形状上的独立光照渲染器")
    }

    /** Runtime DEX sweep across base + split APKs. */
    @Suppress("DEPRECATION")
    private fun discoverGlassClasses(): Set<String> = runCatching {
        val info = packageContext.applicationInfo
        val paths = buildList {
            info.sourceDir?.let(::add)
            info.splitSourceDirs?.forEach(::add)
        }.distinct()

        buildSet {
            paths.forEach { path ->
                val dex = DexFile(path)
                try {
                    val entries = dex.entries()
                    while (entries.hasMoreElements()) {
                        val rawName = entries.nextElement()
                        val name = rawName.substringBefore('$')
                        val lower = name.lowercase()
                        if (DISCOVERY_PREFIXES.any(name::startsWith) && DISCOVERY_KEYWORDS.any(lower::contains)) {
                            add(name)
                        }
                    }
                } finally {
                    runCatching { dex.close() }
                }
            }
        }
    }.getOrDefault(emptySet())

    private data class ShaderResource(
        val implementation: String,
        val source: String,
        val isGlPipeline: Boolean,
    )

    /** Recursively scans SystemUI shader assets and text resources. */
    private fun discoverShaderResources(): Set<ShaderResource> = buildSet {
        addAll(discoverAssetShaders())
        addAll(discoverRawShaders())
    }

    private fun discoverAssetShaders(): Set<ShaderResource> = runCatching {
        buildSet {
            fun walk(prefix: String) {
                val children = packageContext.assets.list(prefix).orEmpty()
                children.forEach { child ->
                    val path = if (prefix.isBlank()) child else "$prefix/$child"
                    val nested = packageContext.assets.list(path).orEmpty()
                    if (nested.isNotEmpty()) {
                        walk(path)
                    } else if (path.endsWith(".agsl", true) || path.endsWith(".glsl", true)) {
                        val source = runCatching {
                            packageContext.assets.open(path).use(::readTextPrefix)
                        }.getOrDefault("")
                        if (isRelevantShader(path, source)) {
                            add(
                                ShaderResource(
                                    implementation = "assets/$path",
                                    source = source,
                                    isGlPipeline = path.endsWith(".glsl", true),
                                ),
                            )
                        }
                    }
                }
            }
            walk("")
        }
    }.getOrDefault(emptySet())

    private fun discoverRawShaders(): Set<ShaderResource> = runCatching {
        val rawClass = loader.loadClass("$SYSTEM_UI_PACKAGE.R\$raw")
        buildSet {
            rawClass.declaredFields.forEach { field ->
                if (field.type != Int::class.javaPrimitiveType) return@forEach
                field.isAccessible = true
                val id = field.getInt(null)
                val source = runCatching {
                    packageContext.resources.openRawResource(id).use(::readTextPrefix)
                }.getOrDefault("")
                if (source.isNotBlank() && isRelevantShader(field.name, source)) {
                    val gl = "sampler2d" in source.lowercase() || "#version" in source.lowercase()
                    val extension = if (gl) "glsl" else "agsl"
                    add(
                        ShaderResource(
                            implementation = "res/raw/${field.name}.$extension",
                            source = source,
                            isGlPipeline = gl,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptySet())

    private fun readTextPrefix(input: java.io.InputStream, maxChars: Int = 64 * 1024): String {
        val reader = input.bufferedReader()
        val buffer = CharArray(4096)
        val out = StringBuilder(minOf(maxChars, 16 * 1024))
        while (out.length < maxChars) {
            val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
            if (count <= 0) break
            out.append(buffer, 0, count)
        }
        return out.toString()
    }

    private fun isRelevantShader(path: String, source: String): Boolean {
        val pathLower = path.lowercase()
        if (SHADER_PATH_KEYWORDS.any(pathLower::contains)) return true
        val sourceLower = source.lowercase()
        val hits = SHADER_SOURCE_KEYWORDS.count(sourceLower::contains)
        return hits >= 2
    }

    private fun autoClassMapping(className: String): Mapping {
        val lower = className.lowercase()
        val mode = when {
            className in DIRECT_CLASSES -> ExecutionMode.DIRECT_VIEW
            lower.contains("continuousblurdrawable") ||
                lower.contains("metaballblurdrawable") ||
                lower.contains("capsulemetaballcontroller") ||
                lower.contains("metaballblendmanager") ||
                lower.contains("metaballearmanager") -> ExecutionMode.SURFACE_CONTROL
            lower.endsWith("params") || lower.contains("config") || lower.contains("adapter") -> ExecutionMode.CAPABILITY_ONLY
            else -> ExecutionMode.SYSTEM_UI_HOST
        }
        return Mapping(
            group = autoGroup(className),
            systemUiImplementation = className,
            kyantCounterpart = autoKyantCounterpart(className),
            executionMode = mode,
            status = classStatus(className),
            note = "自动从当前安装的 SystemUI DEX 发现；已强制分配 Kyant 机制或显式‘无 1:1’映射。业务宿主不在第三方进程中伪造。",
        )
    }

    private fun autoResourceMapping(resource: ShaderResource): Mapping {
        val lower = (resource.implementation + "\n" + resource.source).lowercase()
        val kyant = when {
            "chromatic" in lower -> "lens(chromaticAberration) 色散子机制"
            "metaball" in lower -> "无 1:1；最近为 Shape/SDF + Highlight 架构"
            "stroke" in lower -> "Highlight / stroke"
            "innershadow" in lower || "shadow" in lower -> "InnerShadow / Shadow"
            "glow" in lower || "bar" in resource.implementation.lowercase() -> "Highlight / edge glow（可能无 1:1）"
            "blur" in lower -> "blur() + Backdrop sampling"
            "luminosity" in lower || "colordodge" in lower || "softlight" in lower || "blend" in lower || "display" in lower ->
                "vibrancy / colorControls / surface tint"
            "sdf" in lower || "distance" in lower -> "RoundedRectangle / Shape SDF"
            else -> "无 1:1；仅实现邻接对照"
        }
        val mode = when {
            resource.isGlPipeline -> ExecutionMode.GL_PIPELINE
            resource.implementation in KNOWN_DIRECT_RESOURCES -> ExecutionMode.DIRECT_VIEW
            else -> ExecutionMode.CAPABILITY_ONLY
        }
        val note = when (mode) {
            ExecutionMode.DIRECT_VIEW -> "自动扫描到已具备真实 uniform 绑定器的 SystemUI shader；由 Demo 直接执行设备原资源。"
            ExecutionMode.GL_PIPELINE -> "自动扫描到 SystemUI GLSL；映射到 GL 管线。已知 blur/display 族由 ColorOsSystemUiGlBlurView 直接执行，其他资源只做语义审计。"
            else -> "自动扫描当前 SystemUI shader 资源；已映射 Kyant 机制，但没有伪造未知 uniform/业务输入绑定。"
        }
        return Mapping(
            group = if (resource.isGlPipeline) "自动发现 · SystemUI GL 着色器" else "自动发现 · SystemUI RuntimeShader",
            systemUiImplementation = resource.implementation,
            kyantCounterpart = kyant,
            executionMode = mode,
            status = if (resource.implementation.startsWith("assets/")) {
                assetStatus(resource.implementation.removePrefix("assets/"))
            } else {
                "available:text-resource"
            },
            note = note,
        )
    }

    private fun autoGroup(className: String): String = when {
        className.startsWith("com.oplus.posteffect.") -> "自动发现 · 核心后处理"
        ".notification." in className -> "自动发现 · 通知"
        ".qs." in className -> "自动发现 · 控制中心/QS"
        ".volume." in className -> "自动发现 · 音量面板"
        ".biometrics." in className -> "自动发现 · 生物识别"
        ".wallpaper" in className -> "自动发现 · 壁纸"
        ".keyguard." in className -> "自动发现 · 锁屏/SystemUI"
        ".panelanimation." in className -> "自动发现 · 全局面板"
        ".blurability." in className -> "自动发现 · 公共模糊基础设施"
        ".shader." in className && "metaball" in className.lowercase() -> "自动发现 · Metaball 光照"
        else -> "自动发现 · 其他相关实现"
    }

    private fun autoKyantCounterpart(className: String): String {
        val lower = className.lowercase()
        return when {
            "metaball" in lower -> "无 1:1；最近为 Shape/SDF + Highlight 架构"
            "spotlight" in lower -> "InteractiveHighlight"
            "innershadow" in lower -> "InnerShadow"
            "shadow" in lower -> "Shadow / InnerShadow"
            "stroke" in lower || "gradient" in lower -> "Highlight / stroke"
            "optic" in lower -> "Highlight / edge optics（无 1:1）"
            "chromatic" in lower -> "lens(chromaticAberration) 色散子机制"
            "materialcolor" in lower || "mixcolor" in lower || "blend" in lower -> "vibrancy / colorControls / surface tint"
            "blur" in lower || "backdrop" in lower -> "blur() + Backdrop acquisition/lifecycle"
            "glow" in lower -> "Highlight"
            "shader" in lower || "posteffect" in lower -> "runtimeShaderEffect / drawBackdrop effect graph"
            "glass" in lower -> "lens + blur + surface tint 组合"
            "material" in lower -> "drawBackdrop material composition"
            else -> "无 1:1；仅实现邻接对照"
        }
    }

    private fun groupRank(group: String): Int = when {
        group == "核心后处理" -> 0
        group == "SystemUI 着色器资产" -> 1
        group == "SystemUI GL 模糊" -> 2
        group == "公共模糊基础设施" -> 3
        group == "通知" -> 4
        group == "控制中心/QS" -> 5
        group == "音量面板" -> 6
        group == "壁纸" -> 7
        group == "生物识别" -> 8
        group == "全局面板" -> 9
        group == "Metaball 光照" -> 10
        group.startsWith("自动发现") -> 20
        else -> 15
    }

    private fun MutableList<Mapping>.clazz(
        group: String,
        className: String,
        kyant: String,
        mode: ExecutionMode,
        note: String,
    ) {
        add(Mapping(group, className, kyant, mode, classStatus(className), note))
    }

    private fun MutableList<Mapping>.asset(
        group: String,
        assetName: String,
        kyant: String,
        mode: ExecutionMode,
        note: String,
    ) {
        add(Mapping(group, "assets/$assetName", kyant, mode, assetStatus(assetName), note))
    }

    private fun MutableList<Mapping>.raw(
        group: String,
        rawName: String,
        kyant: String,
        mode: ExecutionMode,
        note: String,
    ) {
        add(Mapping(group, "res/raw/$rawName.agsl", kyant, mode, rawStatus(rawName), note))
    }

    private fun classStatus(name: String): String = runCatching {
        loader.loadClass(name)
        "available"
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun assetStatus(name: String): String = runCatching {
        packageContext.assets.open(name).use { "available:${it.available()}B+" }
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun rawStatus(name: String): String = runCatching {
        val id = packageContext.resources.getIdentifier(name, "raw", SYSTEM_UI_PACKAGE)
        require(id != 0)
        packageContext.resources.openRawResource(id).use { "available:${it.available()}B+" }
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }

    private val DIRECT_CLASSES = setOf(
        "com.oplus.posteffect.drawable.BlendDrawable",
        "com.oplus.posteffect.agsl.DrawableShader",
        "com.oplus.posteffect.params.CornerParams",
        "com.oplus.posteffect.params.OpticsParams",
        "com.oplus.posteffect.params.GradientStrokeLineParams",
        "com.oplus.posteffect.params.InnerShadowParams",
        "com.oplus.posteffect.params.MetaBallParams",
        "com.oplus.posteffect.params.CustomClip",
        "com.oplus.posteffect.agsl.ShaderBlendParam",
        "com.oplus.posteffect.ForegroundBlurParam",
        "com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeShader",
        "com.oplus.systemui.qs.base.widget.strokeshader.GradientStrokeShader",
        "com.oplus.systemui.volume.utils.material.VolumeGradientStrokeShader",
    )
}
