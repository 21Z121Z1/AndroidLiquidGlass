package com.kyant.backdrop.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiDirectViewBridge
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.RoundedRectangle

/**
 * Runs complete SystemUI business views whose constructors/draw paths own their material hosts.
 * A PASS means the shipping View was attached and asked to render; there is no imitation fallback.
 */
@Composable
fun SystemUiBusinessViewsOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            BusinessButton("SYSUI 业务 View") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val wallpaper = remember(context) { createBusinessWallpaper(context) }
    val backdrop = rememberLayerBackdrop()
    var progress by rememberSaveable { mutableIntStateOf(65) }
    var qsStatus by remember { mutableStateOf("等待 OplusQsVerticalSeekBar…") }
    var volumeStatus by remember { mutableStateOf("等待 OplusVolumeSeekBar…") }

    Box(Modifier.fillMaxSize().background(Color(0xFF07090D))) {
        Image(
            painter = BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.layerBackdrop(backdrop).fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    "SystemUI 真实业务 View",
                    style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold),
                )
                BusinessButton("关闭") { open = false }
            }

            BasicText(
                "这一页与独立 shader 样本不同：直接 attach SystemUI 自己的业务 View。QS View 的 onDraw 会进入 QsSeekBarBlurManager；音量 View 的构造链会创建 OplusVolumeBarMaterialHost → OplusVolumeStrokeRenderer。若它们依赖 SystemUI-only 服务而失败，只报告 UNAVAILABLE。",
                style = businessInfoStyle(),
            )

            BusinessTitle("1 · 控制中心 OplusQsVerticalSeekBar")
            BasicText(
                "ColorOS 原生链：OplusQsVerticalSeekBar → QsSeekBarBlurManager → QS blur/mix/stroke。Kyant 仅作为机制参考：blur + vibrancy + Highlight，不声称几何或配方 1:1。",
                style = businessInfoStyle(),
            )
            KyantBusinessReference(backdrop, "Kyant seekbar material reference")
            AndroidView(
                factory = { BusinessHostView(it, BusinessKind.QS_VERTICAL) },
                update = { host ->
                    host.onStatus = { qsStatus = it }
                    host.updateProgress(progress)
                },
                modifier = Modifier.fillMaxWidth().height(260.dp),
            )
            BusinessStatus(qsStatus)

            BusinessTitle("2 · 音量 OplusVolumeSeekBar")
            BasicText(
                "直接运行完整音量条业务 View，而不是只运行 VolumeGradientStrokeShader。构造阶段已经进入 OplusVolumeBarMaterialHost 和 OplusVolumeStrokeRenderer，因此成功渲染时覆盖音量胶囊几何、材质宿主和描边渲染器的集成路径。",
                style = businessInfoStyle(),
            )
            KyantBusinessReference(backdrop, "Kyant volume material reference")
            AndroidView(
                factory = { BusinessHostView(it, BusinessKind.VOLUME) },
                update = { host ->
                    host.onStatus = { volumeStatus = it }
                    host.updateProgress(progress)
                },
                modifier = Modifier.fillMaxWidth().height(124.dp),
            )
            BusinessStatus(volumeStatus)

            BusinessIntSlider("progress", progress, 0, 100) { progress = it }

            BasicText(
                "判定标准：能实例化但不能 attach/draw 不算 PASS；只有实际 View 已装入宿主并完成 progress 更新、layout/draw 请求时才报告 PASS。最终视觉正确性仍需要 ColorOS 17 真机截图确认。",
                style = businessInfoStyle(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KyantBusinessReference(backdrop: com.kyant.backdrop.Backdrop, label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(30.dp) },
                effects = {
                    blur(14.dp.toPx())
                    vibrancy()
                },
                highlight = { Highlight.Default },
                shadow = null,
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.055f)) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
    }
}

private enum class BusinessKind { QS_VERTICAL, VOLUME }

