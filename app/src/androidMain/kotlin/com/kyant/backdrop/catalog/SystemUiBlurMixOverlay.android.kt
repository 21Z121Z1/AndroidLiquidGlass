package com.kyant.backdrop.catalog

import android.content.Context
import android.graphics.Bitmap
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiAuditScope
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiBlurMixBridge
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.RoundedRectangle

/**
 * Executes real QS + notification BlurMixConfig recipes and compares them with the nearest Kyant
 * mechanism. No color/mode constants are duplicated from SystemUI.
 */
@Composable
fun SystemUiBlurMixOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MixButton("SYSUI Blur/Mix") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val wallpaper = remember(context) { createMixWallpaper(context) }
    val backdrop = rememberLayerBackdrop()
    val bridgeResult = remember(context) { runCatching { ColorOsSystemUiBlurMixBridge(context) } }
    val recipes = remember(bridgeResult) { bridgeResult.getOrNull()?.recipes().orEmpty() }
    val catalog = remember(context) { ColorOsSystemUiLiquidGlassCatalog(context) }
    val mappings = remember(catalog) { catalog.mappings() }
    val scopedSummary = remember(mappings) { ColorOsSystemUiAuditScope.summary(mappings) }

    var index by rememberSaveable { mutableIntStateOf(0) }
    if (recipes.isNotEmpty() && index !in recipes.indices) index = 0
    var amount by rememberSaveable { mutableFloatStateOf(1f) }
    var nativeStatus by remember { mutableStateOf("等待真实 BlurMixConfig…") }
    var nativeDetails by remember { mutableStateOf("") }

    val recipe = recipes.getOrNull(index)
    val qsRadiusText = remember(bridgeResult) {
        bridgeResult.getOrNull()?.defaultQsBlurRadius()?.fold(
            onSuccess = { "QS default blur radius=$it px" },
            onFailure = { "QS default blur radius unavailable: ${mixDescribe(it)}" },
        ) ?: "QS blur bridge unavailable"
    }
    val resolvedColors = remember(bridgeResult) {
        bridgeResult.getOrNull()?.notificationResolvedColors()?.getOrDefault(emptyList()).orEmpty()
    }

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
                .padding(bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    "SystemUI shipping Blur / Mix",
                    style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold),
                )
                MixButton("关闭") { open = false }
            }

            BasicText(
                "执行链：SystemUI 原生 BlurMixConfig 工厂 → ShaderBlendParamHelper → BlendDrawable.setShaderBlendParams。没有 shader 参数的 motion/platform 配方只标 HOST_ONLY，不用 Kyant 或自制 shader 冒充。",
                style = mixInfoStyle(),
            )

            ScopeSummaryCard(scopedSummary)

            MixTitle("1 · QSBlurConfigProvider + ShaderBlendParamHelper")
            BasicText(
                "$qsRadiusText。active/inactive/dialog/seekbar/SEP/STD 配方全部由当前固件静态工厂即时生成；amount 会直接传给 SystemUI helper 更新 shader 参数。",
                style = mixInfoStyle(),
            )

            MixTitle("2 · NotificationBlurMixColorParams → NotificationPlatFormBlurParamsManager")
            BasicText(
                "normal card、锁屏第一/第二堆叠、close-all 直接调用 NotificationPlatFormBlurParamsManager。它内部消费 NotificationBlurMixColorParams，所以这里不复制任何通知颜色常量。另有 ${resolvedColors.size} 个 resolve*() 结果可从当前固件实时读取。",
                style = mixInfoStyle(),
            )
            if (resolvedColors.isNotEmpty()) {
                BasicText(
                    resolvedColors.take(6).joinToString(" · ") + if (resolvedColors.size > 6) " · …" else "",
                    style = mixDiagnosticsStyle(),
                )
            }

            MixTitle("3 · 当前 shipping 配方 A/B")
            if (recipe == null) {
                MixStatus("UNAVAILABLE — 未发现 QS/Notification blur-mix 配方")
            } else {
                BasicText(
                    "${index + 1}/${recipes.size} · ${recipe.source} · ${recipe.label}",
                    style = TextStyle(Color.White, 13.sp, FontWeight.Medium),
                )
                BasicText("↔ Kyant：${recipe.kyantCounterpart}", style = mixDiagnosticsStyle())

                BasicText("Kyant reference", style = mixLabelStyle())
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(34.dp) },
                            effects = {
                                blur(18.dp.toPx())
                                vibrancy()
                            },
                            highlight = { Highlight.Default },
                            shadow = null,
                            innerShadow = { InnerShadow.Default },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.055f)) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Kyant blur + vibrancy", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                }

                BasicText("ColorOS SystemUI shipping mix", style = mixLabelStyle())
                AndroidView(
                    factory = { BlurMixHostView(it) },
                    update = { view ->
                        view.onStatus = { status, details ->
                            nativeStatus = status
                            nativeDetails = details
                        }
                        view.configure(
                            wallpaper = wallpaper,
                            recipe = recipe,
                            amount = amount,
                            radiusPx = with(density) { 34.dp.toPx() },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(136.dp),
                )
                MixStatus(nativeStatus)
                if (nativeDetails.isNotBlank()) {
                    BasicText(nativeDetails, style = mixDiagnosticsStyle())
                }

                MixFloatSlider("SystemUI blur/mix amount", amount, 0f, 1f) { amount = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixButton("上一个") { index = (index - 1 + recipes.size) % recipes.size }
                    MixButton("下一个") { index = (index + 1) % recipes.size }
                    MixButton("跳到通知") {
                        val next = recipes.indexOfFirst { it.source == ColorOsSystemUiBlurMixBridge.Source.NOTIFICATION }
                        if (next >= 0) index = next
                    }
                }
            }

            MixTitle("4 · 审计口径")
            BasicText(
                "核心材质覆盖率只计算 ColorOS PostEffect、blurability、通知/QS/音量材质、壁纸模糊、生物识别材质、平台模糊和已确认 shipping shader。普通 AOSP ripple、泛用 shadow/gradient/shader 保留在高召回扫描中，但归入相邻图形效果，不再影响 Liquid Glass 完成度。",
                style = mixInfoStyle(),
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ScopeSummaryCard(summary: ColorOsSystemUiAuditScope.ScopedSummary) {
    val pass = summary.coreComplete
    val color = if (pass) Color(0xFF8EE6A2) else Color(0xFFFF8A80)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            if (pass) "CORE PASS · ${summary.coreCoveragePercent}%" else "CORE FAIL · ${summary.coreUnmapped} 未映射",
            style = TextStyle(color, 15.sp, FontWeight.SemiBold),
        )
        BasicText(
            "扫描总计 ${summary.total} · 核心材质 ${summary.core} · 相邻图形 ${summary.adjacent}",
            style = mixDiagnosticsStyle(),
        )
        BasicText(
            "核心 mapped ${summary.coreMapped} · available ${summary.coreAvailable} · direct ${summary.coreDirect} · host-bound ${summary.coreHostBound}",
            style = mixDiagnosticsStyle(),
        )
    }
}

