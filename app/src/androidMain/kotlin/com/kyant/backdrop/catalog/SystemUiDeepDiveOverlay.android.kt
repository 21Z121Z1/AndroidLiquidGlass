package com.kyant.backdrop.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.SeekBar
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiDirectViewBridge
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiPresetBridge
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/**
 * Direct-execution deep dive for SystemUI implementations that are more specific than the
 * generic PostEffect sample. It intentionally uses the real SystemUI classes and shipping
 * preset objects whenever the third-party process can host them.
 */
@Composable
fun SystemUiDeepDiveOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            DeepButton("SYSUI 真实预设") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val wallpaper = remember(context) { createDeepDiveWallpaper(context) }
    val backdrop = rememberLayerBackdrop()
    val presetBridgeResult = remember(context) { runCatching { ColorOsSystemUiPresetBridge(context) } }
    val directBridgeResult = remember(context) { runCatching { ColorOsSystemUiDirectViewBridge(context) } }
    val presets = remember(presetBridgeResult) { presetBridgeResult.getOrNull()?.presets().orEmpty() }

    var presetIndex by rememberSaveable { mutableIntStateOf(0) }
    if (presets.isNotEmpty() && presetIndex !in presets.indices) presetIndex = 0
    val preset = presets.getOrNull(presetIndex)

    var presetStatus by remember { mutableStateOf("等待 SystemUI shipping preset…") }
    var progressiveStatus by remember { mutableStateOf("等待 QS ProgressiveBlurOverlay…") }
    var tiltStatus by remember { mutableStateOf("等待 Notification TiltShiftBlur…") }
    var keyguardStatus by remember { mutableStateOf("等待 Keyguard GradientBlurImageView…") }
    var multiLightStatus by remember { mutableStateOf("等待 QS MultiLight RuntimeShader…") }
    var progressive by rememberSaveable { mutableFloatStateOf(1f) }

    Box(Modifier.fillMaxSize().background(Color(0xFF07090D))) {
        Image(
            painter = BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.layerBackdrop(backdrop).fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    "SystemUI 原生配方 / 直接执行",
                    style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold),
                )
                DeepButton("关闭") { open = false }
            }

            BasicText(
                "这一页不再用自定参数代表 SystemUI。能直接运行的路径使用设备 com.android.systemui 中的真实类、RuntimeShader 或参数对象；依赖完整 SystemUI 生命周期的路径仍由全量审计页标记宿主限制。",
                style = deepInfoStyle(),
            )

            DeepTitle("1 · SystemUI shipping Optics / InnerShadow / Stroke 全预设")
            val bridgeSummary = presetBridgeResult.fold(
                onSuccess = { it.summary() },
                onFailure = { "UNAVAILABLE — ${describeDeep(it)}" },
            )
            BasicText(bridgeSummary, style = deepDiagnosticsStyle())
            if (preset == null) {
                DeepStatus("UNAVAILABLE — 没有发现可执行的 MixColorTile*Adapter getter")
            } else {
                BasicText(
                    "${presetIndex + 1}/${presets.size} · ${preset.family} · ${preset.displayName} · ${if (preset.dark) "暗色配方" else "亮色/通用配方"}",
                    style = TextStyle(Color.White, 13.sp, FontWeight.Medium),
                )
                BasicText("↔ Kyant：${preset.kyantCounterpart}", style = deepDiagnosticsStyle())

                KyantPresetReference(backdrop, preset.family)

                AndroidView(
                    factory = { SystemUiPresetHostView(it) },
                    update = { view ->
                        view.onStatus = { presetStatus = it }
                        view.configure(wallpaper, preset, with(density) { 34.dp.toPx() })
                    },
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                )
                DeepStatus(presetStatus)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeepButton("上一个") {
                        presetIndex = (presetIndex - 1 + presets.size) % presets.size
                    }
                    DeepButton("下一个") {
                        presetIndex = (presetIndex + 1) % presets.size
                    }
                    DeepButton("切换明暗同族") {
                        val stem = preset.methodName.replace("Dark", "", ignoreCase = true)
                        val candidate = presets.indexOfFirst {
                            it.family == preset.family &&
                                it.dark != preset.dark &&
                                it.methodName.replace("Dark", "", ignoreCase = true) == stem
                        }
                        if (candidate >= 0) presetIndex = candidate
                    }
                }
            }

            DeepTitle("2 · QS ProgressiveBlurOverlay")
            BasicText(
                "直接实例化 com.oplus.systemui.qs.media.ProgressiveBlurOverlay，并调用它自己的 setBlurProgress()。Kyant 侧用 blur + RuntimeShader alpha mask 对照渐进模糊机制。",
                style = deepInfoStyle(),
            )
            KyantProgressiveReference(backdrop)
            AndroidView(
                factory = { ForeignMaterialHostView(it, ForeignKind.QS_PROGRESSIVE) },
                update = { view ->
                    view.onStatus = { progressiveStatus = it }
                    view.configure(wallpaper, progressive)
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            DeepStatus(progressiveStatus)
            FloatSlider("Progress", progressive, 0f, 1f) { progressive = it }

            DeepTitle("3 · Notification TiltShiftBlurContainer")
            BasicText(
                "直接构造 OplusNotificationTiltShiftBlurContainer，再调用 updateTiltShiftBlurtSize() + setMaterialBlur()。如果固件把它和 MaterialBlurStateManager 强绑定，页面会明确显示 UNAVAILABLE，而不会改用仿制 shader。",
                style = deepInfoStyle(),
            )
            KyantProgressiveReference(backdrop)
            AndroidView(
                factory = { ForeignMaterialHostView(it, ForeignKind.NOTIFICATION_TILT_SHIFT) },
                update = { view ->
                    view.onStatus = { tiltStatus = it }
                    view.configure(wallpaper, progressive)
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            DeepStatus(tiltStatus)

            DeepTitle("4 · Keyguard GradientBlurImageView")
            BasicText(
                "这条锁屏渐变遮罩不是 personality-clocks 的折射玻璃。这里单独执行 GradientBlurImageView.showBlurMask()，与 Kyant 的渐进模糊/遮罩机制对照，避免把它误并入折射。",
                style = deepInfoStyle(),
            )
            KyantProgressiveReference(backdrop)
            AndroidView(
                factory = { ForeignMaterialHostView(it, ForeignKind.KEYGUARD_GRADIENT) },
                update = { view ->
                    view.onStatus = { keyguardStatus = it }
                    view.configure(wallpaper, progressive)
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            DeepStatus(keyguardStatus)

            DeepTitle("5 · QS MultiLight RuntimeShader")
            BasicText(
                "MultiLightShaderParams 直接返回 SystemUI RuntimeShader。它是屏幕空间多光源/边缘/阴影材质层，不做背景折射；Kyant 最接近的是 Highlight + InnerShadow 组合。",
                style = deepInfoStyle(),
            )
            KyantEdgeReference(backdrop)
            AndroidView(
                factory = { MultiLightHostView(it) },
                update = { view ->
                    view.onStatus = { multiLightStatus = it }
                    view.configure(with(density) { 34.dp.toPx() })
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            DeepStatus(multiLightStatus)

            directBridgeResult.exceptionOrNull()?.let {
                BasicText("Direct bridge init: ${describeDeep(it)}", style = TextStyle(Color(0xFFFFB4A9), 9.sp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KyantPresetReference(backdrop: Backdrop, family: ColorOsSystemUiPresetBridge.Family) {
    when (family) {
        ColorOsSystemUiPresetBridge.Family.OPTICS,
        ColorOsSystemUiPresetBridge.Family.STROKE -> KyantEdgeReference(backdrop)
        ColorOsSystemUiPresetBridge.Family.INNER_SHADOW -> Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(34.dp) },
                    effects = { blur(7.dp.toPx()) },
                    highlight = null,
                    shadow = null,
                    innerShadow = { InnerShadow.Default },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Kyant InnerShadow", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
        }
    }
}

@Composable
private fun KyantEdgeReference(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(34.dp) },
                effects = { blur(7.dp.toPx()) },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant edge / highlight reference", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
    }
}

@Composable
private fun KyantProgressiveReference(backdrop: Backdrop) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { androidx.compose.ui.graphics.RectangleShape },
                effects = {
                    blur(6.dp.toPx())
                    runtimeShaderEffect(
                        "SystemUiDeepDiveProgressiveMask",
                        """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float alpha = smoothstep(size.y, size.y * 0.42, coord.y);
    return content.eval(coord) * alpha;
}
""",
                        "content",
                    ) {
                        setFloatUniform("size", size.width, size.height)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Kyant progressive reference", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
    }
}

private class SystemUiPresetHostView(context: Context) : View(context) {
    private val bridgeResult = runCatching { ColorOsSystemUiPresetBridge(context) }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
    private var wallpaper: Bitmap? = null
    private var preset: ColorOsSystemUiPresetBridge.Preset? = null
    private var radiusPx = 0f
    private var lastKey: String? = null
    private var posted = false
    var onStatus: ((String) -> Unit)? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleApply() }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radiusPx)
            }
        }
    }

    fun configure(wallpaper: Bitmap, preset: ColorOsSystemUiPresetBridge.Preset, radiusPx: Float) {
        this.wallpaper = wallpaper
        this.preset = preset
        this.radiusPx = radiusPx
        invalidateOutline()
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        scheduleApply()
    }

    override fun onDetachedFromWindow() {
        foreground = null
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        invalidateOutline()
        scheduleApply()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, basePaint)
    }

    private fun scheduleApply() {
        if (posted) return
        posted = true
        postOnAnimation {
            posted = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        val p = preset ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val key = "${System.identityHashCode(bg)}:${p.id}:$width:$height:${loc[0]}:${loc[1]}:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            val bridge = bridgeResult.getOrThrow()
            val crop = cropBitmapForView(bg, this)
            foreground = bridge.createPresetDrawable(crop, width, height, radiusPx, p).getOrThrow()
            invalidate()
        }.onSuccess {
            onStatus?.invoke("PASS — shipping ${p.family}: ${p.methodName}")
        }.onFailure {
            foreground = null
            lastKey = null
            onStatus?.invoke("UNAVAILABLE — shipping preset: ${describeDeep(it)}")
        }
    }
}

private enum class ForeignKind { QS_PROGRESSIVE, NOTIFICATION_TILT_SHIFT, KEYGUARD_GRADIENT }

private class ForeignMaterialHostView(context: Context, private val kind: ForeignKind) : FrameLayout(context) {
    private val bridgeResult = runCatching { ColorOsSystemUiDirectViewBridge(context) }
    private var child: View? = null
    private var wallpaper: Bitmap? = null
    private var progress = 1f
    private var appliedWallpaper = 0
    var onStatus: ((String) -> Unit)? = null

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width.coerceAtLeast(1),
                    view.height.coerceAtLeast(1),
                    30f * resources.displayMetrics.density,
                )
            }
        }
    }

    fun configure(wallpaper: Bitmap, progress: Float) {
        this.wallpaper = wallpaper
        this.progress = progress.coerceIn(0f, 1f)
        post { applyIfReady() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        post { applyIfReady() }
    }

    override fun onDetachedFromWindow() {
        if (kind == ForeignKind.NOTIFICATION_TILT_SHIFT) {
            child?.let { bridgeResult.getOrNull()?.resetNotificationTiltShift(it) }
        }
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        if (width <= 0 || height <= 0) return
        val bridge = bridgeResult.getOrElse {
            onStatus?.invoke("UNAVAILABLE — direct bridge: ${describeDeep(it)}")
            return
        }
        val bg = wallpaper ?: return
        runCatching {
            when (kind) {
                ForeignKind.QS_PROGRESSIVE -> {
                    val target = child ?: bridge.createQsProgressiveBlur(progress).getOrThrow().also(::install)
                    bridge.updateQsProgressiveBlur(target, progress).getOrThrow()
                }
                ForeignKind.NOTIFICATION_TILT_SHIFT -> {
                    val target = child ?: bridge.createNotificationTiltShift(width, height).getOrThrow().also(::install)
                    bridge.updateNotificationTiltShift(target, width, height).getOrThrow()
                }
                ForeignKind.KEYGUARD_GRADIENT -> {
                    val identity = System.identityHashCode(bg)
                    val target = child ?: bridge.createKeyguardGradientBlur(bg, progress).getOrThrow().also(::install)
                    if (identity != appliedWallpaper || child != null) {
                        bridge.updateKeyguardGradientBlur(target, bg, progress).getOrThrow()
                        appliedWallpaper = identity
                    }
                }
            }
        }.onSuccess {
            onStatus?.invoke("PASS — $kind / real SystemUI view")
        }.onFailure {
            onStatus?.invoke("UNAVAILABLE — $kind: ${describeDeep(it)}")
        }
    }

    private fun install(view: View) {
        child = view
        removeAllViews()
        addView(
            view,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }
}

private class MultiLightHostView(context: Context) : View(context) {
    private val bridgeResult = runCatching { ColorOsSystemUiDirectViewBridge(context) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: RuntimeShader? = null
    private var radiusPx = 0f
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
        paint.color = 0x18FFFFFF
        paint.shader = null
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
        shader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
    }

    private fun applyIfReady() {
        if (width <= 0 || height <= 0) return
        val key = "$width:$height:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        runCatching {
            bridgeResult.getOrThrow().createQsMultiLightShader(width, height, radiusPx).getOrThrow()
        }.onSuccess {
            shader = it
            invalidate()
            onStatus?.invoke("PASS — QS MultiLightShaderParams.getRuntimeShader()")
        }.onFailure {
            shader = null
            lastKey = null
            onStatus?.invoke("UNAVAILABLE — QS MultiLight: ${describeDeep(it)}")
        }
    }
}

