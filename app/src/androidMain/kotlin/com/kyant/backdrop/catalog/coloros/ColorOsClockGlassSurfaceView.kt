package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewTreeObserver

/**
 * Small reusable host for the real personality-clocks GlassEffectBuilder.
 * It draws a vendor-provided GlassRegion marker as the view content/mask and lets the installed
 * ColorOS builder create the actual refraction/dispersion RenderEffect.
 */
internal class ColorOsClockGlassSurfaceView(context: Context) : View(context) {
    private val bridge = ColorOsClockGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var markerColor: Int? = bridge.locationColor().getOrNull()
    private var wallpaper: Bitmap? = null
    private var radiusPx = 0f
    private var glass = 1f
    private var mix = 1f
    private var mask = 1f
    private var light = true
    private var lastKey: String? = null
    private var scrollUpdatePosted = false

    var onStatus: ((String) -> Unit)? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (!scrollUpdatePosted) {
            scrollUpdatePosted = true
            postOnAnimation {
                scrollUpdatePosted = false
                lastKey = null
                applyIfReady()
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        paint.color = markerColor ?: Color.TRANSPARENT
    }

    fun configure(
        wallpaper: Bitmap,
        radiusPx: Float,
        glass: Float = 1f,
        mix: Float = 1f,
        mask: Float = 1f,
        light: Boolean = true,
    ) {
        this.wallpaper = wallpaper
        this.radiusPx = radiusPx.coerceAtLeast(0f)
        this.glass = glass.coerceIn(0f, 1f)
        this.mix = mix.coerceIn(0f, 1f)
        this.mask = mask.coerceIn(0f, 1f)
        this.light = light
        markerColor = markerColor ?: bridge.locationColor().getOrNull()
        paint.color = markerColor ?: Color.TRANSPARENT
        lastKey = null
        invalidate()
        applyIfReady()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewTreeObserver.isAlive) viewTreeObserver.addOnScrollChangedListener(scrollListener)
        lastKey = null
        applyIfReady()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKey = null
        applyIfReady()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val marker = markerColor ?: return
        paint.color = marker
        val r = radiusPx.coerceAtMost(minOf(width, height) / 2f)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        val marker = markerColor ?: bridge.locationColor().getOrElse {
            onStatus?.invoke("UNAVAILABLE — marker: ${describe(it)}")
            return
        }.also { markerColor = it }
        if (width <= 0 || height <= 0 || !isAttachedToWindow) return

        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = "${System.identityHashCode(bg)}:$width:$height:${location[0]}:${location[1]}:$radiusPx:$marker:$glass:$mix:$mask:$light"
        if (key == lastKey) return
        lastKey = key
        paint.color = marker
        invalidate()

        bridge.apply(this, bg, glass, mix, mask, light)
            .onSuccess { onStatus?.invoke("PASS — $it") }
            .onFailure {
                lastKey = null
                bridge.clear(this)
                onStatus?.invoke("UNAVAILABLE — ${describe(it)}")
            }
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
