package com.kyant.backdrop.catalog

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
import android.widget.Switch
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
import androidx.compose.foundation.layout.size
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
import com.kyant.backdrop.catalog.coloros.ColorOsTunableGlassBridge
import com.kyant.backdrop.catalog.coloros.TunableColor4
import com.kyant.backdrop.catalog.coloros.TunableGlassParams
import kotlin.math.roundToInt

@Composable
actual fun TunableGlassPlaygroundContent(
    destination: CatalogDestination,
): Boolean {
    if (destination != CatalogDestination.GlassPlayground) return false
    ColorOsTunableGlassPlayground()
    return true
}

@Composable
private fun ColorOsTunableGlassPlayground() {
    val context = LocalContext.current
    var wallpaper by remember(context) { mutableStateOf(createTuningWallpaper(context)) }
    var params by remember { mutableStateOf(TunableGlassParams()) }
    var cornerRadius by remember { mutableFloatStateOf(42f) }
    var status by remember { mutableStateOf("Waiting for ColorOS tunable shader…") }
    var imageError by remember { mutableStateOf<String?>(null) }
    var showColorRecipes by remember { mutableStateOf(false) }
    var showAuthoringNotes by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Bitmap decode returned null")
            }.onSuccess {
                wallpaper = normalizeTuningWallpaper(context, it)
                imageError = null
            }.onFailure {
                imageError = "Image load failed: ${it.javaClass.simpleName}: ${it.message}"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TuningText("ColorOS Liquid Glass · 实时调试", size = 22, weight = FontWeight.SemiBold)
            TuningText(
                "从设备已安装的 personality-clocks APK 动态提取当前固件 AGSL；仓库不内置 OPPO shader。拖动普通参数会直接更新 RuntimeShader，只有模糊半径变化会重建多输入 RenderEffect。",
                color = Color.White.copy(alpha = 0.78f),
                size = 12,
            )

            val density = LocalDensity.current
            val radiusPx = with(density) { cornerRadius.dp.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { TunableGlassPreviewView(it) },
                    update = { view ->
                        view.onStatus = { status = it }
                        view.configure(wallpaper, params, radiusPx)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                TuningText("ColorOS", size = 20, weight = FontWeight.SemiBold)
            }

            TuningText(
                status,
                color = if (status.startsWith("PASS")) Color(0xFF8EE6A2) else Color(0xFFFFCC80),
                size = 11,
            )
            imageError?.let { TuningText(it, color = Color(0xFFFF8A80), size = 11) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuningButton("恢复系统默认") {
                    params = TunableGlassParams()
                    cornerRadius = 42f
                }
                TuningButton("选择背景") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ParameterSection("全局 / 调试")
                ParamSlider("玻璃混合 u_RefractMix", params.glassMix, 0f..1f) { params = params.copy(glassMix = it) }
                ParamSlider("整体透明度 u_Alpha", params.alpha, 0f..1f) { params = params.copy(alpha = it) }
                ParamSlider("状态混合 u_StateMix", params.stateMix, 0f..1f) { params = params.copy(stateMix = it) }
                ParamSlider("颜色遮罩进度", params.maskColorProgress, 0f..1f) { params = params.copy(maskColorProgress = it) }
                ParamSlider("动画混合 u_AnimMix", params.animMix, 0f..1f) { params = params.copy(animMix = it) }
                ParamSlider("冒号状态 u_HasColonMix", params.hasColonMix, 0f..1f) { params = params.copy(hasColonMix = it) }
                ParamSlider("预览圆角（Demo 几何）", cornerRadius, 0f..105f, suffix = " dp") { cornerRadius = it }
                BoolControl("方向边缘光开关", params.effectLightEnabled) { params = params.copy(effectLightEnabled = it) }
                BoolControl("显示软距离场 u_ShowSDF", params.showSdf) { params = params.copy(showSdf = it) }
                BoolControl("仅显示原始 View u_OnlyViewContext", params.onlyViewContext) { params = params.copy(onlyViewContext = it) }

                ParameterSection("软距离场 / 模糊")
                TuningText(
                    "这里是量产 GlassEffectBuilder 实际使用的 10 px 时钟蒙版模糊，用来生成 u_BlurClockTex/软距离场；它不是壁纸背景模糊。拖动时只重建模糊节点和 ColorOS 多输入 RenderEffect。",
                    color = Color.White.copy(alpha = 0.68f),
                    size = 11,
                )
                ParamSlider("SDF 模糊半径 X", params.blurRadiusX, 0f..40f, suffix = " px") { params = params.copy(blurRadiusX = it) }
                ParamSlider("SDF 模糊半径 Y", params.blurRadiusY, 0f..40f, suffix = " px") { params = params.copy(blurRadiusY = it) }

                ParameterSection("折射 / 色散")
                TuningText(
                    "折射范围不是像素半径，而是软距离场中围绕 0.5 等值线的边缘带宽；实际位移幅度还会乘屏幕尺寸、梯度和边缘权重。",
                    color = Color.White.copy(alpha = 0.68f),
                    size = 11,
                )
                ParamSlider("折射强度比例", params.refractionIntensityScale, 0f..1.25f) { params = params.copy(refractionIntensityScale = it) }
                ParamSlider("折射范围 X（边缘带）", params.refractionRangeX, 0.01f..1.5f) { params = params.copy(refractionRangeX = it) }
                ParamSlider("折射范围 Y（边缘带）", params.refractionRangeY, 0.01f..1.5f) { params = params.copy(refractionRangeY = it) }
                ParamSlider("RGB 色散强度比例", params.dispersionIntensityScale, 0f..0.60f) { params = params.copy(dispersionIntensityScale = it) }
                ParamSlider("梯度钳位", params.maxGradient, 0.005f..0.10f, decimals = 3) { params = params.copy(maxGradient = it) }

                ParameterSection("边缘方向光")
                ParamSlider("Glow Power", params.glowPower, 0.01f..8f) { params = params.copy(glowPower = it) }
                ParamSlider("方向缩放 X", params.glowDirScaleX, 0.1f..4f) { params = params.copy(glowDirScaleX = it) }
                ParamSlider("方向缩放 Y", params.glowDirScaleY, 0.1f..4f) { params = params.copy(glowDirScaleY = it) }
                ParamSlider("Glow Offset X", params.glowOffsetX, -20f..20f, suffix = " px") { params = params.copy(glowOffsetX = it) }
                ParamSlider("Glow Offset Y", params.glowOffsetY, -20f..20f, suffix = " px") { params = params.copy(glowOffsetY = it) }
                ParamSlider("Glow Exposure", params.glowExposure, -1f..2f) { params = params.copy(glowExposure = it) }
                ParamSlider("光线方向 X", params.glowLightDirX, -8f..8f) { params = params.copy(glowLightDirX = it) }
                ParamSlider("光线方向 Y", params.glowLightDirY, -8f..8f) { params = params.copy(glowLightDirY = it) }
                ParamSlider("光线聚焦", params.glowLightFocus, 0.1f..16f) { params = params.copy(glowLightFocus = it) }
                ParamSlider("Glow 强度 Mode 0", params.glowIntensityMode0, 0f..2f) { params = params.copy(glowIntensityMode0 = it) }
                ParamSlider("Glow 强度 Mode 1", params.glowIntensityMode1, 0f..2f) { params = params.copy(glowIntensityMode1 = it) }
                ColorEditor("Glow Color", params.glowColor) { params = params.copy(glowColor = it) }

                ParameterSection("描边")
                ParamSlider("Stroke Power", params.strokePower, 0.01f..10f) { params = params.copy(strokePower = it) }
                ParamSlider("方向缩放 X", params.strokeDirScaleX, 0.1f..4f) { params = params.copy(strokeDirScaleX = it) }
                ParamSlider("方向缩放 Y", params.strokeDirScaleY, 0.1f..4f) { params = params.copy(strokeDirScaleY = it) }
                ParamSlider("Stroke Exposure", params.strokeExposure, -1f..2f) { params = params.copy(strokeExposure = it) }
                ParamSlider("Stroke Intensity", params.strokeIntensity, 0f..2f) { params = params.copy(strokeIntensity = it) }

                ParameterSection("内阴影")
                ParamSlider("Shadow Offset X", params.shadowOffsetX, -2f..2f) { params = params.copy(shadowOffsetX = it) }
                ParamSlider("Shadow Offset Y", params.shadowOffsetY, -2f..2f) { params = params.copy(shadowOffsetY = it) }
                ParamSlider("Shadow Distance", params.shadowDistance, -0.5f..2f) { params = params.copy(shadowDistance = it) }
                ParamSlider("Shadow Softness", params.shadowSoftness, 0.001f..2f, decimals = 3) { params = params.copy(shadowSoftness = it) }
                ColorEditor("Inner Shadow Color", params.innerShadowColor) { params = params.copy(innerShadowColor = it) }

                ParameterSection("噪点")
                ParamSlider("Noise Density", params.noiseDensity, 0f..1f) { params = params.copy(noiseDensity = it) }
                ParamSlider("Noise Scale", params.noiseScale, 0f..4f) { params = params.copy(noiseScale = it) }

                ParameterSection("材质混色配方（高级）")
                BoolControl("展开所有系统配色常量", showColorRecipes) { showColorRecipes = it }
                if (showColorRecipes) {
                    TuningText(
                        "这些值在当前优化版 AGSL 中原本是 const；调试页只在内存中把它们改成 uniform。",
                        color = Color.White.copy(alpha = 0.68f),
                        size = 11,
                    )
                    ColorEditor("ONE_PLUS_TOP", params.onePlusTop) { params = params.copy(onePlusTop = it) }
                    ColorEditor("ONE_PLUS_MIDDLE", params.onePlusMiddle) { params = params.copy(onePlusMiddle = it) }
                    ColorEditor("ONE_PLUS_BOT", params.onePlusBottom) { params = params.copy(onePlusBottom = it) }
                    ColorEditor("NO_COLON_LIGHT_TOP", params.noColonLightTop) { params = params.copy(noColonLightTop = it) }
                    ColorEditor("NO_COLON_LIGHT_BOT", params.noColonLightBottom) { params = params.copy(noColonLightBottom = it) }
                    ColorEditor("NO_COLON_LIGHT_BOT_ALT", params.noColonLightBottomAlt) { params = params.copy(noColonLightBottomAlt = it) }
                    ColorEditor("NO_COLON_DARK_TOP", params.noColonDarkTop) { params = params.copy(noColonDarkTop = it) }
                    ColorEditor("NO_COLON_DARK_MIDDLE", params.noColonDarkMiddle) { params = params.copy(noColonDarkMiddle = it) }
                    ColorEditor("NO_COLON_DARK_MIDDLE_ALT", params.noColonDarkMiddleAlt) { params = params.copy(noColonDarkMiddleAlt = it) }
                    ColorEditor("NO_COLON_DARK_BOT", params.noColonDarkBottom) { params = params.copy(noColonDarkBottom = it) }
                    ColorEditor("HAS_COLON_LIGHT_TOP", params.hasColonLightTop) { params = params.copy(hasColonLightTop = it) }
                    ColorEditor("HAS_COLON_LIGHT_BOT", params.hasColonLightBottom) { params = params.copy(hasColonLightBottom = it) }
                    ColorEditor("HAS_COLON_DARK_TOP", params.hasColonDarkTop) { params = params.copy(hasColonDarkTop = it) }
                    ColorEditor("HAS_COLON_DARK_MIDDLE", params.hasColonDarkMiddle) { params = params.copy(hasColonDarkMiddle = it) }
                    ColorEditor("HAS_COLON_DARK_BOT", params.hasColonDarkBottom) { params = params.copy(hasColonDarkBottom = it) }
                }

                ParameterSection("作者工程参数说明")
                BoolControl("显示 clock.coz 中已被量产优化版移除的参数", showAuthoringNotes) { showAuthoringNotes = it }
                if (showAuthoringNotes) {
                    TuningText(
                        "原始 clock.coz 作者工程还出现 u_RefractOffset、u_StrokeOffset、u_GlowSaturation、u_GlowContrast、BoxBlur kernel/radius 等控制量；当前 classes2.dex 的量产 AGSL 已把这些接口优化掉或改写成另一套数学。这里不把它们伪装成量产运行时参数。需要的话应单独增加“作者工程模式”，而不是修改当前固件模式的语义。",
                        color = Color(0xFFFFCC80),
                        size = 11,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TuningText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Int = 12,
    weight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight),
    )
}

@Composable
private fun TuningButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        TuningText(label, size = 13, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun ParameterSection(title: String) {
    Spacer(Modifier.height(6.dp))
    TuningText(title, size = 17, weight = FontWeight.SemiBold)
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 2,
    suffix: String = "",
    onValueChange: (Float) -> Unit,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - range.start) / span).coerceIn(0f, 1f)
    val factor = when (decimals) {
        0 -> 1f
        1 -> 10f
        2 -> 100f
        else -> 1000f
    }
    val rounded = (value * factor).roundToInt() / factor

    Column(Modifier.fillMaxWidth()) {
        TuningText("$label  $rounded$suffix", color = Color.White.copy(alpha = 0.88f), size = 12)
        AndroidView(
            factory = { SeekBar(it).apply { max = 1000 } },
            update = { seek ->
                seek.setOnSeekBarChangeListener(null)
                seek.progress = (normalized * seek.max).roundToInt().coerceIn(0, seek.max)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val t = progress / 1000f
                        onValueChange(range.start + span * t)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            modifier = Modifier.fillMaxWidth().height(38.dp),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun BoolControl(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TuningText(label, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.88f), size = 12)
        AndroidView(
            factory = { Switch(it) },
            update = { toggle ->
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = checked
                toggle.setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
            },
            modifier = Modifier.height(44.dp),
        )
    }
}

@Composable
private fun ColorEditor(label: String, color: TunableColor4, onChange: (TunableColor4) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Color(
                            color.r.coerceIn(0f, 1f),
                            color.g.coerceIn(0f, 1f),
                            color.b.coerceIn(0f, 1f),
                            color.a.coerceIn(0f, 1f),
                        ),
                    ),
            )
            TuningText(label, size = 12, weight = FontWeight.Medium)
        }
        ParamSlider("R", color.r, 0f..1f) { onChange(color.copy(r = it)) }
        ParamSlider("G", color.g, 0f..1f) { onChange(color.copy(g = it)) }
        ParamSlider("B", color.b, 0f..1f) { onChange(color.copy(b = it)) }
        ParamSlider("A", color.a, 0f..1f) { onChange(color.copy(a = it)) }
    }
}