@Composable
private fun FloatSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val span = (max - min).coerceAtLeast(0.0001f)
    Column(Modifier.fillMaxWidth()) {
        BasicText("$label · ${((value * 100f).toInt() / 100f)}", style = TextStyle(Color.White.copy(alpha = 0.82f), 11.sp))
        AndroidView(
            factory = { SeekBar(it).apply { this.max = 1000 } },
            update = { seek ->
                seek.setOnSeekBarChangeListener(null)
                seek.progress = (((value - min) / span) * 1000f).toInt().coerceIn(0, 1000)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onChange(min + span * progress / 1000f)
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
private fun DeepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun DeepTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    BasicText(text, style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
}

@Composable
private fun DeepStatus(text: String) {
    val color = when {
        text.startsWith("PASS") -> Color(0xFF8EE6A2)
        text.startsWith("UNAVAILABLE") -> Color(0xFFFFCC80)
        else -> Color.White.copy(alpha = 0.70f)
    }
    BasicText(text, style = TextStyle(color, 10.sp))
}

private fun deepInfoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun deepDiagnosticsStyle() = TextStyle(Color.White.copy(alpha = 0.68f), 9.sp)

private fun cropBitmapForView(bitmap: Bitmap, view: View): Bitmap {
    val outW = view.width.coerceAtLeast(1)
    val outH = view.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    val left = loc[0].coerceAtLeast(0)
    val top = loc[1].coerceAtLeast(0)
    val right = (loc[0] + outW).coerceAtMost(bitmap.width)
    val bottom = (loc[1] + outH).coerceAtMost(bitmap.height)
    if (right > left && bottom > top) {
        Canvas(out).drawBitmap(
            bitmap,
            Rect(left, top, right, bottom),
            Rect((left - loc[0]).coerceAtLeast(0), (top - loc[1]).coerceAtLeast(0),
                (left - loc[0]).coerceAtLeast(0) + (right - left),
                (top - loc[1]).coerceAtLeast(0) + (bottom - top)),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
    return out
}

private fun createDeepDiveWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(720)
    val h = dm.heightPixels.coerceAtLeast(1280)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(
            AndroidColor.rgb(14, 27, 67),
            AndroidColor.rgb(148, 57, 176),
            AndroidColor.rgb(13, 174, 198),
            AndroidColor.rgb(251, 176, 48),
        ),
        floatArrayOf(0f, 0.34f, 0.70f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(w * 0.20f, h * 0.24f, w * 0.13f, paint)
    paint.color = AndroidColor.rgb(255, 70, 112)
    canvas.drawCircle(w * 0.78f, h * 0.46f, w * 0.18f, paint)
    paint.color = AndroidColor.rgb(48, 238, 198)
    canvas.drawCircle(w * 0.39f, h * 0.77f, w * 0.16f, paint)
    paint.color = 0xAAFFFFFF.toInt()
    paint.strokeWidth = 2f
    val step = (w / 18f).coerceAtLeast(24f)
    var x = 0f
    while (x < w) {
        canvas.drawLine(x, 0f, x, h.toFloat(), paint)
        x += step
    }
    return bitmap
}

private fun describeDeep(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}