private class BlurMixHostView(context: Context) : View(context) {
    private val bridgeResult = runCatching { ColorOsSystemUiBlurMixBridge(context) }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x16FFFFFF }
    private var wallpaper: Bitmap? = null
    private var recipe: ColorOsSystemUiBlurMixBridge.Recipe? = null
    private var amount = 1f
    private var radiusPx = 0f
    private var lastKey: String? = null
    private var scheduled = false
    var onStatus: ((String, String) -> Unit)? = null

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

    fun configure(
        wallpaper: Bitmap,
        recipe: ColorOsSystemUiBlurMixBridge.Recipe,
        amount: Float,
        radiusPx: Float,
    ) {
        this.wallpaper = wallpaper
        this.recipe = recipe
        this.amount = amount.coerceIn(0f, 1f)
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
        if (scheduled) return
        scheduled = true
        postOnAnimation {
            scheduled = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val bg = wallpaper ?: return
        val current = recipe ?: return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = "${System.identityHashCode(bg)}:${current.id}:$amount:$width:$height:${location[0]}:${location[1]}:$radiusPx"
        if (key == lastKey) return
        lastKey = key

        val bridge = bridgeResult.getOrElse {
            lastKey = null
            onStatus?.invoke("UNAVAILABLE — blur/mix bridge", mixDescribe(it))
            return
        }

        bridge.evaluate(current, amount).fold(
            onSuccess = { evaluation ->
                if (evaluation.execution == ColorOsSystemUiBlurMixBridge.Execution.HOST_ONLY) {
                    foreground = null
                    onStatus?.invoke(
                        "HOST_ONLY — ${evaluation.configClass.substringAfterLast('.')}",
                        evaluation.details,
                    )
                    return
                }

                runCatching {
                    val crop = cropMixWallpaper(bg, this)
                    foreground = bridge.createDrawable(
                        bitmap = crop,
                        width = width,
                        height = height,
                        cornerRadiusPx = radiusPx,
                        recipe = current,
                        amount = amount,
                    ).getOrThrow()
                    invalidate()
                }.onSuccess {
                    onStatus?.invoke(
                        "PASS — ${evaluation.configClass.substringAfterLast('.')} → ShaderBlendParamHelper → BlendDrawable",
                        evaluation.details,
                    )
                }.onFailure {
                    foreground = null
                    lastKey = null
                    onStatus?.invoke("UNAVAILABLE — direct shader mix", mixDescribe(it))
                }
            },
            onFailure = {
                foreground = null
                lastKey = null
                onStatus?.invoke("UNAVAILABLE — recipe factory", mixDescribe(it))
            },
        )
    }
}