private class TunableGlassPreviewView(context: Context) : View(context) {
    private val bridge = ColorOsTunableGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var markerColor: Int? = null
    private var wallpaper: Bitmap? = null
    private var params = TunableGlassParams()
    private var radiusPx = 0f
    private var attachedWallpaperId = 0
    private var attachedWidth = 0
    private var attachedHeight = 0
    private var scrollUpdatePosted = false
    private var parameterUpdatePosted = false

    var onStatus: ((String) -> Unit)? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (!scrollUpdatePosted) {
            scrollUpdatePosted = true
            postOnAnimation {
                scrollUpdatePosted = false
                bridge.updateGeometry(this)
                    .onFailure { onStatus?.invoke("UNAVAILABLE — geometry: ${describe(it)}") }
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        markerColor = bridge.locationColor().getOrNull()
        paint.color = markerColor ?: AndroidColor.TRANSPARENT
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radiusPx)
            }
        }
        clipToOutline = true
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
        scheduleParameterUpdate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        attachedWallpaperId = 0
        scheduleParameterUpdate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        if (w != oldw || h != oldh) attachedWallpaperId = 0
        scheduleParameterUpdate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = markerColor ?: return
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun scheduleParameterUpdate() {
        if (parameterUpdatePosted) return
        parameterUpdatePosted = true
        postOnAnimation {
            parameterUpdatePosted = false
            applyIfReady()
        }
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
            bridge.update(this, params)
        }
        result
            .onSuccess { onStatus?.invoke("PASS — ${if (needsAttach) it.toString() else "live uniforms updated"}") }
            .onFailure {
                attachedWallpaperId = 0
                onStatus?.invoke("UNAVAILABLE — ${describe(it)}")
            }
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}

private fun createTuningWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(720)
    val h = dm.heightPixels.coerceAtLeast(1280)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        w.toFloat(),
        h.toFloat(),
        intArrayOf(0xFF17112E.toInt(), 0xFF6459C7.toInt(), 0xFFFF7347.toInt(), 0xFF16275A.toInt()),
        floatArrayOf(0f, 0.38f, 0.68f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = 0xFFFFB126.toInt()
    canvas.drawCircle(w * 0.77f, h * 0.44f, w * 0.32f, paint)
    paint.color = 0xFF5F4CFF.toInt()
    canvas.drawCircle(w * 0.20f, h * 0.64f, w * 0.27f, paint)
    return bitmap
}

private fun normalizeTuningWallpaper(context: Context, source: Bitmap): Bitmap {
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
        Paint(Paint.ANTI_ALIAS_FLAG),
    )
    return out
}
