package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Unified visual host for ColorOsSystemUiExecutionRegistry routes. */
internal class ColorOsSystemUiRouteHostView(context: Context) : FrameLayout(context) {
    private val postEffect = runCatching { ColorOsSystemUiPostEffectBridge(context) }
    private val executable = runCatching { ColorOsSystemUiExecutableBridge(context) }
    private val notificationStroke = runCatching { ColorOsNotificationStrokeBridge(context) }
    private val direct = runCatching { ColorOsSystemUiDirectViewBridge(context) }
    private val interactive = runCatching { ColorOsSystemUiInteractiveEffectBridge(context) }
    private val parameterAudit = runCatching { ColorOsSystemUiParameterAuditBridge(context) }
    private val presetBridge = runCatching { ColorOsSystemUiPresetBridge(context) }
    private val blurMixBridge = runCatching { ColorOsSystemUiBlurMixBridge(context) }
    private val materialBridge = runCatching { ColorOsMaterialBridge(context) }

    private val backgroundView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
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
            ColorOsSystemUiExecutionRegistry.Kind.DIRECT_EXECUTABLE -> runDirect(route, implementation, bitmap)
            ColorOsSystemUiExecutionRegistry.Kind.GL_PIPELINE -> runGl(bitmap)
            ColorOsSystemUiExecutionRegistry.Kind.PARAMETER_EXECUTOR -> runParameterRoute(implementation, route, bitmap)
            ColorOsSystemUiExecutionRegistry.Kind.HOST_BOUND ->
                showBoundary("HOST_BOUND — 需要 SystemUI 业务宿主；不做仿制\n${route.implementation}")
            ColorOsSystemUiExecutionRegistry.Kind.SURFACE_CONTROL_BOUND ->
                showBoundary("SURFACE_CONTROL_BOUND — 需要系统合成器实时后景；不做仿制\n${route.implementation}")
        }
    }

    private fun runParameterRoute(
        implementation: String,
        route: ColorOsSystemUiExecutionRegistry.Route,
        bitmap: Bitmap,
    ) {
        when (route) {
            ColorOsSystemUiExecutionRegistry.Route.SHIPPING_PRESET_BROWSER -> runShippingPreset(implementation, bitmap)
            ColorOsSystemUiExecutionRegistry.Route.BLUR_MIX_RECIPE_EXECUTOR -> runBlurMixRecipe(implementation, bitmap)
            else -> runParameterAudit(implementation, route)
        }
    }

    private fun runShippingPreset(implementation: String, bitmap: Bitmap) {
        presetBridge.mapCatching { bridge ->
            val all = bridge.presets()
            val exactId = implementation
                .takeIf { it.startsWith(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX) }
                ?.removePrefix(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX)
            val matching = if (exactId != null) {
                all.filter { it.id == exactId }
            } else {
                all.filter { it.adapterClass == implementation }
            }
            require(matching.isNotEmpty()) {
                if (exactId != null) "shipping preset id $exactId not found"
                else "no executable shipping preset binder for $implementation"
            }
            val preset = if (exactId != null) matching.single() else {
                val index = (matching.lastIndex * progress).toInt().coerceIn(0, matching.lastIndex)
                matching[index]
            }
            val drawable = bridge.createPresetDrawable(
                bitmap = bitmap,
                width = width,
                height = height,
                cornerRadiusPx = radiusPx,
                preset = preset,
            ).getOrThrow()
            preset to DrawableSurfaceView(context, drawable)
        }.onSuccess { (preset, child) ->
            addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val exact = if (implementation.startsWith(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX)) {
                " [exact recipe row]"
            } else {
                " [adapter browser]"
            }
            postStatus("PASS — shipping preset ${preset.family}/${preset.methodName}$exact from ${preset.adapterClass}")
        }.onFailure {
            runParameterAudit(
                implementation,
                ColorOsSystemUiExecutionRegistry.Route.SHIPPING_PRESET_BROWSER,
                "preset visual unavailable: ${describe(it)}",
            )
        }
    }

    private fun runBlurMixRecipe(implementation: String, bitmap: Bitmap) {
        blurMixBridge.mapCatching { bridge ->
            val all = bridge.recipes()
            val exactId = implementation
                .takeIf { it.startsWith(ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX) }
                ?.removePrefix(ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX)
            val recipe = if (exactId != null) {
                all.firstOrNull { it.id == exactId }
                    ?: error("shipping BlurMix recipe id $exactId not found")
            } else {
                val wantedSource = if (implementation.contains("notification", ignoreCase = true)) {
                    ColorOsSystemUiBlurMixBridge.Source.NOTIFICATION
                } else {
                    ColorOsSystemUiBlurMixBridge.Source.QS
                }
                val directRecipes = all.filter {
                    it.source == wantedSource &&
                        it.executionHint == ColorOsSystemUiBlurMixBridge.Execution.DIRECT_SHADER
                }
                require(directRecipes.isNotEmpty()) { "no DIRECT_SHADER shipping blur/mix recipe for $wantedSource" }
                val index = (directRecipes.lastIndex * progress).toInt().coerceIn(0, directRecipes.lastIndex)
                directRecipes[index]
            }
            require(recipe.executionHint == ColorOsSystemUiBlurMixBridge.Execution.DIRECT_SHADER) {
                "${recipe.id} is HOST_ONLY and must not execute through the direct shader bridge"
            }
            val drawable = bridge.createDrawable(
                bitmap = bitmap,
                width = width,
                height = height,
                cornerRadiusPx = radiusPx,
                recipe = recipe,
                amount = progress,
            ).getOrThrow()
            recipe to DrawableSurfaceView(context, drawable)
        }.onSuccess { (recipe, child) ->
            addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val exact = if (implementation.startsWith(ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX)) {
                " [exact recipe row]"
            } else {
                " [provider browser]"
            }
            postStatus("PASS — shipping blur/mix ${recipe.label} · ${recipe.source}$exact")
        }.onFailure {
            runParameterAudit(
                implementation,
                ColorOsSystemUiExecutionRegistry.Route.BLUR_MIX_RECIPE_EXECUTOR,
                "blur/mix visual unavailable: ${describe(it)}",
            )
        }
    }

    private fun runParameterAudit(
        implementation: String,
        route: ColorOsSystemUiExecutionRegistry.Route,
        prefix: String? = null,
    ) {
        if (implementation.startsWith("assets/") || implementation.startsWith("res/raw/")) {
            showBoundary("PARAMETER/RESOURCE ROUTE — $implementation\n${route.implementation}")
            return
        }
        parameterAudit.mapCatching { it.inspect(implementation).getOrThrow() }
            .onSuccess { snapshot ->
                val evidence = buildList {
                    prefix?.let(::add)
                    add(snapshot.className)
                    snapshot.instanceSource?.let { add("instance=$it") }
                    addAll(snapshot.enumConstants.take(5).map { "enum: $it" })
                    addAll(snapshot.staticConstants.take(8).map { "const: $it" })
                    addAll(snapshot.getterValues.take(6).map { "getter: $it" })
                    addAll(snapshot.methodSignatures.take(12).map { "api: $it" })
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
                postStatus("PASS — vendor evidence ${snapshot.evidenceCount} entries · ${route.name}")
            }
            .onFailure { showBoundary("UNAVAILABLE — parameter/resource audit: ${describe(it)}") }
    }

    private fun runDirect(
        route: ColorOsSystemUiExecutionRegistry.Route,
        implementation: String,
        bitmap: Bitmap,
    ) {
        when (route) {
            ColorOsSystemUiExecutionRegistry.Route.COUI_MATERIAL_BLUR,
            ColorOsSystemUiExecutionRegistry.Route.COUI_MATERIAL_STROKE,
            ColorOsSystemUiExecutionRegistry.Route.COUI_SPOTLIGHT,
            ColorOsSystemUiExecutionRegistry.Route.COUI_TOOLBAR_STACK,
            ColorOsSystemUiExecutionRegistry.Route.COUI_PROGRESSIVE_BLUR -> {
                runCoui(route, implementation)
                return
            }
            ColorOsSystemUiExecutionRegistry.Route.KEYGUARD_GLASS_BUILDER -> {
                runClockGlass(bitmap)
                return
            }
            else -> Unit
        }

        val result: Result<View> = when (route) {
            ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_NOTIFICATION_SPOTLIGHT -> interactive.mapCatching { bridge ->
                val exactKind = implementation
                    .takeIf { it.startsWith(ColorOsSystemUiInteractiveRecipeInventory.NOTIFICATION_SPOTLIGHT_PREFIX) }
                    ?.removePrefix(ColorOsSystemUiInteractiveRecipeInventory.NOTIFICATION_SPOTLIGHT_PREFIX)
                val kind = exactKind
                    ?: bridge.notificationSpotLightKinds().firstOrNull { it == "NOTIFICATION_CARD" }
                    ?: bridge.notificationSpotLightKinds().firstOrNull()
                    ?: error("NotificationSpotLightParamsKind has no values")
                bridge.createNotificationSpotLight(kind).getOrThrow()
            }
            ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_QS_MEDIA_SPOTLIGHT -> interactive.mapCatching { bridge ->
                val exactShape = implementation
                    .takeIf { it.startsWith(ColorOsSystemUiInteractiveRecipeInventory.QS_MEDIA_SPOTLIGHT_PREFIX) }
                    ?.removePrefix(ColorOsSystemUiInteractiveRecipeInventory.QS_MEDIA_SPOTLIGHT_PREFIX)
                val shape = exactShape
                    ?: bridge.qsMediaClipShapes().firstOrNull { it == "CAPSULE" }
                    ?: bridge.qsMediaClipShapes().firstOrNull()
                    ?: error("QsMediaSpotLightHelper.ClipShape has no values")
                bridge.createQsMediaSpotLight(shape).getOrThrow()
            }
            ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_VOLUME_SETTINGS_SPOTLIGHT -> interactive.mapCatching { bridge ->
                bridge.createVolumeSettingsButtonSpotLight().getOrThrow()
            }
            ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_SCENARIO_METABALL_LIGHT -> interactive.mapCatching { bridge ->
                bridge.createScenarioMetaballLight().getOrThrow()
            }
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_COMPOSER,
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_SHAPE,
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_OPTICS,
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_STROKE,
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_INNER_SHADOW -> postEffect.mapCatching { bridge ->
                createPostEffectSurface(bridge, route, bitmap)
            }
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_METABALL -> executable.mapCatching { bridge ->
                DrawableSurfaceView(
                    context,
                    bridge.createMetaBallPostEffectDrawable(bitmap, width, height, radiusPx, progress).getOrThrow(),
                )
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
            else -> Result.failure(IllegalStateException("${route.name} is not a generic direct visual route"))
        }

        result.onSuccess { child ->
            child.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val detail = when (route) {
                ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_NOTIFICATION_SPOTLIGHT,
                ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_QS_MEDIA_SPOTLIGHT,
                ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_VOLUME_SETTINGS_SPOTLIGHT -> " · touch/drag this card"
                ColorOsSystemUiExecutionRegistry.Route.SYSTEMUI_SCENARIO_METABALL_LIGHT -> " · shipping animated light drawable"
                else -> ""
            }
            postStatus("PASS — ColorOS ${route.name} attached from installed SystemUI$detail")
        }.onFailure { showBoundary("UNAVAILABLE — ${route.name}: ${describe(it)}") }
    }

    private fun runCoui(route: ColorOsSystemUiExecutionRegistry.Route, implementation: String) {
        val child = CouiSurfaceView(context, radiusPx)
        addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        postStatus("RUNNING — ${route.name} from installed com.oplus.uxdesign")

        child.post {
            materialBridge.mapCatching { bridge ->
                val catalog = bridge.catalog()
                when (route) {
                    ColorOsSystemUiExecutionRegistry.Route.COUI_MATERIAL_BLUR -> {
                        val preset = exactPreset(implementation, ColorOsCouiPresetInventory.BLUR_PREFIX)
                            ?: choosePreset(catalog.blur)
                        require(preset in catalog.blur) { "BlurEffectType.$preset missing from installed uxdesign" }
                        bridge.applyBlur(child, preset).getOrThrow()
                        "COUIMaterialBlurEffect/$preset${exactSuffix(implementation)}"
                    }
                    ColorOsSystemUiExecutionRegistry.Route.COUI_MATERIAL_STROKE -> {
                        val preset = exactPreset(implementation, ColorOsCouiPresetInventory.STROKE_PREFIX)
                            ?: choosePreset(catalog.stroke)
                        require(preset in catalog.stroke) { "StrokeEffectType.$preset missing from installed uxdesign" }
                        bridge.applyStroke(child, preset).getOrThrow()
                        "COUIMaterialStrokeEffect/$preset${exactSuffix(implementation)}"
                    }
                    ColorOsSystemUiExecutionRegistry.Route.COUI_SPOTLIGHT -> {
                        val preset = exactPreset(implementation, ColorOsCouiPresetInventory.SPOTLIGHT_PREFIX)
                            ?: choosePreset(catalog.spotLight)
                        require(preset in catalog.spotLight) { "SpotLightType.$preset missing from installed uxdesign" }
                        bridge.applySpotLight(child, preset).getOrThrow()
                        "COUISpotLightEffect/$preset${exactSuffix(implementation)} — touch this card to drive hotspot"
                    }
                    ColorOsSystemUiExecutionRegistry.Route.COUI_TOOLBAR_STACK -> {
                        val category = exactPreset(implementation, ColorOsCouiPresetInventory.TOOLBAR_PREFIX)
                            ?: choosePreset(catalog.toolbarCategories)
                        require(category in catalog.toolbarCategories) { "ViewCategory.$category missing from installed uxdesign" }
                        bridge.applyToolbarStack(
                            child,
                            categoryName = category,
                            blur = true,
                            stroke = true,
                            spotLight = true,
                            caustic = true,
                            forceEnable = true,
                        ).getOrThrow()
                        "ToolbarMaterialEffectDelegate/$category${exactSuffix(implementation)} blur+stroke+spotlight+caustic"
                    }
                    ColorOsSystemUiExecutionRegistry.Route.COUI_PROGRESSIVE_BLUR -> {
                        bridge.applyGradientBlur(child, progress).getOrThrow()
                        "AppBarBlurHelper gradientBlur fraction=${"%.2f".format(progress)}"
                    }
                    else -> error("$route is not a COUI route")
                }
            }.onSuccess { postStatus("PASS — $it") }
                .onFailure {
                    materialBridge.getOrNull()?.clear(child)
                    removeView(child)
                    showBoundary("UNAVAILABLE — ${route.name}: ${describe(it)}")
                }
        }
    }

    private fun exactPreset(implementation: String, prefix: String): String? =
        implementation.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.takeIf { it.isNotBlank() }

    private fun exactSuffix(implementation: String): String =
        if (implementation.startsWith(ColorOsCouiPresetInventory.PREFIX)) " [exact preset row]"
        else " [family browser]"

    private fun choosePreset(values: List<String>): String {
        val usable = values.filterNot {
            it.contains("NO_EFFECT", ignoreCase = true) ||
                it.equals("NONE", ignoreCase = true) ||
                it.contains("DISABLE", ignoreCase = true)
        }.ifEmpty { values }
        require(usable.isNotEmpty()) { "installed vendor enum exposes no presets" }
        val index = (usable.lastIndex * progress).toInt().coerceIn(0, usable.lastIndex)
        return usable[index]
    }

    private fun runClockGlass(bitmap: Bitmap) {
        val glass = ColorOsClockGlassSurfaceView(context).apply { onStatus = { postStatus(it) } }
        addView(glass, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        glass.configure(bitmap, radiusPx, progress, 1f, 1f, true)
        postStatus("RUNNING — personality-clocks GlassEffectBuilder; waiting for real RenderEffect")
    }

    private fun createPostEffectSurface(
        bridge: ColorOsSystemUiPostEffectBridge,
        route: ColorOsSystemUiExecutionRegistry.Route,
        bitmap: Bitmap,
    ): View {
        val modules = when (route) {
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_COMPOSER -> Triple(true, true, true)
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_OPTICS -> Triple(true, false, false)
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_STROKE -> Triple(false, true, false)
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_INNER_SHADOW -> Triple(false, false, true)
            ColorOsSystemUiExecutionRegistry.Route.POST_EFFECT_SHAPE -> Triple(false, false, false)
            else -> error("$route is not a PostEffect surface route")
        }
        val drawable = bridge.createPostEffectDrawable(
            bitmap,
            width,
            height,
            ColorOsSystemUiPostEffectBridge.PostEffectOptions(
                "G2",
                radiusPx,
                1f,
                modules.first,
                modules.second,
                modules.third,
            ),
        ).getOrThrow()
        return DrawableSurfaceView(context, drawable)
    }

    private fun runGl(bitmap: Bitmap) {
        runCatching {
            val view = ColorOsSystemUiGlBlurView(context).apply {
                onStatus = { postStatus(it) }
                configure(bitmap, blurRadius = (4 + progress * 16).toInt(), blendMode = 4)
            }
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            postStatus("RUNNING — SystemUI GLES blur_down → gaussian H/V → blur_up → display")
        }.onFailure { showBoundary("UNAVAILABLE — SystemUI GL: ${describe(it)}") }
    }

    private fun clearRouteChildren() {
        while (childCount > 1) {
            val child = getChildAt(childCount - 1)
            if (child is ColorOsSystemUiGlBlurView) runCatching { child.onPause() }
            materialBridge.getOrNull()?.let { bridge -> runCatching { bridge.clear(child) } }
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

    private class CouiSurfaceView(context: Context, private val radiusPx: Float) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0CFFFFFF }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width.coerceAtLeast(1),
                        view.height.coerceAtLeast(1),
                        radiusPx,
                    )
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, fill)
        }
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
