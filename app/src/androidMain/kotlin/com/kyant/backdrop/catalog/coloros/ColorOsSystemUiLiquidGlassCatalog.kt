package com.kyant.backdrop.catalog.coloros

import android.content.Context

/**
 * Exhaustive mechanism/subsystem inventory for the ColorOS 17 SystemUI build
 * used by this demo. Each entry is mapped to the closest Kyant mechanism.
 *
 * This is intentionally an implementation catalog, not a visual-similarity
 * table: a row is marked DIRECT_VIEW only when the ColorOS implementation can
 * be exercised inside an ordinary third-party View. SystemUI-host and
 * SurfaceControl-only implementations stay visible as capability probes.
 */
internal class ColorOsSystemUiLiquidGlassCatalog(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
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

    @Suppress("DEPRECATION")
    private val packageContextResult = runCatching {
        context.applicationContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    private val packageContext: Context get() = packageContextResult.getOrThrow()
    private val loader: ClassLoader get() = packageContext.classLoader

    fun mappings(): List<Mapping> = buildList {
        // Core post-effect graph. These are the primitive building blocks that
        // most of the subsystem-specific material hosts eventually feed.
        clazz("核心后处理", "com.oplus.posteffect.drawable.BlendDrawable", "Backdrop 输入层 + effects 组合", ExecutionMode.DIRECT_VIEW,
            "位图输入、BlendParam、ForegroundBlurParam 和 DrawableShader 的普通 View 宿主")
        clazz("核心后处理", "com.oplus.posteffect.agsl.DrawableShader", "BackdropEffectScope / RuntimeShader effect graph", ExecutionMode.DIRECT_VIEW,
            "动态组装圆角、混色、Metaball、描边、内阴影、Optics 和自定义裁剪")
        clazz("核心后处理", "com.oplus.posteffect.params.CornerParams", "RoundedRectangle / SDF shape", ExecutionMode.DIRECT_VIEW,
            "ColorOS 提供 G2/FULL/CONIC/NONE 圆角场")
        clazz("核心后处理", "com.oplus.posteffect.params.OpticsParams", "Highlight / edge optics", ExecutionMode.DIRECT_VIEW,
            "SDF 边缘光学覆盖层；不是背景坐标折射")
        clazz("核心后处理", "com.oplus.posteffect.params.GradientStrokeLineParams", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "方向性近/远端渐变描边")
        clazz("核心后处理", "com.oplus.posteffect.params.InnerShadowParams", "InnerShadow", ExecutionMode.DIRECT_VIEW,
            "无偏移 + 有偏移两组内阴影")
        clazz("核心后处理", "com.oplus.posteffect.params.MetaBallParams", "动态 Shape/SDF 融合", ExecutionMode.DIRECT_VIEW,
            "DrawableShader 本身可在 BlendDrawable 中执行 Metaball；完整连续模糊宿主另需 SurfaceControl")
        clazz("核心后处理", "com.oplus.posteffect.params.CustomClip", "Shape/clip provider", ExecutionMode.DIRECT_VIEW,
            "可注入 ClipShader；Demo 不伪造任一 SystemUI 私有业务裁剪公式")
        clazz("核心后处理", "com.oplus.posteffect.agsl.ShaderBlendParam", "colorControls/vibrancy/surface tint", ExecutionMode.DIRECT_VIEW,
            "DrawableShader 多层着色器混色")
        clazz("核心后处理", "com.oplus.posteffect.ForegroundBlurParam", "foreground tint / color filter", ExecutionMode.DIRECT_VIEW,
            "前景双颜色 + 混合模式")
        clazz("核心后处理", "com.oplus.posteffect.drawable.ContinuousBlurDrawable", "实时 Backdrop blur", ExecutionMode.SURFACE_CONTROL,
            "构造器直接要求 SurfaceControl，并通过 BlurDrawableManager/模糊服务更新")
        clazz("核心后处理", "com.oplus.posteffect.drawable.MetaBallBlurDrawable", "动态形状 + 实时 Backdrop", ExecutionMode.SURFACE_CONTROL,
            "完整 MetaballBlurDrawable 需要 SurfaceControl；其中的 Metaball AGSL 可由 BlendDrawable 单独验证")

        // SystemUI-owned shader assets.
        asset("SystemUI 着色器资产", "chromatic.agsl", "lens(chromaticAberration) 的色散子机制", ExecutionMode.DIRECT_VIEW,
            "独立正/负方向 RGB 位移工具；不是锁屏折射本身")
        asset("SystemUI 着色器资产", "barglow.agsl", "Highlight + chromatic edge glow", ExecutionMode.DIRECT_VIEW,
            "抛物线/线段距离场发光，并在发光上附加色散")
        asset("SystemUI GL 模糊", "gaussian_blur_fragment_shader.glsl", "blur()", ExecutionMode.GL_PIPELINE,
            "可分离高斯模糊片元着色器")
        asset("SystemUI GL 模糊", "blur_down_fragment_shader.glsl", "Backdrop 降采样/低成本 blur 前级", ExecutionMode.GL_PIPELINE,
            "中心 4 倍权重 + 四邻域降采样")
        asset("SystemUI GL 模糊", "blur_up_fragment_shader.glsl", "Backdrop 上采样/blur 后级", ExecutionMode.GL_PIPELINE,
            "8 tap 上采样")
        asset("SystemUI GL 模糊", "display_fragment_shader.glsl", "vibrancy/colorControls/surface tint", ExecutionMode.GL_PIPELINE,
            "最终亮度、抖动以及 mask/luminosity+dodge/mask+dodge/mask+overlay 合成")

        // Common blurability infrastructure. These correspond to Backdrop
        // acquisition / blur lifecycle rather than the optical surface itself.
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.ViewBlurProxy", "Backdrop host/lifecycle", ExecutionMode.SYSTEM_UI_HOST,
            "把业务 View 连接到静态、平台或壁纸模糊实现")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.BlurConfig", "blur() 配置", ExecutionMode.CAPABILITY_ONLY,
            "系统级模糊半径/配置模型")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.BlurMixConfig", "blur + color filter/vibrancy", ExecutionMode.CAPABILITY_ONLY,
            "模糊与混色的组合配置")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.MixColor", "surface tint/colorFilter", ExecutionMode.CAPABILITY_ONLY,
            "SystemUI 混色描述")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.MixColorWithShader", "runtimeShaderEffect/color filter", ExecutionMode.CAPABILITY_ONLY,
            "带着色器的混色描述")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.InnerShadowGroup", "InnerShadow", ExecutionMode.CAPABILITY_ONLY,
            "业务级内阴影参数组")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.StrokeLineGroup", "Highlight stroke", ExecutionMode.CAPABILITY_ONLY,
            "业务级渐变描边参数组")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable", "automatic Backdrop selection", ExecutionMode.SYSTEM_UI_HOST,
            "按运行环境选择模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable", "masked Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "带形状遮罩的模糊 drawable")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable", "live Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "平台窗口/背景模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.staticblur.StaticBlurDrawable", "captured Backdrop blur", ExecutionMode.SYSTEM_UI_HOST,
            "截图/静态位图模糊后端")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.motion.MotionBlurHelper", "dynamic blur refresh", ExecutionMode.SYSTEM_UI_HOST,
            "运动过程中调整模糊更新节奏")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.screenshot.StaticBlurManager", "Backdrop capture", ExecutionMode.SYSTEM_UI_HOST,
            "截图式静态模糊输入管理")
        clazz("公共模糊基础设施", "com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable", "Backdrop + blur + tint", ExecutionMode.SYSTEM_UI_HOST,
            "壁纸模糊后继续走 BlendDrawable/MixColor")

        // Notification material stack.
        clazz("通知", "com.oplus.systemui.notification.material.NotificationSpotLightDelegate", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "通知卡片按压聚光，底层为 COUISpotLightEffectDrawable")
        clazz("通知", "com.oplus.systemui.notification.blur.NotificationBlurMixColorParams", "blur + vibrancy/color filter", ExecutionMode.CAPABILITY_ONLY,
            "普通卡片、装饰卡片、锁屏堆叠、胶囊分别定义混色配方")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.CapsuleMetaBallController", "dynamic Shape/SDF merge", ExecutionMode.SURFACE_CONTROL,
            "锁屏通知胶囊 Metaball 业务控制器")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.MetaBallBlendManager", "dynamic Shape/SDF merge", ExecutionMode.SURFACE_CONTROL,
            "管理胶囊间融合")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.metaball.MetaBallEarManager", "dynamic Shape/SDF merge", ExecutionMode.SURFACE_CONTROL,
            "管理胶囊融合耳部形变")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeShader", "Highlight stroke", ExecutionMode.SYSTEM_UI_HOST,
            "独立 SDF + near/far 渐变描边着色器")
        clazz("通知", "com.oplus.systemui.notification.lockscreen.capsule.stroke.NotificationGradientStrokeLineAdapter", "Highlight stroke", ExecutionMode.CAPABILITY_ONLY,
            "锁屏胶囊/堆叠描边业务参数适配")
        clazz("通知", "com.oplus.systemui.notification.row.material.MaterialViewWrapper", "surface material wrapper", ExecutionMode.SYSTEM_UI_HOST,
            "通知子 View 的材质包装基类")

        // QS / control center.
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "共享 COUISpotLightEffectDrawable 会话/手势管理")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.widget.strokeshader.GradientStrokeShader", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "独立 RuntimeShader，支持 G2/CONIC")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.widget.strokeshader.StrokeShaderProxy", "Highlight modifier/host", ExecutionMode.SYSTEM_UI_HOST,
            "负责布局、Path 与 GradientStrokeShader")
        clazz("控制中心/QS", "com.oplus.systemui.qs.base.util.QsSeekBarBlurManager", "blur() for slider", ExecutionMode.SYSTEM_UI_HOST,
            "亮度/滑杆模糊管理")
        clazz("控制中心/QS", "com.oplus.systemui.qs.media.ProgressiveBlurOverlay", "progressive blur", ExecutionMode.SYSTEM_UI_HOST,
            "媒体卡片渐进模糊覆盖层")
        clazz("控制中心/QS", "com.oplus.systemui.qs.media.QsMediaSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "媒体区域聚光")
        clazz("控制中心/QS", "com.oplus.systemui.qs.media.OplusQsMediaOutputSpotlightLayout", "InteractiveHighlight host", ExecutionMode.SYSTEM_UI_HOST,
            "媒体输出区域聚光宿主")

        // Volume material stack.
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.VolumeGradientStrokeShader", "Highlight stroke", ExecutionMode.DIRECT_VIEW,
            "独立 RuntimeShader，支持 FULL/SMOOTH")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.OplusVolumeStrokeShaderHost", "Highlight host", ExecutionMode.SYSTEM_UI_HOST,
            "音量胶囊渐变描边布局宿主")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.OplusVolumeBarMaterialHost", "material surface", ExecutionMode.SYSTEM_UI_HOST,
            "音量条材质宿主")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.VolumeBlurManager", "blur() + captured backdrop", ExecutionMode.SYSTEM_UI_HOST,
            "音量条/面板背景模糊")
        clazz("音量面板", "com.oplus.systemui.volume.utils.material.OplusVolumeAutoBlurAnimHelper", "dynamic blur", ExecutionMode.SYSTEM_UI_HOST,
            "音量面板自动模糊动画")
        clazz("音量面板", "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSeekBarSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "音量滑杆聚光")
        clazz("音量面板", "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSettingsButtonSpotLightHelper", "InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "设置按钮聚光")

        // Wallpaper / biometric / global panel state.
        clazz("壁纸", "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable", "Backdrop bitmap blur surface", ExecutionMode.SYSTEM_UI_HOST,
            "当前壁纸模糊位图 + 颜色/alpha 包装")
        clazz("壁纸", "com.oplus.systemui.wallpaperblur.WallpaperBlurManager", "Backdrop source manager", ExecutionMode.SYSTEM_UI_HOST,
            "刷新并维护壁纸模糊输入")
        clazz("生物识别", "com.oplus.systemui.biometrics.material.OplusBiometricMaterialManager", "blur + Highlight + InteractiveHighlight", ExecutionMode.SYSTEM_UI_HOST,
            "PIN/面板的材质模糊、描边和聚光状态")
        clazz("全局面板", "com.android.systemui.panelanimation.platformblur.PlatformBlurManager", "Backdrop refresh policy", ExecutionMode.SYSTEM_UI_HOST,
            "AUTO/MOTION/MOTION_120/STATIC 模糊刷新模式")
        clazz("全局面板", "com.oplus.systemui.panelanimation.platformblur.MaterialBlurStateManager", "Backdrop lifecycle", ExecutionMode.SYSTEM_UI_HOST,
            "ColorOS 材质模糊状态管理")

        // ColorOS common metaball lighting is separate from geometric fusion.
        clazz("Metaball 光照", "com.oplusos.systemui.common.shader.MetaballLightRenderer", "Highlight/lighting", ExecutionMode.SYSTEM_UI_HOST,
            "Metaball 形状上的独立光照渲染器")
        clazz("Metaball 光照", "com.oplusos.systemui.common.shader.MetaballLightConfig", "Highlight configuration", ExecutionMode.CAPABILITY_ONLY,
            "Metaball 光照配置")
    }

    private fun MutableList<Mapping>.clazz(
        group: String,
        className: String,
        kyant: String,
        mode: ExecutionMode,
        note: String,
    ) {
        add(
            Mapping(
                group = group,
                systemUiImplementation = className,
                kyantCounterpart = kyant,
                executionMode = mode,
                status = classStatus(className),
                note = note,
            ),
        )
    }

    private fun MutableList<Mapping>.asset(
        group: String,
        assetName: String,
        kyant: String,
        mode: ExecutionMode,
        note: String,
    ) {
        add(
            Mapping(
                group = group,
                systemUiImplementation = "assets/$assetName",
                kyantCounterpart = kyant,
                executionMode = mode,
                status = assetStatus(assetName),
                note = note,
            ),
        )
    }

    private fun classStatus(name: String): String = runCatching {
        loader.loadClass(name)
        "available"
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun assetStatus(name: String): String = runCatching {
        packageContext.assets.open(name).use { "available:${it.available()}B+" }
    }.getOrElse { "unavailable:${describe(it)}" }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
