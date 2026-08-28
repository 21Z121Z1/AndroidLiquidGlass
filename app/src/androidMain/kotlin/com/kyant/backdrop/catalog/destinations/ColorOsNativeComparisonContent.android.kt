package com.kyant.backdrop.catalog.destinations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.SeekBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsMaterialBridge
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiExecutableBridge
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiPostEffectBridge
import com.kyant.backdrop.catalog.coloros.ColorOsTunableGlassBridge
import com.kyant.backdrop.catalog.coloros.TunableGlassParams
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * ColorOS SystemUI <-> Kyant implementation matrix.
 *
 * Rules used by this page:
 * 1. Kyant samples execute the upstream implementation in this repository.
 * 2. ColorOS samples load classes/shaders from installed ColorOS packages.
 * 3. A subsystem-specific shader is shown separately even if it shares a
 *    lower-level post-effect primitive with another SystemUI surface.
 * 4. SurfaceControl/SystemUI-host/GL-only code is never replaced by a generic
 *    look-alike. It is listed as a capability with the exact host constraint.
 * 5. A missing 1:1 Kyant mechanism is labelled as such rather than invented.
 */
@Composable
actual fun ColorOsNativeComparisonContent() {
    val context = LocalContext.current
    val density = LocalDensity.current
    var wallpaper by remember(context) { mutableStateOf(createTestWallpaper(context)) }
    var imageError by remember { mutableStateOf<String?>(null) }

    var refraction by remember { mutableFloatStateOf(1f) }
    var dispersion by remember { mutableFloatStateOf(1f) }
    var progressiveFraction by remember { mutableFloatStateOf(1f) }
    var chromaticOffset by remember { mutableFloatStateOf(5f) }
    var metaballPhase by remember { mutableFloatStateOf(0.5f) }
    var barGlowBend by remember { mutableFloatStateOf(0.055f) }
    var rawMetaballTime by remember { mutableFloatStateOf(1f) }

    val backdrop = rememberLayerBackdrop()
    val materialBridge = remember(context) { ColorOsMaterialBridge(context) }
    val postBridge = remember(context) { ColorOsSystemUiPostEffectBridge(context) }
    val executableBridge = remember(context) { ColorOsSystemUiExecutableBridge(context) }
    val systemUiCatalog = remember(context) { ColorOsSystemUiLiquidGlassCatalog(context).mappings() }
    val materialCatalog = remember(materialBridge) { materialBridge.catalog() }
    val shaderBlendModes = remember(executableBridge) { executableBridge.shaderBlendModes() }

    var glassStatus by remember { mutableStateOf("等待 ColorOS 锁屏玻璃…") }
    var blurStatus by remember { mutableStateOf("等待 COUI 模糊…") }
    var progressiveStatus by remember { mutableStateOf("等待 ColorOS 渐进模糊…") }
    var colorStatus by remember { mutableStateOf("等待 ColorOS 材质混色…") }
    var postStatus by remember { mutableStateOf("等待 SystemUI PostEffect…") }
    var strokeStatus by remember { mutableStateOf("等待 COUI 描边…") }
    var qsStrokeStatus by remember { mutableStateOf("等待 QS GradientStrokeShader…") }
    var volumeStrokeStatus by remember { mutableStateOf("等待 VolumeGradientStrokeShader…") }
    var barGlowStatus by remember { mutableStateOf("等待 SystemUI barglow…") }
    var chromaticStatus by remember { mutableStateOf("等待 SystemUI chromatic…") }
    var metaballStatus by remember { mutableStateOf("等待 DrawableShader Metaball…") }
    var rawMetaballStatus by remember { mutableStateOf("等待 res/raw/metaball.agsl…") }
    var spotStatus by remember { mutableStateOf("等待 COUI 聚光…") }
    var causticStatus by remember { mutableStateOf("等待 ColorOS 焦散阴影…") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Bitmap decode returned null")
            }.onSuccess {
                wallpaper = normalizeWallpaper(context, it)
                imageError = null
            }.onFailure {
                imageError = "图片加载失败：${describe(it)}"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.layerBackdrop(backdrop).fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .padding(bottom = 96.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                "Kyant ↔ ColorOS SystemUI Liquid Glass 全实现矩阵",
                style = TextStyle(Color.White, 22.sp, FontWeight.SemiBold),
            )
            BasicText(
                "这里按实际执行机制拆分，不按外观归类。ColorOS 一侧只运行设备安装的 personality-clocks、uxdesign 与 SystemUI 代码/着色器；需要 SurfaceControl、SystemUI 业务宿主或 OpenGL 管线的实现保留真实限制。",
                style = infoStyle(),
            )
            imageError?.let { BasicText(it, style = TextStyle(Color(0xFFFF8A80), 11.sp)) }

            MechanismSummary()

            SectionTitle("1 · 几何场 / SDF / 平滑圆角")
            BasicText(
                "Kyant 的镜片形状由解析圆角矩形距离场提供；SystemUI PostEffect 另有 G2、FULL、CONIC 三套圆角场。下面三块只切换 ColorOS CornerParams，不打开 Optics、描边或内阴影。",
                style = infoStyle(),
            )
            listOf("G2", "FULL", "CONIC").forEach { type ->
                SampleLabel("ColorOS SystemUI · CornerType.$type")
                AndroidView(
                    factory = { ColorOsPostEffectHostView(it) },
                    update = { view ->
                        view.configure(
                            wallpaper = wallpaper,
                            radiusPx = with(density) { 28.dp.toPx() },
                            cornerType = type,
                            optics = false,
                            gradientStroke = false,
                            innerShadow = false,
                            onStatus = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(78.dp),
                )
            }

            SectionTitle("2 · 折射 + 色散")
            BasicText(
                "Kyant lens()：解析 SDF 梯度 → 镜片边缘曲线 → 背景坐标偏移，可选多路色散。ColorOS 锁屏玻璃：软距离场梯度 → 折射位移 → RGB 三路壁纸采样。两者参数单位不同，因此滑杆只比较归一化响应。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · lens()")
            KyantLensSample(backdrop, refraction, dispersion > 0.01f)
            SampleLabel("ColorOS · GlassEffectBuilder / 当前固件 AGSL")
            AndroidView(
                factory = { ColorOsGlassHostView(it) },
                update = { view ->
                    view.onStatus = { glassStatus = it }
                    view.configure(
                        wallpaper,
                        TunableGlassParams(
                            refractionIntensityScale = 0.55f * refraction,
                            dispersionIntensityScale = 0.20f * dispersion,
                        ),
                        with(density) { 38.dp.toPx() },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            StatusText(glassStatus)
            NeutralSlider("折射强度", refraction, 0f..1.6f) { refraction = it }
            NeutralSlider("色散强度", dispersion, 0f..2f) { dispersion = it }

            SectionTitle("3 · 背景模糊 / 渐进模糊 / 材质混色")
            SampleLabel("Kyant · blur()")
            KyantBlurSample(backdrop)
            SampleLabel("ColorOS · COUIMaterialBlurEffect")
            MaterialSample(
                sample = MaterialSampleKind.Blur,
                status = { blurStatus = it },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            StatusText(blurStatus)

            BasicText(
                "渐进模糊：Kyant 示例是模糊后以 RuntimeShader alpha mask 渐隐；ColorOS AppBarBlurHelper 直接走 OplusRenderEffect.createGradientBlurEffect。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · blur + alpha mask")
            KyantProgressiveSample(backdrop)
            SampleLabel("ColorOS · AppBarBlurHelper")
            MaterialSample(
                sample = MaterialSampleKind.GradientBlur,
                fraction = progressiveFraction,
                status = { progressiveStatus = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            StatusText(progressiveStatus)
            NeutralSlider("渐进模糊进度", progressiveFraction, 0f..1f) { progressiveFraction = it }

            BasicText(
                "混色：Kyant 暴露 vibrancy / colorControls；COUIMaterialBlurEffect 把模糊与两层 BlendModeColorFilter 绑定成材质配方。SystemUI DrawableShader 还另有 ShaderBlendParam、多重混色与前景混色。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · vibrancy()")
            KyantVibrancySample(backdrop)
            SampleLabel("ColorOS · COUI BlurEffect + 双 BlendMode 层")
            MaterialSample(
                sample = MaterialSampleKind.ColorMaterial,
                status = { colorStatus = it },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            StatusText(colorStatus)
            if (shaderBlendModes.isNotEmpty()) {
                BasicText("SystemUI ShaderBlendParam 模式探针：${shaderBlendModes.joinToString()}", style = diagnosticsStyle())
            }

            SectionTitle("4 · 通用 PostEffect：Optics + 渐变描边 + 内阴影")
            BasicText(
                "这条是 SystemUI 的通用 DrawableShader 组合，不用 COUI 或锁屏 shader 代替。Kyant 对照项分别是 Highlight、Highlight stroke 与 InnerShadow。Optics 是边缘光学覆盖层，不等于背景折射。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · Highlight + Shadow + InnerShadow")
            KyantEdgeSample(backdrop)
            SampleLabel("ColorOS SystemUI · BlendDrawable")
            AndroidView(
                factory = { ColorOsPostEffectHostView(it) },
                update = { view ->
                    view.configure(
                        wallpaper = wallpaper,
                        radiusPx = with(density) { 38.dp.toPx() },
                        cornerType = "G2",
                        optics = true,
                        gradientStroke = true,
                        innerShadow = true,
                        onStatus = { postStatus = it },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(145.dp),
            )
            StatusText(postStatus)

            SampleLabel("ColorOS COUI · MaterialStroke 独立路径")
            MaterialSample(
                sample = MaterialSampleKind.Stroke,
                status = { strokeStatus = it },
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            StatusText(strokeStatus)

            SectionTitle("5 · SystemUI 业务专用描边：QS 与音量面板")
            BasicText(
                "不能因为二者都叫渐变描边就只展示通用 GradientStrokeLine。QS 有自己的 GradientStrokeShader（G2/CONIC），音量面板还有独立 VolumeGradientStrokeShader（FULL/SMOOTH）。下面分别直接实例化这两个 SystemUI RuntimeShader。",
                style = infoStyle(),
            )
            SampleLabel("ColorOS QS · GradientStrokeShader")
            AndroidView(
                factory = { SystemUiStrokeHostView(it, SystemUiStrokeKind.QS) },
                update = { view ->
                    view.onStatus = { qsStrokeStatus = it }
                    view.configure(with(density) { 34.dp.toPx() })
                },
                modifier = Modifier.fillMaxWidth().height(104.dp),
            )
            StatusText(qsStrokeStatus)

            SampleLabel("ColorOS Volume · VolumeGradientStrokeShader")
            AndroidView(
                factory = { SystemUiStrokeHostView(it, SystemUiStrokeKind.Volume) },
                update = { view ->
                    view.onStatus = { volumeStrokeStatus = it }
                    view.configure(with(density) { 34.dp.toPx() })
                },
                modifier = Modifier.fillMaxWidth().height(104.dp),
            )
            StatusText(volumeStrokeStatus)

            SectionTitle("6 · SystemUI barglow.agsl：SDF 发光 + 色散")
            BasicText(
                "barglow 不是背景折射。它自己计算抛物线/线段距离场，以指数衰减生成发光，并在发光上做正负方向色差，再与输入纹理合成。Kyant 最接近的是 Highlight + 色散视觉子机制，但没有 1:1 的 bar-glow primitive。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsBarGlowHostView(it) },
                update = { view ->
                    view.onStatus = { barGlowStatus = it }
                    view.configure(wallpaper, barGlowBend)
                },
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            StatusText(barGlowStatus)
            NeutralSlider("弯曲量", barGlowBend, -0.10f..0.12f) { barGlowBend = it }

            SectionTitle("7 · SystemUI chromatic.agsl：独立色差工具")
            BasicText(
                "Kyant 的色散集成在 lens() 内；SystemUI 还额外携带可独立工作的 chromatic.agsl。这里对任意输入做中心、正偏移、负偏移三次采样，只验证色差工具，不把它误标成折射。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsChromaticHostView(it) },
                update = { view ->
                    view.onStatus = { chromaticStatus = it }
                    view.configure(wallpaper, chromaticOffset)
                },
                modifier = Modifier.fillMaxWidth().height(118.dp),
            )
            StatusText(chromaticStatus)
            NeutralSlider("色差偏移", chromaticOffset, 0f..18f, " px") { chromaticOffset = it }

            SectionTitle("8 · Metaball：几何融合模块与独立 raw shader")
            BasicText(
                "这里必须拆成两项。第一项是 DrawableShader 内置的真正 Metaball SDF 融合模块：它可借普通 BlendDrawable 单独运行；完整 MetaBallBlurDrawable 再叠加实时模糊 transport 时才要求 SurfaceControl。Kyant 当前 core 没有 1:1 Metaball primitive，只能拿 shape/SDF 架构做能力邻接对照。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsMetaBallHostView(it) },
                update = { view ->
                    view.onStatus = { metaballStatus = it }
                    view.configure(wallpaper, metaballPhase, with(density) { 36.dp.toPx() })
                },
                modifier = Modifier.fillMaxWidth().height(145.dp),
            )
            StatusText(metaballStatus)
            NeutralSlider("Metaball 相位", metaballPhase, 0f..1f) { metaballPhase = it }

            BasicText(
                "第二项是 SystemUI 自带 res/raw/metaball.agsl：它是圆形范围内的旋转纹理/光照遮罩，不是上面的几何融合算法。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsRawMetaballHostView(it) },
                update = { view ->
                    view.onStatus = { rawMetaballStatus = it }
                    view.configure(wallpaper, rawMetaballTime)
                },
                modifier = Modifier.fillMaxWidth().height(145.dp),
            )
            StatusText(rawMetaballStatus)
            NeutralSlider("raw shader 时间", rawMetaballTime, 0f..20f) { rawMetaballTime = it }

            SectionTitle("9 · 交互聚光 / 焦散阴影")
            SampleLabel("Kyant · InteractiveHighlight")
            KyantInteractiveSample(backdrop)
            SampleLabel("ColorOS · COUISpotLightEffect")
            MaterialSample(
                sample = MaterialSampleKind.SpotLight,
                status = { spotStatus = it },
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            StatusText(spotStatus)

            BasicText(
                "ColorOS ToolbarMaterialEffectDelegate 还维护独立 caustic-shadow 开关，并可和 blur/stroke/spotlight 同时启用。Kyant 的 Shadow 只有能力邻接关系，不能把两者当成同一算法。",
                style = infoStyle(),
            )
            MaterialSample(
                sample = MaterialSampleKind.ToolbarCaustic,
                status = { causticStatus = it },
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            StatusText(causticStatus)

            SectionTitle("10 · SystemUI 完整实现清单 ↔ Kyant 对照")
            BasicText(
                "下面按核心后处理、SystemUI 着色器资产、公共模糊基础设施、通知、控制中心/QS、音量、壁纸、生物识别与全局面板逐项探测。DIRECT_VIEW 已在上方或可在普通 View 执行；SURFACE_CONTROL / SYSTEM_UI_HOST / GL_PIPELINE 保持原宿主约束。",
                style = infoStyle(),
            )
            systemUiCatalog.groupBy { it.group }.forEach { (group, entries) ->
                BasicText(group, style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                entries.forEach { item ->
                    val available = item.status.startsWith("available")
                    val statusColor = if (available) Color(0xFF9DE7AA) else Color(0xFFFFB4A9)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.20f))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        BasicText(item.systemUiImplementation, style = TextStyle(Color.White, 10.sp, FontWeight.Medium))
                        BasicText("↔ Kyant：${item.kyantCounterpart}", style = diagnosticsStyle())
                        BasicText("${item.executionMode} · ${item.status}", style = TextStyle(statusColor, 9.sp))
                        BasicText(item.note, style = diagnosticsStyle())
                    }
                }
            }

            SectionTitle("11 · COUI 原生预设目录")
            BasicText("Blur (${materialCatalog.blur.size})：${materialCatalog.blur.joinToString()}", style = diagnosticsStyle())
            BasicText("Stroke (${materialCatalog.stroke.size})：${materialCatalog.stroke.joinToString()}", style = diagnosticsStyle())
            BasicText("SpotLight (${materialCatalog.spotLight.size})：${materialCatalog.spotLight.joinToString()}", style = diagnosticsStyle())
            BasicText("Toolbar (${materialCatalog.toolbarCategories.size})：${materialCatalog.toolbarCategories.joinToString()}", style = diagnosticsStyle())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlainButton("选择背景") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PlainButton("恢复参数") {
                    refraction = 1f
                    dispersion = 1f
                    progressiveFraction = 1f
                    chromaticOffset = 5f
                    metaballPhase = 0.5f
                    barGlowBend = 0.055f
                    rawMetaballTime = 1f
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MechanismSummary() {
    val lines = listOf(
        "背景取样：Kyant LayerBackdrop ↔ SystemUI 截图/平台/壁纸/SurfaceControl 多后端",
        "模糊：Kyant blur ↔ COUI blur / AppBar gradient blur / SystemUI GL + continuous blur",
        "折射：Kyant lens ↔ keyguard GlassEffectBuilder（SystemUI PostEffect Optics 不是折射）",
        "色散：Kyant lens 色散 ↔ keyguard RGB 折射色散 + SystemUI chromatic/barglow 色差",
        "形状：Kyant RoundedRectangle SDF ↔ PostEffect G2/FULL/CONIC + Metaball SDF",
        "轮廓：Kyant Highlight/InnerShadow ↔ COUI stroke + PostEffect optics/stroke/shadow + QS/Volume 专用 shader",
        "交互：Kyant InteractiveHighlight ↔ COUI/SystemUI Spotlight hosts",
        "无 1:1：Kyant core 当前没有 Metaball fusion、caustic-shadow 或 barglow primitive",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("机制覆盖", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
        lines.forEach { BasicText(it, style = diagnosticsStyle()) }
    }
}

@Composable
private fun KyantLensSample(backdrop: Backdrop, refraction: Float, dispersion: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(145.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(38.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 34.dp.toPx() * refraction,
                        depthEffect = true,
                        chromaticAberration = dispersion,
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant lens", style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun KyantBlurSample(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = { blur(18.dp.toPx()) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant blur", style = TextStyle(Color.White, 16.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantVibrancySample(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = {
                    blur(10.dp.toPx())
                    vibrancy()
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant vibrancy", style = TextStyle(Color.White, 16.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantProgressiveSample(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { androidx.compose.ui.graphics.RectangleShape },
                effects = {
                    blur(4.dp.toPx())
                    runtimeShaderEffect(
                        "AlphaMaskComparison",
                        """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;
half4 main(float2 coord) {
    float a = smoothstep(size.y, size.y * 0.5, coord.y);
    return mix(content.eval(coord) * a, tint * a, tintIntensity);
}
""",
                        "content",
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", Color.White)
                        setFloatUniform("tintIntensity", 0.16f)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant progressive", style = TextStyle(Color.White, 16.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantEdgeSample(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(145.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(38.dp) },
                effects = { blur(8.dp.toPx()) },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant edge stack", style = TextStyle(Color.White, 16.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantInteractiveSample(backdrop: Backdrop) {
    val coroutineScope = rememberCoroutineScope()
    val interactive = remember(coroutineScope) { InteractiveHighlight(coroutineScope) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(28.dp) },
                effects = { blur(8.dp.toPx()) },
                highlight = { Highlight.Ambient },
                shadow = null,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
            )
            .then(interactive.modifier)
            .then(interactive.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("按住并拖动", style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
    }
}

private enum class MaterialSampleKind {
    Blur,
    GradientBlur,
    ColorMaterial,
    Stroke,
    SpotLight,
    ToolbarCaustic,
}

@Composable
private fun MaterialSample(
    sample: MaterialSampleKind,
    fraction: Float = 1f,
    status: (String) -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        factory = { ColorOsMaterialHostView(it) },
        update = { view ->
            view.onStatus = status
            view.configure(sample, fraction)
        },
        modifier = modifier,
    )
}

private class ColorOsMaterialHostView(context: Context) : View(context) {
    private val bridge = ColorOsMaterialBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
    private var kind: MaterialSampleKind? = null
    private var fraction = 1f
    private var appliedKey: String? = null
    var onStatus: ((String) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), 28f * resources.displayMetrics.density)
            }
        }
    }

    fun configure(kind: MaterialSampleKind, fraction: Float = 1f) {
        this.kind = kind
        this.fraction = fraction.coerceIn(0f, 1f)
        applyIfReady()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        appliedKey = null
        applyIfReady()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = 28f * resources.displayMetrics.density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val mode = kind ?: return
        if (width <= 0 || height <= 0) return
        val key = "$mode:$fraction:$width:$height"
        if (key == appliedKey) return
        appliedKey = key
        bridge.clear(this)
        val result = when (mode) {
            MaterialSampleKind.Blur -> bridge.applyBlur(this, "TYPE_FRAMEWORK_TOP_BAR_BLUR")
            MaterialSampleKind.GradientBlur -> bridge.applyGradientBlur(this, fraction)
            MaterialSampleKind.ColorMaterial -> bridge.applyBlur(this, "TYPE_CONTENT_BUTTON_1")
            MaterialSampleKind.Stroke -> bridge.applyStroke(this, "TYPE_FRAMEWORK_CAPSULE_6")
            MaterialSampleKind.SpotLight -> bridge.applySpotLight(this, "TYPE_TRANSLUCENT_SMALL_1")
            MaterialSampleKind.ToolbarCaustic -> bridge.applyToolbarStack(
                view = this,
                categoryName = "TOOLBAR_BUTTON",
                blur = true,
                stroke = true,
                spotLight = true,
                caustic = true,
                forceEnable = true,
            )
        }
        result.onSuccess {
            onStatus?.invoke("PASS — $mode")
        }.onFailure {
            appliedKey = null
            onStatus?.invoke("UNAVAILABLE — $mode: ${describe(it)}")
        }
    }
}

private class ColorOsGlassHostView(context: Context) : View(context) {
    private val bridge = ColorOsTunableGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wallpaper: Bitmap? = null
    private var params = TunableGlassParams()
    private var radiusPx = 0f
    private var markerColor: Int? = bridge.locationColor().getOrNull()
    private var attachedWallpaperId = 0
    private var attachedWidth = 0
    private var attachedHeight = 0
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (!updatePosted) {
            updatePosted = true
            postOnAnimation {
                updatePosted = false
                bridge.updateGeometry(this).onFailure { onStatus?.invoke("UNAVAILABLE — geometry: ${describe(it)}") }
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radiusPx)
            }
        }
    }

    fun configure(wallpaper: Bitmap, params: TunableGlassParams, radiusPx: Float) {
        val changed = this.wallpaper !== wallpaper
        this.wallpaper = wallpaper
        this.params = params
        this.radiusPx = radiusPx
        if (changed) attachedWallpaperId = 0
        invalidateOutline()
        invalidate()
        postOnAnimation { applyIfReady() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        attachedWallpaperId = 0
        postOnAnimation { applyIfReady() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        attachedWallpaperId = 0
        invalidateOutline()
        postOnAnimation { applyIfReady() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        markerColor?.let {
            paint.color = it
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
        }
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        markerColor = markerColor ?: bridge.locationColor().getOrNull()
        val id = System.identityHashCode(bg)
        val needsAttach = id != attachedWallpaperId || width != attachedWidth || height != attachedHeight
        val result = if (needsAttach) {
            bridge.attach(this, bg, params).onSuccess {
                attachedWallpaperId = id
                attachedWidth = width
                attachedHeight = height
            }
        } else {
            bridge.update(this, params).map { "live uniforms updated" }
        }
        result.onSuccess {
            onStatus?.invoke("PASS — ColorOS keyguard glass: $it")
        }.onFailure {
            attachedWallpaperId = 0
            onStatus?.invoke("UNAVAILABLE — ColorOS keyguard glass: ${describe(it)}")
        }
    }
}

private class ColorOsPostEffectHostView(context: Context) : View(context) {
    private val bridge = ColorOsSystemUiPostEffectBridge(context)
    private var wallpaper: Bitmap? = null
    private var radiusPx = 0f
    private var cornerType = "G2"
    private var optics = true
    private var gradientStroke = true
    private var innerShadow = true
    private var statusSink: ((String) -> Unit)? = null
    private var lastKey: String? = null
    private var updatePosted = false
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configure(
        wallpaper: Bitmap,
        radiusPx: Float,
        cornerType: String,
        optics: Boolean,
        gradientStroke: Boolean,
        innerShadow: Boolean,
        onStatus: ((String) -> Unit)?,
    ) {
        this.wallpaper = wallpaper
        this.radiusPx = radiusPx
        this.cornerType = cornerType
        this.optics = optics
        this.gradientStroke = gradientStroke
        this.innerShadow = innerShadow
        this.statusSink = onStatus
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        scheduleApply()
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = "${System.identityHashCode(bg)}:$width:$height:${location[0]}:${location[1]}:$radiusPx:$cornerType:$optics:$gradientStroke:$innerShadow"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            val crop = bridge.cropWallpaperForView(bg, this)
            foreground = bridge.createPostEffectDrawable(
                bitmap = crop,
                width = width,
                height = height,
                options = ColorOsSystemUiPostEffectBridge.PostEffectOptions(
                    cornerType = cornerType,
                    cornerRadiusPx = radiusPx,
                    cornerWeight = 1f,
                    optics = optics,
                    gradientStroke = gradientStroke,
                    innerShadow = innerShadow,
                ),
            ).getOrThrow()
            invalidate()
        }.onSuccess {
            statusSink?.invoke("PASS — BlendDrawable $cornerType / optics=$optics / stroke=$gradientStroke / innerShadow=$innerShadow")
        }.onFailure {
            lastKey = null
            foreground = null
            statusSink?.invoke("UNAVAILABLE — SystemUI PostEffect: ${describe(it)}")
        }
    }
}

private enum class SystemUiStrokeKind { QS, Volume }

private class SystemUiStrokeHostView(context: Context, private val kind: SystemUiStrokeKind) : View(context) {
    private val bridge = ColorOsSystemUiExecutableBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF }
    private var radiusPx = 0f
    private var shader: RuntimeShader? = null
    private var lastKey: String? = null
    var onStatus: ((String) -> Unit)? = null

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configure(radiusPx: Float) {
        this.radiusPx = radiusPx
        applyIfReady()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        applyIfReady()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, basePaint)
        shader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
    }

    private fun applyIfReady() {
        if (width <= 0 || height <= 0) return
        val key = "$kind:$width:$height:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        val result = when (kind) {
            SystemUiStrokeKind.QS -> bridge.createQsStrokeShader(width, height, radiusPx)
            SystemUiStrokeKind.Volume -> bridge.createVolumeStrokeShader(width, height, radiusPx)
        }
        result.onSuccess {
            shader = it
            invalidate()
            onStatus?.invoke("PASS — $kind shipping RuntimeShader")
        }.onFailure {
            shader = null
            lastKey = null
            onStatus?.invoke("UNAVAILABLE — $kind stroke: ${describe(it)}")
        }
    }
}

private class ColorOsBarGlowHostView(context: Context) : View(context) {
    private val postBridge = ColorOsSystemUiPostEffectBridge(context)
    private val bridge = ColorOsSystemUiExecutableBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var wallpaper: Bitmap? = null
    private var crop: Bitmap? = null
    private var bend = 0.055f
    private var lastKey: String? = null
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configure(wallpaper: Bitmap, bend: Float) {
        this.wallpaper = wallpaper
        this.bend = bend
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        scheduleApply()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        crop?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint) }
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        crop?.recycle()
        crop = null
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val key = "${System.identityHashCode(bg)}:$width:$height:${loc[0]}:${loc[1]}:$bend"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            crop?.recycle()
            crop = postBridge.cropWallpaperForView(bg, this)
            invalidate()
            bridge.applyBarGlow(this, bend = bend).getOrThrow()
        }.onSuccess {
            onStatus?.invoke("PASS — SystemUI assets/barglow.agsl")
        }.onFailure {
            lastKey = null
            bridge.clear(this)
            onStatus?.invoke("UNAVAILABLE — barglow: ${describe(it)}")
        }
    }
}

private class ColorOsChromaticHostView(context: Context) : View(context) {
    private val bridge = ColorOsSystemUiPostEffectBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var wallpaper: Bitmap? = null
    private var crop: Bitmap? = null
    private var offsetPx = 0f
    private var lastKey: String? = null
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), 28f * resources.displayMetrics.density)
            }
        }
    }

    fun configure(wallpaper: Bitmap, offsetPx: Float) {
        this.wallpaper = wallpaper
        this.offsetPx = offsetPx
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        lastKey = null
        scheduleApply()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        crop?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint) }
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        crop?.recycle()
        crop = null
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val key = "${System.identityHashCode(bg)}:$width:$height:${loc[0]}:${loc[1]}:$offsetPx"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            crop?.recycle()
            crop = bridge.cropWallpaperForView(bg, this)
            invalidate()
            bridge.applyChromatic(this, offsetPx).getOrThrow()
        }.onSuccess {
            onStatus?.invoke("PASS — SystemUI assets/chromatic.agsl")
        }.onFailure {
            lastKey = null
            bridge.clear(this)
            onStatus?.invoke("UNAVAILABLE — chromatic: ${describe(it)}")
        }
    }
}

private class ColorOsMetaBallHostView(context: Context) : View(context) {
    private val postBridge = ColorOsSystemUiPostEffectBridge(context)
    private val bridge = ColorOsSystemUiExecutableBridge(context)
    private var wallpaper: Bitmap? = null
    private var phase = 0.5f
    private var radiusPx = 0f
    private var lastKey: String? = null
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configure(wallpaper: Bitmap, phase: Float, radiusPx: Float) {
        this.wallpaper = wallpaper
        this.phase = phase
        this.radiusPx = radiusPx
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        scheduleApply()
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val key = "${System.identityHashCode(bg)}:$width:$height:${loc[0]}:${loc[1]}:$phase:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            val crop = postBridge.cropWallpaperForView(bg, this)
            foreground = bridge.createMetaBallPostEffectDrawable(crop, width, height, radiusPx, phase).getOrThrow()
            invalidate()
        }.onSuccess {
            onStatus?.invoke("PASS — DrawableShader MetaBall AGSL on BlendDrawable")
        }.onFailure {
            lastKey = null
            foreground = null
            onStatus?.invoke("UNAVAILABLE — Metaball module: ${describe(it)}")
        }
    }
}

private class ColorOsRawMetaballHostView(context: Context) : View(context) {
    private val postBridge = ColorOsSystemUiPostEffectBridge(context)
    private val bridge = ColorOsSystemUiExecutableBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wallpaper: Bitmap? = null
    private var time = 0f
    private var crop: Bitmap? = null
    private var shader: RuntimeShader? = null
    private var lastKey: String? = null
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configure(wallpaper: Bitmap, time: Float) {
        this.wallpaper = wallpaper
        this.time = time
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        scheduleApply()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        shader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
    }

    override fun onDetachedFromWindow() {
        crop?.recycle()
        crop = null
        shader = null
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val key = "${System.identityHashCode(bg)}:$width:$height:${loc[0]}:${loc[1]}:$time"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            crop?.recycle()
            crop = postBridge.cropWallpaperForView(bg, this)
            shader = bridge.createRawMetaballShader(crop!!, width, height, time).getOrThrow()
            invalidate()
        }.onSuccess {
            onStatus?.invoke("PASS — SystemUI res/raw/metaball.agsl")
        }.onFailure {
            lastKey = null
            shader = null
            onStatus?.invoke("UNAVAILABLE — raw metaball: ${describe(it)}")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(6.dp))
    BasicText(text, style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold))
}

@Composable
private fun SampleLabel(text: String) {
    BasicText(text, style = TextStyle(Color.White.copy(alpha = 0.92f), 13.sp, FontWeight.Medium))
}

@Composable
private fun StatusText(text: String) {
    val color = when {
        text.startsWith("PASS") -> Color(0xFF8EE6A2)
        text.startsWith("UNAVAILABLE") -> Color(0xFFFFCC80)
        else -> Color.White.copy(alpha = 0.68f)
    }
    BasicText(text, style = TextStyle(color, 10.sp))
}

@Composable
private fun NeutralSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onValueChange: (Float) -> Unit,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - range.start) / span).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        BasicText("$label ${value.round2()}$suffix", style = TextStyle(Color.White.copy(alpha = 0.84f), 12.sp))
        AndroidView(
            factory = { SeekBar(it).apply { max = 1000 } },
            update = { seek ->
                seek.setOnSeekBarChangeListener(null)
                seek.progress = (normalized * seek.max).roundToInt().coerceIn(0, seek.max)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onValueChange(range.start + span * (progress / 1000f))
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            modifier = Modifier.fillMaxWidth().height(38.dp),
        )
    }
}

