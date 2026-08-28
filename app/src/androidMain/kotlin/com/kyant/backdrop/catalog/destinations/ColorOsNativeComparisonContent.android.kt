package com.kyant.backdrop.catalog.destinations

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsClockGlassBridge
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

@Composable
actual fun ColorOsNativeComparisonContent() {
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf(createTestWallpaper(context)) }
    var glass by remember { mutableFloatStateOf(1f) }
    var mix by remember { mutableFloatStateOf(1f) }
    var mask by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("Waiting for ColorOS native view…") }
    val backdrop = rememberLayerBackdrop()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                } ?: error("Bitmap decode returned null")
            }.onSuccess { wallpaper = normalizeWallpaper(context, it) }
                .onFailure { status = "Photo load failed: ${it.javaClass.simpleName}: ${it.message}" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.layerBackdrop(backdrop).fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText("ColorOS native vs generic", style = TextStyle(Color.White, 24.sp))
            BasicText(
                "The ColorOS panel fails closed: it is labelled native only after the installed personality-clocks package returns a real android.graphics.RenderEffect.",
                style = TextStyle(Color.White.copy(alpha = 0.86f), 13.sp)
            )

            BasicText("Generic Android / Kyant", style = TextStyle(Color.White, 15.sp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(40.dp) },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 20.dp.toPx(),
                                refractionAmount = 34.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.12f)) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Kyant RuntimeShader lens", style = TextStyle(Color.White, 16.sp))
            }

            BasicText("ColorOS 17 / GlassEffectBuilder", style = TextStyle(Color.White, 15.sp))
            AndroidView(
                factory = { ctx ->
                    ColorOsClockGlassHostView(ctx).also { view ->
                        view.onStatus = { status = it }
                    }
                },
                update = { view ->
                    view.configure(wallpaper, glass, mix, mask, true)
                },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )

            BasicText("Glass", style = TextStyle(Color.White, 13.sp))
            LiquidSlider(
                value = { glass },
                onValueChange = { glass = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = backdrop
            )
            BasicText("Mix progress", style = TextStyle(Color.White, 13.sp))
            LiquidSlider(
                value = { mix },
                onValueChange = { mix = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = backdrop
            )
            BasicText("Mask color progress", style = TextStyle(Color.White, 13.sp))
            LiquidSlider(
                value = { mask },
                onValueChange = { mask = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = backdrop
            )

            LiquidButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                backdrop = backdrop,
                tint = Color(0xFF0088FF)
            ) {
                BasicText("Pick comparison image", Modifier.padding(horizontal = 10.dp), style = TextStyle(Color.White, 15.sp))
            }

            BasicText(status, style = TextStyle(Color.White, 12.sp))
        }
    }
}

private class ColorOsClockGlassHostView(context: Context) : View(context) {
    private val bridge = ColorOsClockGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var encodedColor: Int? = null
    private var wallpaper: Bitmap? = null
    private var glass = 1f
    private var mix = 1f
    private var mask = 1f
    private var light = true
    private var lastKey: String? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        applyIfReady()
    }

    var onStatus: ((String) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), 40f * resources.displayMetrics.density)
            }
        }
        encodedColor = bridge.locationColor().getOrElse {
            onStatus?.invoke("ColorOS region-color lookup failed: ${it.javaClass.simpleName}: ${it.message}")
            null
        }
        paint.color = encodedColor ?: AndroidColor.TRANSPARENT
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }

    fun configure(bitmap: Bitmap, glass: Float, mix: Float, mask: Float, light: Boolean) {
        this.wallpaper = bitmap
        this.glass = glass
        this.mix = mix
        this.mask = mask
        this.light = light
        invalidate()
        applyIfReady()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        applyIfReady()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 40f * resources.displayMetrics.density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        }
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        val color = encodedColor ?: bridge.locationColor().getOrElse {
            onStatus?.invoke("ColorOS region-color lookup failed: ${it.javaClass.simpleName}: ${it.message}")
            return
        }.also {
            encodedColor = it
            paint.color = it
        }
        if (width <= 0 || height <= 0) return
        val screenLocation = IntArray(2)
        getLocationOnScreen(screenLocation)
        val key = "${System.identityHashCode(bg)}:$width:$height:${screenLocation[0]}:${screenLocation[1]}:$color:$glass:$mix:$mask:$light"
        if (key == lastKey) return
        lastKey = key
        bridge.apply(this, bg, glass, mix, mask, light)
            .onSuccess { result ->
                onStatus?.invoke("PASS — real ColorOS RenderEffect attached. $result\n${bridge.diagnostics().joinToString("\n")}")
            }
            .onFailure { error ->
                lastKey = null
                bridge.clear(this)
                onStatus?.invoke("UNAVAILABLE — no fallback used. ${error.javaClass.simpleName}: ${error.message}\n${bridge.diagnostics().joinToString("\n")}")
            }
    }
}

private fun createTestWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val bitmap = Bitmap.createBitmap(dm.widthPixels.coerceAtLeast(720), dm.heightPixels.coerceAtLeast(1280), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
        intArrayOf(AndroidColor.rgb(24, 43, 92), AndroidColor.rgb(143, 72, 172), AndroidColor.rgb(24, 168, 196), AndroidColor.rgb(252, 187, 61)),
        floatArrayOf(0f, 0.35f, 0.72f, 1f), Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(bitmap.width * 0.22f, bitmap.height * 0.28f, bitmap.width * 0.14f, paint)
    paint.color = AndroidColor.rgb(255, 74, 112)
    canvas.drawCircle(bitmap.width * 0.78f, bitmap.height * 0.48f, bitmap.width * 0.18f, paint)
    paint.color = AndroidColor.rgb(54, 238, 201)
    canvas.drawCircle(bitmap.width * 0.38f, bitmap.height * 0.78f, bitmap.width * 0.16f, paint)
    return bitmap
}

private fun normalizeWallpaper(context: Context, source: Bitmap): Bitmap {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(1)
    val h = dm.heightPixels.coerceAtLeast(1)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val scale = maxOf(w / source.width.toFloat(), h / source.height.toFloat())
    val sw = (w / scale).toInt().coerceAtLeast(1)
    val sh = (h / scale).toInt().coerceAtLeast(1)
    val left = ((source.width - sw) / 2).coerceAtLeast(0)
    val top = ((source.height - sh) / 2).coerceAtLeast(0)
    Canvas(out).drawBitmap(source, Rect(left, top, (left + sw).coerceAtMost(source.width), (top + sh).coerceAtMost(source.height)), Rect(0, 0, w, h), Paint(Paint.ANTI_ALIAS_FLAG))
    return out
}
