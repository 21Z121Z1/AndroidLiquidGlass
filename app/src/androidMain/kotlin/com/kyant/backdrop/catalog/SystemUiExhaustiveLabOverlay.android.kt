package com.kyant.backdrop.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
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
import com.kyant.backdrop.catalog.coloros.ColorOsNotificationStrokeBridge
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiGlBlurView
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/**
 * Supplemental SystemUI lab layered above the normal catalog.
 *
 * The main ColorOS comparison page already executes keyguard refraction, COUI material,
 * generic PostEffect, QS/volume stroke, barglow, chromatic and Metaball. This overlay
 * closes the two remaining direct-execution gaps (notification capsule StrokeShader and
 * the shipping GLES blur/display pipeline) and exposes the exhaustive runtime coverage audit.
 */
@Composable
fun SystemUiExhaustiveLabOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            LabButton("SYSUI 全量审计") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val wallpaper = remember(context) { createAuditWallpaper(context) }
    val backdrop = rememberLayerBackdrop()
    val catalog = remember(context) { ColorOsSystemUiLiquidGlassCatalog(context) }
    val mappings = remember(catalog) { catalog.mappings() }
    val coverage = remember(catalog) { catalog.coverageSummary() }

    var notificationStatus by remember { mutableStateOf("等待通知胶囊 StrokeShader…") }
    var glStatus by remember { mutableStateOf("等待 SystemUI GLES 模糊管线…") }
    var glRadius by rememberSaveable { mutableIntStateOf(8) }
    var glBlend by rememberSaveable { mutableIntStateOf(4) }

    Box(Modifier.fillMaxSize().background(Color(0xFF080A0F))) {
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
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    "SystemUI Liquid Glass 全量审计",
                    style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold),
                )
                LabButton("关闭") { open = false }
            }

            BasicText(
                "这层只补主矩阵尚未直接显示的实现，并把运行时 DEX + shader 资源扫描结果作为覆盖率闸门。未找到 1:1 Kyant primitive 的 ColorOS 模块仍必须显式映射为“无 1:1”，不能从清单消失。",
                style = infoStyle(),
            )

            CoverageCard(coverage)

            LabTitle("A · 通知锁屏胶囊独立 StrokeShader")
            BasicText(
                "ColorOS 这条不是通用 GradientStrokeLine，也不是 QS/音量描边。类 com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeShader 本身继承 RuntimeShader；右侧样本直接实例化设备 SystemUI 类。Kyant 对照为 Highlight/描边机制。",
                style = infoStyle(),
            )
            BasicText("Kyant · Highlight + InnerShadow", style = labelStyle())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(34.dp) },
                        effects = { blur(6.dp.toPx()) },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        innerShadow = { InnerShadow.Default },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Kyant stroke reference", style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
            }

            BasicText("ColorOS Notification · shipping StrokeShader", style = labelStyle())
            AndroidView(
                factory = { NotificationStrokeHostView(it) },
                update = { view ->
                    view.onStatus = { notificationStatus = it }
                    view.configure(34f * view.resources.displayMetrics.density)
                },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            LabStatus(notificationStatus)

            LabTitle("B · SystemUI GLES 模糊 + display 材质合成")
            BasicText(
                "这是与 COUI RenderEffect、PostEffect RuntimeShader 都独立的一条真实 OpenGL ES 管线。ColorOS 样本直接从安装的 SystemUI APK 读取并编译 blur_down / gaussian / blur_up / display shader，经过 FBO 串联执行；Kyant 对照为 blur() + vibrancy()。",
                style = infoStyle(),
            )

            BasicText("Kyant · blur() + vibrancy()", style = labelStyle())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(32.dp) },
                        effects = {
                            blur(18.dp.toPx())
                            vibrancy()
                        },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Kyant blur + vibrancy", style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
            }

            BasicText("ColorOS SystemUI · GLES shipping pipeline", style = labelStyle())
            AndroidView(
                factory = { ColorOsSystemUiGlBlurView(it) },
                update = { view ->
                    view.onStatus = { glStatus = it }
                    view.configure(wallpaper, blurRadius = glRadius, blendMode = glBlend)
                },
                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)),
            )
            LabStatus(glStatus)
            IntSlider("Gaussian radius", glRadius, 1, 24) { glRadius = it }
            IntSlider("display blend mode", glBlend, 0, 4) { glBlend = it }

            LabTitle("C · 每一项 SystemUI 实现 ↔ Kyant")
            BasicText(
                "以下列表来自当前设备运行时扫描，不是只看我们手写的几项。类来自 SystemUI base/split DEX；着色器来自 assets 与 res/raw。每一行都有明确 Kyant 对应机制和宿主限制。",
                style = infoStyle(),
            )

            mappings.groupBy { it.group }.forEach { (group, rows) ->
                BasicText(group, style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                rows.forEach { row -> MappingCard(row) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CoverageCard(summary: ColorOsSystemUiLiquidGlassCatalog.CoverageSummary) {
    val pass = summary.complete
    val color = if (pass) Color(0xFF8EE6A2) else Color(0xFFFF8A80)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            if (pass) "PASS · 映射覆盖 ${summary.coveragePercent}%" else "FAIL · 仍有 ${summary.unmapped} 项未映射",
            style = TextStyle(color, 15.sp, FontWeight.SemiBold),
        )
        BasicText(
            "总计 ${summary.total} · 当前固件可用 ${summary.available} · 不可用/版本差异 ${summary.unavailable}",
            style = diagnosticsStyle(),
        )
        BasicText(
            "DIRECT_VIEW ${summary.directView} · SURFACE_CONTROL ${summary.surfaceControl} · SYSTEM_UI_HOST ${summary.systemUiHost} · GL_PIPELINE ${summary.glPipeline} · CAPABILITY_ONLY ${summary.capabilityOnly}",
            style = diagnosticsStyle(),
        )
        BasicText(
            "机制/近似对应 ${summary.exactOrMechanismMapped} · 明确无 1:1 ${summary.noOneToOneButExplicitlyMapped} · 未映射 ${summary.unmapped}",
            style = diagnosticsStyle(),
        )
        summary.unmappedImplementations.forEach {
            BasicText("UNMAPPED · $it", style = TextStyle(Color(0xFFFF8A80), 9.sp))
        }
    }
}

@Composable
private fun MappingCard(row: ColorOsSystemUiLiquidGlassCatalog.Mapping) {
    val available = row.status.startsWith("available")
    val color = if (available) Color(0xFF9DE7AA) else Color(0xFFFFB4A9)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(row.systemUiImplementation, style = TextStyle(Color.White, 10.sp, FontWeight.Medium))
        BasicText("↔ Kyant：${row.kyantCounterpart}", style = diagnosticsStyle())
        BasicText("${row.executionMode} · ${row.status}", style = TextStyle(color, 9.sp))
        BasicText(row.note, style = diagnosticsStyle())
    }
}

