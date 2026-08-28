package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Unified visual host for ColorOsSystemUiExecutionRegistry routes.
 *
 * DIRECT_EXECUTABLE and GL_PIPELINE routes execute code/resources from the installed
 * com.android.systemui package. PARAMETER_EXECUTOR routes inspect the real vendor class and show
 * shipping constants/getters/signatures. HOST/SURFACE_CONTROL routes remain explicit boundaries.
 */
internal class ColorOsSystemUiRouteHostView(context: Context) : FrameLayout(context) {
    private val postEffect = runCatching { ColorOsSystemUiPostEffectBridge(context) }
    private val executable = runCatching { ColorOsSystemUiExecutableBridge(context) }
    private val notificationStroke = runCatching { ColorOsNotificationStrokeBridge(context) }
    private val direct = runCatching { ColorOsSystemUiDirectViewBridge(context) }
    private val parameterAudit = runCatching { ColorOsSystemUiParameterAuditBridge(context) }

    private val backgroundView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private var currentKey: String? = null
    private var pendingApply = false
    private var wallpaper: Bitmap? = null
    private var route: ColorOsSystemUiExecutionRegistry.Route? = null
    private var implementationName: String? = null
    private var radiusPx: Float = 0f
    private var progress: Float = 0.65f

    var onStatus: ((String) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        addView(backgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun configure(
        route: ColorOsSystemUiExecutionRegistry.Route?,
        implementationName: String,
        wallpaper: Bitmap,
        radiusPx: Float,
        progress: Float = 0.65f,
    ) {
        this.route = route
        this.implementationName = implementationName
        this.wallpaper = wallpaper
        this.radiusPx = radiusPx.coerceAtLeast(0f)
        this.progress = progress.coerceIn(0f, 1f)
        backgroundView.setImageBitmap(wallpaper)
        scheduleApply(force = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleApply(force = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        currentKey = null
        scheduleApply(force = true)
    }

    override fun onDetachedFromWindow() {
        clearRouteChildren()
        super.onDetachedFromWindow()
    }

    private fun scheduleApply(force: Boolean) {
        if (force) currentKey = null
        if (pendingApply) return
        pendingApply = true
        postOnAnimation {
            pendingApply = false
            applyIfReady()
        }
    }

    private fun applyIfReady() {
        val route = route ?: run {
            showBoundary("MISSING_ROUTE")
            return
        }
        val bitmap = wallpaper ?: return
        val implementation = implementationName.orEmpty()
        if (width <= 0 || height <= 0 || !isAttachedToWindow) return

        val key = "${route.name}:$implementation:${width}x$height:${System.identityHashCode(bitmap)}:${radiusPx.toInt()}:${(progress * 1000).toInt()}"
        if (key == currentKey) return
        currentKey = key
        clearRouteChildren()

        when (route.kind) {
            ColorOsSystemUiExecutionRegistry.Kind.DIRECT_EXECUTABLE -> runDirect(route, bitmap)
            ColorOsSystemUiExecutionRegistry.Kind.GL_PIPELINE -> runGl(bitmap)
            ColorOsSystemUiExecutionRegistry.Kind.PARAMETER_EXECUTOR -> runParameterAudit(implementation, route)
            ColorOsSystemUiExecutionRegistry.Kind.HOST_BOUND ->
                showBoundary("HOST_BOUND — 需要 SystemUI 业务宿主；不做仿制\n${route.implementation}")
            ColorOsSystemUiExecutionRegistry.Kind.SURFACE_CONTROL_BOUND ->
                showBoundary("SURFACE_CONTROL_BOUND — 需要系统合成器实时后景；不做仿制\n${route.implementation}")
        }
    }

    private fun runParameterAudit(implementation: String, route: ColorOsSystemUiExecutionRegistry.Route) {
        if (implementation.startsWith("assets/") || implementation.startsWith("res/raw/")) {
            showBoundary("PARAMETER/RESOURCE ROUTE — ${route.implementation}")
            return
        }
        parameterAudit.mapCatching { it.inspect(implementation).getOrThrow() }
            .onSuccess { snapshot ->
                val evidence = buildList {
                    add(snapshot.className)
                    snapshot.instanceSource?.let { add("instance=$it") }
                    addAll(snapshot.enumConstants.take(5).map { "enum: $it" })
                    addAll(snapshot.staticConstants.take(6).map { "const: $it" })
                    addAll(snapshot.getterValues.take(6).map { "getter: $it" })
                    addAll(snapshot.methodSignatures.take(8).map { "api: $it" })
                }
                val label = TextView(context).apply {
                    text = evidence.joinToString("\n")
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 8.5f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(18, 12, 18, 12)
                    setBackgroundColor(0x88000000.toInt())
                }
                addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                postStatus("PASS — vendor parameter evidence ${snapshot.evidenceCount} entries · ${route.name}")
            }
            .onFailure {
                showBoundary("UNAVAILABLE — parameter audit: ${describe(it)}")
            }
    }

    private fun runDirect(route: ColorOsSystemUiExecutionRegistry.Route, bitmap: Bitmap) {
        val result: Result<View> = when (route) {
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_COMPOSER -> postEffect.mapCatching { bridge ->
                val drawable = bridge.createPostEffectDrawable(
                    bitmap = bitmap,
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
                DrawableSurfaceView(context, drawable)
            }

            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_METABALL -> executable.mapCatching { bridge ->
                val drawable = bridge.createMetaBallPostEffectDrawable(
                    bitmap = bitmap,
                    width = width,
                    height = height,
                    radiusPx = radiusPx,
                    phase = progress,
                ).getOrThrow()
                DrawableSurfaceView(context, drawable)
            }

            ColorOsSystemUiExecutionRegistry.Route.CHROMATIC_SHADER -> postEffect.mapCatching { bridge ->
                BitmapSurfaceView(context, bitmap).also { child ->
                    child.post {
                        bridge.applyChromatic(child, (width.coerceAtMost(height) * 0.018f).coerceAtLeast(1f))
                            .onFailure { postStatus("UNAVAILABLE — chromatic: ${describe(it)}") }
                    }
                }
            }

            ColorOsSystemUiExecutionRegistry.Route.BAR_GLOW_SHADER -> executable.mapCatching { bridge ->
                BitmapSurfaceView(context, bitmap).also { child ->
                    child.post {
                        bridge.applyBarGlow(child)
                            .onFailure { postStatus("UNAVAILABLE — barglow: ${describe(it)}") }
                    }
                }
            }

            ColorOsSystemUiExecutionRegistry.Route.RAW_METABALL_SHADER -> executable.mapCatching { bridge ->
                ShaderSurfaceView(context, bridge.createRawMetaballShader(bitmap, width, height, progress).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.QS_STROKE_SHADER -> executable.mapCatching { bridge ->
                ShaderSurfaceView(context, bridge.createQsStrokeShader(width, height, radiusPx).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.VOLUME_STROKE_SHADER -> executable.mapCatching { bridge ->
                ShaderSurfaceView(context, bridge.createVolumeStrokeShader(width, height, radiusPx).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.NOTIFICATION_STROKE_SHADER -> notificationStroke.mapCatching { bridge ->
                ShaderSurfaceView(context, bridge.create(width, height, radiusPx).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.WALLPAPER_BLUR_DRAWABLE -> executable.mapCatching { bridge ->
                DrawableSurfaceView(context, bridge.createWallpaperBlurDrawable(bitmap, 0x18FFFFFF).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.QS_PROGRESSIVE_BLUR_VIEW -> direct.mapCatching {
                it.createQsProgressiveBlur(progress).getOrThrow()
            }

            ColorOsSystemUiExecutionRegistry.Route.NOTIFICATION_TILT_SHIFT_VIEW -> direct.mapCatching {
                it.createNotificationTiltShift(width, height).getOrThrow()
            }

            ColorOsSystemUiExecutionRegistry.Route.KEYGUARD_GRADIENT_BLUR_VIEW -> direct.mapCatching {
                it.createKeyguardGradientBlur(bitmap, progress).getOrThrow()
            }

            ColorOsSystemUiExecutionRegistry.Route.QS_MULTI_LIGHT_SHADER -> direct.mapCatching {
                ShaderSurfaceView(context, it.createQsMultiLightShader(width, height, radiusPx).getOrThrow())
            }

            ColorOsSystemUiExecutionRegistry.Route.QS_BUSINESS_SEEKBAR -> direct.mapCatching {
                it.createQsVerticalSeekBar((progress * 100).toInt()).getOrThrow()
            }

            ColorOsSystemUiExecutionRegistry.Route.VOLUME_BUSINESS_SEEKBAR -> direct.mapCatching {
                it.createVolumeSeekBar((progress * 100).toInt()).getOrThrow()
            }

            else -> Result.failure(IllegalStateException("${route.name} is not a direct visual route"))
        }

        result.onSuccess { child ->
            child.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            postStatus("PASS — ColorOS ${route.name} attached from installed SystemUI")
        }.onFailure {
            showBoundary("UNAVAILABLE — ${route.name}: ${describe(it)}")
        }
    }

    private fun runGl(bitmap: Bitmap) {
        runCatching {
            val view = ColorOsSystemUiGlBlurView(context).apply {
                onStatus = { postStatus(it) }
                configure(bitmap, blurRadius = (4 + progress * 16).toInt(), blendMode = 4)
            }
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            postStatus("RUNNING — SystemUI GLES blur_down → gaussian H/V → blur_up → display")
        }.onFailure {
            showBoundary("UNAVAILABLE — SystemUI GL: ${describe(it)}")
        }
    }

    private fun clearRouteChildren() {
        while (childCount > 1) {
            val child = getChildAt(childCount - 1)
            if (child is ColorOsSystemUiGlBlurView) runCatching { child.onPause() }
            removeViewAt(childCount - 1)
        }
        postEffect.getOrNull()?.clear(this)
        executable.getOrNull()?.clear(this)
    }

    private fun showBoundary(message: String) {
        val label = TextView(context).apply {
            text = message
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0x66000000)
        }
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        postStatus(message)
    }

    private fun postStatus(message: String) {
        post { onStatus?.invoke(message) }
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }

    private class BitmapSurfaceView(context: Context, private val bitmap: Bitmap) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), paint)
        }
    }

    private class DrawableSurfaceView(context: Context, private val drawable: Drawable) : View(context) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            drawable.setBounds(0, 0, w, h)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawable.draw(canvas)
        }
    }

    private class ShaderSurfaceView(context: Context, shader: RuntimeShader) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