private class BusinessHostView(
    context: Context,
    private val kind: BusinessKind,
) : FrameLayout(context) {
    private val bridgeResult = runCatching { ColorOsSystemUiDirectViewBridge(context) }
    private var materialView: View? = null
    private var progress = 65
    private var lastAppliedProgress = -1
    private var installed = false
    var onStatus: ((String) -> Unit)? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x18000000)
    }

    fun updateProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        post { ensureInstalledAndUpdate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { ensureInstalledAndUpdate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { ensureInstalledAndUpdate() }
    }

    private fun ensureInstalledAndUpdate() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val bridge = bridgeResult.getOrElse {
            onStatus?.invoke("UNAVAILABLE — direct bridge: ${businessDescribe(it)}")
            return
        }

        runCatching {
            val child = materialView ?: when (kind) {
                BusinessKind.QS_VERTICAL -> bridge.createQsVerticalSeekBar(progress).getOrThrow()
                BusinessKind.VOLUME -> bridge.createVolumeSeekBar(progress).getOrThrow()
            }.also { installMaterialView(it) }

            if (lastAppliedProgress != progress) {
                when (kind) {
                    BusinessKind.QS_VERTICAL -> bridge.updateQsVerticalSeekBar(child, progress).getOrThrow()
                    BusinessKind.VOLUME -> bridge.updateVolumeSeekBar(child, progress).getOrThrow()
                }
                lastAppliedProgress = progress
            }
            child.requestLayout()
            child.invalidate()
            invalidate()
        }.onSuccess {
            if (installed) {
                onStatus?.invoke(
                    when (kind) {
                        BusinessKind.QS_VERTICAL -> "PASS — real OplusQsVerticalSeekBar attached; QsSeekBarBlurManager draw path requested"
                        BusinessKind.VOLUME -> "PASS — real OplusVolumeSeekBar attached; VolumeBarMaterialHost/StrokeRenderer path requested"
                    },
                )
            }
        }.onFailure {
            onStatus?.invoke("UNAVAILABLE — $kind: ${businessDescribe(it)}")
        }
    }

    private fun installMaterialView(view: View) {
        materialView = view
        removeAllViews()
        val density = resources.displayMetrics.density
        val params = when (kind) {
            BusinessKind.QS_VERTICAL -> LayoutParams(
                (88f * density).toInt().coerceAtLeast(1),
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
            BusinessKind.VOLUME -> LayoutParams(
                LayoutParams.MATCH_PARENT,
                (88f * density).toInt().coerceAtLeast(1),
                Gravity.CENTER,
            )
        }
        addView(view, params)
        installed = true
    }
}

@Composable
private fun BusinessIntSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        BasicText("$label · $value", style = TextStyle(Color.White.copy(alpha = 0.82f), 11.sp))
        AndroidView(
            factory = { SeekBar(it).apply { this.max = max - min } },
            update = { seek ->
                seek.setOnSeekBarChangeListener(null)
                seek.max = max - min
                seek.progress = (value - min).coerceIn(0, max - min)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onChange(min + progress)
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
private fun BusinessButton(text: String, onClick: () -> Unit) {
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
private fun BusinessTitle(text: String) {
    BasicText(text, style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
}

@Composable
private fun BusinessStatus(text: String) {
    val color = when {
        text.startsWith("PASS") -> Color(0xFF8EE6A2)
        text.startsWith("UNAVAILABLE") -> Color(0xFFFFB4A9)
        else -> Color.White.copy(alpha = 0.70f)
    }
    BasicText(text, style = TextStyle(color, 10.sp))
}

private fun businessInfoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)

private fun createBusinessWallpaper(context: Context): Bitmap {
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
            AndroidColor.rgb(13, 28, 68),
            AndroidColor.rgb(147, 58, 177),
            AndroidColor.rgb(13, 174, 199),
            AndroidColor.rgb(252, 177, 49),
        ),
        floatArrayOf(0f, 0.34f, 0.70f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(w * 0.20f, h * 0.23f, w * 0.13f, paint)
    paint.color = AndroidColor.rgb(255, 72, 112)
    canvas.drawCircle(w * 0.79f, h * 0.47f, w * 0.18f, paint)
    paint.color = AndroidColor.rgb(48, 238, 198)
    canvas.drawCircle(w * 0.39f, h * 0.77f, w * 0.16f, paint)
    return bitmap
}

private fun businessDescribe(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}
