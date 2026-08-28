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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsMaterialBridge
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
 * A/B implementation matrix rather than a visual look-alike page.
 *
 * Left/reference samples execute the upstream Kyant implementation from this
 * repository. ColorOS samples dynamically execute code or shader assets from
 * the installed ColorOS packages. A capability that needs SurfaceControl is
 * reported but never silently replaced by a generic implementation.
 */
@Composable
actual fun ColorOsNativeComparisonContent() {
    val context = LocalContext.current
    val density = LocalDensity.current
    var wallpaper by remember(context) { mutableStateOf(createTestWallpaper(context)) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var refraction by remember { mutableFloatStateOf(1f) }
    var dispersion by remember { mutableFloatStateOf(1f) }
    var progressiveFraction by remember { mutableFloatStateOf(1f) }
    var chromaticOffset by remember { mutableFloatStateOf(5f) }

    var glassStatus by remember { mutableStateOf("等待 ColorOS 锁屏玻璃…") }
    var blurStatus by remember { mutableStateOf("等待 COUI 背景模糊…") }
    var progressiveStatus by remember { mutableStateOf("等待 ColorOS 渐进模糊…") }
    var postEffectStatus by remember { mutableStateOf("等待 SystemUI PostEffect…") }
    var chromaticStatus by remember { mutableStateOf("等待 SystemUI 色散着色器…") }
    var spotStatus by remember { mutableStateOf("等待 COUI 交互聚光…") }
    var causticStatus by remember { mutableStateOf("等待 ColorOS 焦散阴影…") }

    val backdrop = rememberLayerBackdrop()
    val materialBridge = remember(context) { ColorOsMaterialBridge(context) }
    val postEffectBridge = remember(context) { ColorOsSystemUiPostEffectBridge(context) }
    val materialCatalog = remember(materialBridge) { materialBridge.catalog() }
    val systemUiCapabilities = remember(postEffectBridge) { postEffectBridge.capabilities() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: error("Bitmap decode returned null")
            }.onSuccess {
                wallpaper = normalizeWallpaper(context, it)
                loadError = null
            }.onFailure {
                loadError = "图片加载失败：${describe(it)}"
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                "Kyant ↔ ColorOS Liquid Glass 实现矩阵",
                style = TextStyle(Color.White, 23.sp, FontWeight.SemiBold),
            )
            BasicText(
                "这里不按外观猜实现。Kyant 一侧运行仓库原实现；ColorOS 一侧只调用设备已安装的 personality-clocks、uxdesign 和 SystemUI。需要 SurfaceControl 的能力只报告存在性，不做仿制回退。",
                style = TextStyle(Color.White.copy(alpha = 0.82f), 12.sp),
            )
            loadError?.let { BasicText(it, style = TextStyle(Color(0xFFFF8A80), 12.sp)) }

            MatrixSummary()

            SectionTitle("1 · 折射 + 色散：两套真实镜片管线")
            BasicText(
                "Kyant：解析圆角矩形 SDF → 解析梯度 → circleMap 边缘曲线 → 背景偏移采样，可选 7 路色散。ColorOS：模糊蒙版软场 → 像素梯度 → REFRACTION_RANGE/INTENSITY → RGB 三路壁纸偏移采样。数值单位不同，下面滑杆只做归一化视觉对照。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · lens()")
            KyantLensSample(backdrop, refraction, dispersion > 0.01f)
            SampleLabel("ColorOS · GlassEffectBuilder / 当前固件 AGSL")
            AndroidView(
                factory = { ColorOsTunableGlassHostView(it) },
                update = { view ->
                    view.onStatus = { glassStatus = it }
                    view.configure(
                        wallpaper = wallpaper,
                        radiusPx = with(density) { 38.dp.toPx() },
                        params = TunableGlassParams(
                            refractionIntensityScale = 0.55f * refraction,
                            dispersionIntensityScale = 0.20f * dispersion,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            StatusText(glassStatus)
            NeutralSlider("折射强度", refraction, 0f..1.6f) { refraction = it }
            NeutralSlider("色散强度", dispersion, 0f..2f) { dispersion = it }

            SectionTitle("2 · 背景模糊")
            BasicText(
                "Kyant 的 blur() 是标准 RenderEffect 模糊；ColorOS 这里调用 COUIMaterialBlurEffect 的原生背景材质，不把锁屏玻璃当通用模糊。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · BlurEffect")
            KyantBlurSample(backdrop)
            SampleLabel("ColorOS · COUIMaterialBlurEffect")
            AndroidView(
                factory = { ColorOsMaterialHostView(it) },
                update = { view ->
                    view.onStatus = { blurStatus = it }
                    view.configure(ColorOsMaterialSample.Blur)
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            StatusText(blurStatus)

            SectionTitle("3 · 渐进模糊")
            BasicText(
                "Kyant 示例是“先整体模糊，再用 RuntimeShader alpha mask 渐隐”；ColorOS 的 AppBarBlurHelper 直接走 OplusRenderEffect.createGradientBlurEffect。两者视觉目标相近，但执行图不同。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · blur + AlphaMask RuntimeShader")
            KyantProgressiveSample(backdrop)
            SampleLabel("ColorOS · AppBarBlurHelper")
            AndroidView(
                factory = { ColorOsMaterialHostView(it) },
                update = { view ->
                    view.onStatus = { progressiveStatus = it }
                    view.configure(ColorOsMaterialSample.GradientBlur, progressiveFraction)
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            StatusText(progressiveStatus)
            NeutralSlider("ColorOS 渐进模糊进度", progressiveFraction, 0f..1f) { progressiveFraction = it }

            SectionTitle("4 · 圆角场 + 高光/描边 + 内阴影")
            BasicText(
                "Kyant 的外观层由解析 SDF 高光、描边式 Highlight、Shadow 与 InnerShadow 组合；SystemUI 的 PostEffect 则有独立 G2/FULL/CONIC CornerParams、OpticsParams、GradientStrokeLineParams 与 InnerShadowParams。下面 ColorOS 卡片直接实例化 BlendDrawable。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · Highlight + Shadow + InnerShadow")
            KyantEdgeSample(backdrop)
            SampleLabel("ColorOS SystemUI · G2 + Optics + GradientStroke + InnerShadow")
            AndroidView(
                factory = { ColorOsPostEffectHostView(it) },
                update = { view ->
                    view.onStatus = { postEffectStatus = it }
                    view.configure(wallpaper, with(density) { 38.dp.toPx() })
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            StatusText(postEffectStatus)

            SectionTitle("5 · 色差/色散工具")
            BasicText(
                "Kyant 的色散只存在于 lens() 内；ColorOS 锁屏玻璃也把色散集成在折射函数里。另外 SystemUI 还单独携带 chromatic.agsl，可对任意输入做正/负方向 RGB 偏移。下面这一项专门验证这个独立工具，不把它误称为锁屏折射。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsChromaticHostView(it) },
                update = { view ->
                    view.onStatus = { chromaticStatus = it }
                    view.configure(wallpaper, chromaticOffset)
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            StatusText(chromaticStatus)
            NeutralSlider("SystemUI 色差偏移", chromaticOffset, 0f..18f, " px") { chromaticOffset = it }

            SectionTitle("6 · 交互高光 / 聚光")
            BasicText(
                "Kyant 的 InteractiveHighlight 是按压位置驱动的径向 RuntimeShader；ColorOS 的 COUISpotLightEffect 是设备原生交互聚光。按住并拖动两块区域比较。",
                style = infoStyle(),
            )
            SampleLabel("Kyant · InteractiveHighlight")
            KyantInteractiveSample(backdrop)
            SampleLabel("ColorOS · COUISpotLightEffect")
            AndroidView(
                factory = { ColorOsMaterialHostView(it) },
                update = { view ->
                    view.onStatus = { spotStatus = it }
                    view.configure(ColorOsMaterialSample.SpotLight)
                },
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
            StatusText(spotStatus)

            SectionTitle("7 · 焦散阴影 / Toolbar 材质栈")
            BasicText(
                "Kyant core 只有通用 Shadow；ColorOS ToolbarMaterialEffectDelegate 有独立 caustic-shadow 开关，并可同时打开 blur/stroke/spotlight。这里展示 ColorOS 完整 Toolbar 栈，而不是把它等同成 Kyant Shadow。",
                style = infoStyle(),
            )
            AndroidView(
                factory = { ColorOsMaterialHostView(it) },
                update = { view ->
                    view.onStatus = { causticStatus = it }
                    view.configure(ColorOsMaterialSample.ToolbarCaustic)
                },
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
            StatusText(causticStatus)

            SectionTitle("8 · ColorOS 运行时能力清单")
            BasicText(
                "COUI Blur presets (${materialCatalog.blur.size})：${materialCatalog.blur.joinToString()}",
                style = diagnosticsStyle(),
            )
            BasicText(
                "COUI Stroke presets (${materialCatalog.stroke.size})：${materialCatalog.stroke.joinToString()}",
                style = diagnosticsStyle(),
            )
            BasicText(
                "COUI SpotLight presets (${materialCatalog.spotLight.size})：${materialCatalog.spotLight.joinToString()}",
                style = diagnosticsStyle(),
            )
            BasicText(
                "Toolbar categories (${materialCatalog.toolbarCategories.size})：${materialCatalog.toolbarCategories.joinToString()}",
                style = diagnosticsStyle(),
            )
            systemUiCapabilities.forEach { capability ->
                val execution = if (capability.runnableInOrdinaryView) "普通 View 可执行" else "系统/SurfaceControl 专属或仅诊断"
                BasicText(
                    "• ${capability.name} — ${capability.status} — $execution",
                    style = diagnosticsStyle(),
                )
            }
            BasicText(
                "ContinuousBlurDrawable 与 MetaBallBlurDrawable 在此 SystemUI 构建中都直接要求 SurfaceControl。普通第三方 UID 没有等价宿主时，本 Demo 不创建假的替代实现；它们仍被列入矩阵和诊断。",
                style = TextStyle(Color(0xFFFFCC80), 11.sp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlainButton("选择背景") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PlainButton("恢复测试参数") {
                    refraction = 1f
                    dispersion = 1f
                    progressiveFraction = 1f
                    chromaticOffset = 5f
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MatrixSummary() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("实现对应关系", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
        listOf(
            "几何/SDF：Kyant 解析圆角矩形 ↔ ColorOS 锁屏软距离场 + SystemUI G2/FULL/CONIC",
            "模糊：Kyant BlurEffect ↔ COUIMaterialBlurEffect / AppBarGradientBlur / ContinuousBlurDrawable",
            "折射：Kyant lens() ↔ ColorOS keyguard GlassEffectBuilder",
            "色散：Kyant lens 7 路 ↔ keyguard RGB 三路 + SystemUI chromatic.agsl",
            "高光：Kyant Highlight ↔ keyguard Glow + SystemUI Optics + COUI SpotLight",
            "描边：Kyant Highlight stroke ↔ COUIMaterialStrokeEffect + GradientStrokeLine",
            "内阴影：Kyant InnerShadow ↔ keyguard inner shadow + SystemUI InnerShadowParams",
            "外阴影/焦散：Kyant Shadow ↔ ToolbarMaterialEffectDelegate caustic shadow（非同一算法）",
            "形变：Kyant 形状系统 ↔ ColorOS MetaBallBlurDrawable（SurfaceControl 专属）",
        ).forEach { BasicText(it, style = diagnosticsStyle()) }
    }
}

@Composable
private fun KyantLensSample(backdrop: com.kyant.backdrop.Backdrop, refraction: Float, dispersion: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
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
        BasicText("Kyant", style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun KyantBlurSample(backdrop: com.kyant.backdrop.Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = { blur(18.dp.toPx()) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant blur", style = TextStyle(Color.White, 17.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantProgressiveSample(backdrop: com.kyant.backdrop.Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
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
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
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
        BasicText("Kyant progressive", style = TextStyle(Color.White, 17.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantEdgeSample(backdrop: com.kyant.backdrop.Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
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
        BasicText("Kyant edge stack", style = TextStyle(Color.White, 17.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantInteractiveSample(backdrop: com.kyant.backdrop.Backdrop) {
    val scope = rememberCoroutineScope()
    val interactive = remember(scope) { InteractiveHighlight(scope) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
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

private enum class ColorOsMaterialSample { Blur, GradientBlur, SpotLight, ToolbarCaustic }

private class ColorOsMaterialHostView(context: Context) : View(context) {
    private val bridge = ColorOsMaterialBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
    private var sample: ColorOsMaterialSample? = null
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

    fun configure(sample: ColorOsMaterialSample, fraction: Float = 1f) {
        this.sample = sample
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
        val mode = sample ?: return
        if (width <= 0 || height <= 0) return
        val key = "$mode:$fraction:$width:$height"
        if (key == appliedKey) return
        appliedKey = key
        bridge.clear(this)
        val result = when (mode) {
            ColorOsMaterialSample.Blur -> bridge.applyBlur(this, "TYPE_FRAMEWORK_TOP_BAR_BLUR")
            ColorOsMaterialSample.GradientBlur -> bridge.applyGradientBlur(this, fraction)
            ColorOsMaterialSample.SpotLight -> bridge.applySpotLight(this, "TYPE_TRANSLUCENT_SMALL_1")
            ColorOsMaterialSample.ToolbarCaustic -> bridge.applyToolbarStack(
                view = this,
                categoryName = "TOOLBAR_BUTTON",
                blur = true,
                stroke = true,
                spotLight = true,
                caustic = true,
                forceEnable = true,
            )
        }
        result
            .onSuccess { onStatus?.invoke("PASS — $mode") }
            .onFailure {
                appliedKey = null
                onStatus?.invoke("UNAVAILABLE — $mode: ${describe(it)}")
            }
    }
}

private class ColorOsTunableGlassHostView(context: Context) : View(context) {
    private val bridge = ColorOsTunableGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var markerColor: Int? = null
    private var wallpaper: Bitmap? = null
    private var params = TunableGlassParams()
    private var radiusPx = 0f
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
                bridge.updateGeometry(this)
                    .onFailure { onStatus?.invoke("UNAVAILABLE — geometry: ${describe(it)}") }
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        markerColor = bridge.locationColor().getOrNull()
        paint.color = markerColor ?: AndroidColor.TRANSPARENT
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radiusPx)
            }
        }
    }

    fun configure(wallpaper: Bitmap, params: TunableGlassParams, radiusPx: Float) {
        val wallpaperChanged = this.wallpaper !== wallpaper
        this.wallpaper = wallpaper
        this.params = params
        this.radiusPx = radiusPx
        markerColor = markerColor ?: bridge.locationColor().getOrNull()
        paint.color = markerColor ?: AndroidColor.TRANSPARENT
        invalidateOutline()
        invalidate()
        if (wallpaperChanged) attachedWallpaperId = 0
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
        invalidateOutline()
        attachedWallpaperId = 0
        postOnAnimation { applyIfReady() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = markerColor ?: return
        paint.color = color
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
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
        result
            .onSuccess { onStatus?.invoke("PASS — ColorOS keyguard glass: $it") }
            .onFailure {
                attachedWallpaperId = 0
                onStatus?.invoke("UNAVAILABLE — ColorOS keyguard glass: ${describe(it)}")
            }
    }
}

private class ColorOsPostEffectHostView(context: Context) : View(context) {
    private val bridge = ColorOsSystemUiPostEffectBridge(context)
    private var wallpaper: Bitmap? = null
    private var radiusPx = 0f
    private var lastKey: String? = null
    private var updatePosted = false
    var onStatus: ((String) -> Unit)? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun configure(wallpaper: Bitmap, radiusPx: Float) {
        this.wallpaper = wallpaper
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
        val key = "${System.identityHashCode(bg)}:$width:$height:${loc[0]}:${loc[1]}:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            val crop = bridge.cropWallpaperForView(bg, this)
            val drawable = bridge.createPostEffectDrawable(
                bitmap = crop,
                width = width,
                height = height,
                options = ColorOsSystemUiPostEffectBridge.PostEffectOptions(
                    cornerType = "G2",
                    cornerRadiusPx = radiusPx,
                    cornerWeight = 1f,
                    optics = true,
                    gradientStroke = true,
                    innerShadow = true,
                ),
            ).getOrThrow()
            foreground = drawable
            invalidate()
        }.onSuccess {
            onStatus?.invoke("PASS — SystemUI BlendDrawable: G2 + Optics + GradientStroke + InnerShadow")
        }.onFailure {
            lastKey = null
            foreground = null
            onStatus?.invoke("UNAVAILABLE — SystemUI PostEffect: ${describe(it)}")
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
            onStatus?.invoke("UNAVAILABLE — SystemUI chromatic: ${describe(it)}")
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
    val t = ((value - range.start) / span).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        BasicText("$label ${((value * 100f).roundToInt() / 100f)}$suffix", style = TextStyle(Color.White.copy(alpha = 0.84f), 12.sp))
        AndroidView(
            factory = { SeekBar(it).apply { max = 1000 } },
            update = { seek ->
                seek.setOnSeekBarChangeListener(null)
                seek.progress = (t * seek.max).roundToInt().coerceIn(0, seek.max)
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
    androidx.compose.foundation.layout.Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .then(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            label,
            modifier = Modifier.then(
                Modifier
            ),
            style = TextStyle(Color.White, 13.sp, FontWeight.Medium),
        )
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Transparent)
                .then(
                    Modifier
                )
                .run { androidx.compose.foundation.clickable(onClick = onClick) },
        )
    }
}

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

    // High-frequency reference lines make refraction and chromatic displacement
    // visible without relying on subjective tint differences.
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