private fun cropMixWallpaper(bitmap: Bitmap, view: View): Bitmap {
    val outW = view.width.coerceAtLeast(1)
    val outH = view.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    val srcLeft = loc[0].coerceAtLeast(0)
    val srcTop = loc[1].coerceAtLeast(0)
    val srcRight = (loc[0] + outW).coerceAtMost(bitmap.width)
    val srcBottom = (loc[1] + outH).coerceAtMost(bitmap.height)
    if (srcRight <= srcLeft || srcBottom <= srcTop) return out

    val dstLeft = (srcLeft - loc[0]).coerceAtLeast(0)
    val dstTop = (srcTop - loc[1]).coerceAtLeast(0)
    Canvas(out).drawBitmap(
        bitmap,
        Rect(srcLeft, srcTop, srcRight, srcBottom),
        Rect(dstLeft, dstTop, dstLeft + srcRight - srcLeft, dstTop + srcBottom - srcTop),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    return out
}

@Composable
private fun MixFloatSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val span = (max - min).coerceAtLeast(0.0001f)
    Column(Modifier.fillMaxWidth()) {
        BasicText("$label · ${"%.2f".format(value)}", style = TextStyle(Color.White.copy(alpha = 0.82f), 11.sp))
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
private fun MixButton(text: String, onClick: () -> Unit) {
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
private fun MixTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    BasicText(text, style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
}

@Composable
private fun MixStatus(text: String) {
    val color = when {
        text.startsWith("PASS") -> Color(0xFF8EE6A2)
        text.startsWith("HOST_ONLY") -> Color(0xFFFFCC80)
        text.startsWith("UNAVAILABLE") -> Color(0xFFFFB4A9)
        else -> Color.White.copy(alpha = 0.70f)
    }
    BasicText(text, style = TextStyle(color, 10.sp))
}

private fun mixInfoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun mixLabelStyle() = TextStyle(Color.White.copy(alpha = 0.92f), 12.sp, FontWeight.Medium)
private fun mixDiagnosticsStyle() = TextStyle(Color.White.copy(alpha = 0.68f), 9.sp)

private fun createMixWallpaper(context: Context): Bitmap {
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
        intArrayOf(
            AndroidColor.rgb(14, 28, 68),
            AndroidColor.rgb(145, 57, 176),
            AndroidColor.rgb(12, 176, 199),
            AndroidColor.rgb(253, 177, 48),
        ),
        floatArrayOf(0f, 0.34f, 0.70f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(w * 0.20f, h * 0.23f, w * 0.13f, paint)
    paint.color = AndroidColor.rgb(255, 70, 112)
    canvas.drawCircle(w * 0.78f, h * 0.47f, w * 0.18f, paint)
    paint.color = AndroidColor.rgb(48, 238, 198)
    canvas.drawCircle(w * 0.39f, h * 0.77f, w * 0.16f, paint)
    return bitmap
}

private fun mixDescribe(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}