private class NotificationStrokeHostView(context: Context) : View(context) {
    private val bridge = ColorOsNotificationStrokeBridge(context)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
            shaderPaint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
            shaderPaint.shader = null
        }
    }

    private fun applyIfReady() {
        if (width <= 0 || height <= 0) return
        val key = "$width:$height:$radiusPx"
        if (key == lastKey) return
        lastKey = key
        bridge.create(width, height, radiusPx)
            .onSuccess {
                shader = it
                invalidate()
                onStatus?.invoke("PASS — Notification capsule StrokeShader directly executed")
            }
            .onFailure {
                shader = null
                lastKey = null
                onStatus?.invoke("UNAVAILABLE — Notification StrokeShader: ${describe(it)}")
            }
    }
}

@Composable
private fun IntSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
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
private fun LabButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun LabTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    BasicText(text, style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
}

@Composable
private fun LabStatus(text: String) {
    val color = when {
        text.startsWith("PASS") -> Color(0xFF8EE6A2)
        text.startsWith("UNAVAILABLE") -> Color(0xFFFFCC80)
        else -> Color.White.copy(alpha = 0.68f)
    }
    BasicText(text, style = TextStyle(color, 10.sp))
}

private fun infoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun labelStyle() = TextStyle(Color.White.copy(alpha = 0.92f), 12.sp, FontWeight.Medium)
private fun diagnosticsStyle() = TextStyle(Color.White.copy(alpha = 0.68f), 9.sp)

private fun createAuditWallpaper(context: Context): Bitmap {
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
            AndroidColor.rgb(12, 28, 67),
            AndroidColor.rgb(136, 62, 174),
            AndroidColor.rgb(17, 170, 194),
            AndroidColor.rgb(250, 175, 46),
        ),
        floatArrayOf(0f, 0.34f, 0.71f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    paint.color = AndroidColor.WHITE
    canvas.drawCircle(w * 0.22f, h * 0.25f, w * 0.13f, paint)
    paint.color = AndroidColor.rgb(255, 70, 108)
    canvas.drawCircle(w * 0.78f, h * 0.48f, w * 0.18f, paint)
    paint.color = AndroidColor.rgb(52, 235, 198)
    canvas.drawCircle(w * 0.39f, h * 0.77f, w * 0.16f, paint)

    paint.color = 0xA8FFFFFF.toInt()
    paint.strokeWidth = 2f
    val step = (w / 18f).coerceAtLeast(24f)
    var x = 0f
    while (x < w) {
        canvas.drawLine(x, 0f, x, h.toFloat(), paint)
        x += step
    }
    return bitmap
}

private fun describe(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}