@Composable
private fun PlainButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
    }
}

private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f
private fun infoStyle(): TextStyle = TextStyle(Color.White.copy(alpha = 0.75f), 11.sp)
private fun diagnosticsStyle(): TextStyle = TextStyle(Color.White.copy(alpha = 0.68f), 10.sp)

private fun createTestWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val bitmap = Bitmap.createBitmap(
        dm.widthPixels.coerceAtLeast(720),
        dm.heightPixels.coerceAtLeast(1280),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        bitmap.width.toFloat(),
        bitmap.height.toFloat(),
        intArrayOf(
            AndroidColor.rgb(24, 43, 92),
            AndroidColor.rgb(143, 72, 172),
            AndroidColor.rgb(24, 168, 196),
            AndroidColor.rgb(252, 187, 61),
        ),
        floatArrayOf(0f, 0.35f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(bitmap.width * 0.22f, bitmap.height * 0.28f, bitmap.width * 0.14f, paint)
    paint.color = AndroidColor.rgb(255, 74, 112)
    canvas.drawCircle(bitmap.width * 0.78f, bitmap.height * 0.48f, bitmap.width * 0.18f, paint)
    paint.color = AndroidColor.rgb(54, 238, 201)
    canvas.drawCircle(bitmap.width * 0.38f, bitmap.height * 0.78f, bitmap.width * 0.16f, paint)
    paint.color = 0xAAFFFFFF.toInt()
    paint.strokeWidth = 2f
    val step = (bitmap.width / 18f).coerceAtLeast(24f)
    var x = 0f
    while (x < bitmap.width) {
        canvas.drawLine(x, 0f, x, bitmap.height.toFloat(), paint)
        x += step
    }
    return bitmap
}

private fun normalizeWallpaper(context: Context, source: Bitmap): Bitmap {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(1)
    val h = dm.heightPixels.coerceAtLeast(1)
    if (source.width == w && source.height == h) return source
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val scale = maxOf(w / source.width.toFloat(), h / source.height.toFloat())
    val sw = (w / scale).toInt().coerceAtLeast(1)
    val sh = (h / scale).toInt().coerceAtLeast(1)
    val left = ((source.width - sw) / 2).coerceAtLeast(0)
    val top = ((source.height - sh) / 2).coerceAtLeast(0)
    Canvas(out).drawBitmap(
        source,
        Rect(left, top, (left + sw).coerceAtMost(source.width), (top + sh).coerceAtMost(source.height)),
        Rect(0, 0, w, h),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    return out
}

private fun describe(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}
