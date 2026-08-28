package com.kyant.backdrop.catalog.coloros

/** Machine-readable execution route for each ColorOS SystemUI material row. */
internal object ColorOsSystemUiExecutionRegistry {
    enum class Kind {
        DIRECT_EXECUTABLE,
        GL_PIPELINE,
        PARAMETER_EXECUTOR,
        HOST_BOUND,
        SURFACE_CONTROL_BOUND,
    }

    enum class Route(val kind: Kind, val implementation: String) {
        POST_EFFECT_COMPOSER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.createPostEffectDrawable(all modules)"),
        POST_EFFECT_SHAPE(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.createPostEffectDrawable(corner only)"),
        POST_EFFECT_OPTICS(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.createPostEffectDrawable(optics only)"),
        POST_EFFECT_STROKE(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.createPostEffectDrawable(stroke only)"),
        POST_EFFECT_INNER_SHADOW(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.createPostEffectDrawable(inner shadow only)"),
        POST_EFFECT_METABALL(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.createMetaBallPostEffectDrawable()"),

        COUI_MATERIAL_BLUR(Kind.DIRECT_EXECUTABLE, "ColorOsMaterialBridge.applyBlur() using installed com.oplus.uxdesign preset"),
        COUI_MATERIAL_STROKE(Kind.DIRECT_EXECUTABLE, "ColorOsMaterialBridge.applyStroke() using installed com.oplus.uxdesign preset"),
        COUI_SPOTLIGHT(Kind.DIRECT_EXECUTABLE, "ColorOsMaterialBridge.applySpotLight() using installed com.oplus.uxdesign preset"),
        COUI_TOOLBAR_STACK(Kind.DIRECT_EXECUTABLE, "ColorOsMaterialBridge.applyToolbarStack(blur+stroke+spotlight+caustic)"),
        COUI_PROGRESSIVE_BLUR(Kind.DIRECT_EXECUTABLE, "ColorOsMaterialBridge.applyGradientBlur()/updateGradientBlur()"),
        KEYGUARD_GLASS_BUILDER(Kind.DIRECT_EXECUTABLE, "ColorOsClockGlassSurfaceView -> personality-clocks GlassEffectBuilder -> real RenderEffect"),

        CHROMATIC_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiPostEffectBridge.applyChromatic()"),
        BAR_GLOW_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.applyBarGlow()"),
        RAW_METABALL_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.createRawMetaballShader()"),
        QS_STROKE_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.createQsStrokeShader()"),
        VOLUME_STROKE_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.createVolumeStrokeShader()"),
        NOTIFICATION_STROKE_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsNotificationStrokeBridge.create()"),
        WALLPAPER_BLUR_DRAWABLE(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiExecutableBridge.createWallpaperBlurDrawable()"),
        QS_PROGRESSIVE_BLUR_VIEW(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createQsProgressiveBlur()"),
        NOTIFICATION_TILT_SHIFT_VIEW(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createNotificationTiltShift()"),
        KEYGUARD_GRADIENT_BLUR_VIEW(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createKeyguardGradientBlur()"),
        QS_MULTI_LIGHT_SHADER(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createQsMultiLightShader()"),
        QS_BUSINESS_SEEKBAR(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createQsVerticalSeekBar()"),
        VOLUME_BUSINESS_SEEKBAR(Kind.DIRECT_EXECUTABLE, "ColorOsSystemUiDirectViewBridge.createVolumeSeekBar()"),
        SYSTEMUI_GL_BLUR(Kind.GL_PIPELINE, "ColorOsSystemUiGlBlurView: blur_down -> gaussian X/Y -> blur_up -> display"),
        SHIPPING_PRESET_BROWSER(Kind.PARAMETER_EXECUTOR, "ColorOsSystemUiPresetBridge -> exact shipping params -> BlendDrawable"),
        BLUR_MIX_RECIPE_EXECUTOR(Kind.PARAMETER_EXECUTOR, "ColorOsSystemUiBlurMixBridge + ShaderBlendParamHelper -> BlendDrawable"),
        MATERIAL_PARAMETER_AUDIT(Kind.PARAMETER_EXECUTOR, "Runtime reflection/resource inspection of installed vendor material implementation"),
        SYSTEMUI_SHADER_RESOURCE_AUDIT(Kind.PARAMETER_EXECUTOR, "ColorOsSystemUiLiquidGlassCatalog shader scan; unknown uniforms remain non-executable"),
        SYSTEMUI_HOST(Kind.HOST_BOUND, "SystemUI business/controller host required; Kyant parity remains executable"),
        SURFACE_CONTROL_HOST(Kind.SURFACE_CONTROL_BOUND, "Vendor SurfaceControl/live-backdrop transport required"),
    }

    fun resolve(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
        effectiveExecution: ColorOsSystemUiLiquidGlassCatalog.ExecutionMode,
    ): Route? {
        val impl = mapping.systemUiImplementation
        val lower = impl.lowercase()

        if (impl.startsWith("assets/") || impl.startsWith("res/raw/")) {
            return when {
                "chromatic" in lower -> Route.CHROMATIC_SHADER
                "barglow" in lower -> Route.BAR_GLOW_SHADER
                "metaball" in lower -> Route.RAW_METABALL_SHADER
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.GL_PIPELINE &&
                    ("blur_down" in lower || "blur_up" in lower || "gaussian" in lower || "display_" in lower) -> Route.SYSTEMUI_GL_BLUR
                else -> Route.SYSTEMUI_SHADER_RESOURCE_AUDIT
            }
        }

        when {
            impl.startsWith(ColorOsCouiPresetInventory.BLUR_PREFIX) -> return Route.COUI_MATERIAL_BLUR
            impl.startsWith(ColorOsCouiPresetInventory.STROKE_PREFIX) -> return Route.COUI_MATERIAL_STROKE
            impl.startsWith(ColorOsCouiPresetInventory.SPOTLIGHT_PREFIX) -> return Route.COUI_SPOTLIGHT
            impl.startsWith(ColorOsCouiPresetInventory.TOOLBAR_PREFIX) -> return Route.COUI_TOOLBAR_STACK
            impl.startsWith(ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX) -> return Route.SHIPPING_PRESET_BROWSER
            impl.startsWith(ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX) -> {
                return if (effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST) {
                    Route.SYSTEMUI_HOST
                } else {
                    Route.BLUR_MIX_RECIPE_EXECUTOR
                }
            }
        }

        when (impl) {
            "com.coui.appcompat.COUIMaterialBlurEffect" -> return Route.COUI_MATERIAL_BLUR
            "com.coui.appcompat.COUIMaterialStrokeEffect" -> return Route.COUI_MATERIAL_STROKE
            "com.coui.appcompat.spotlight.COUISpotLightEffect" -> return Route.COUI_SPOTLIGHT
            "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate" -> return Route.COUI_TOOLBAR_STACK
            "com.coui.appcompat.toolbar.AppBarBlurHelper" -> return Route.COUI_PROGRESSIVE_BLUR
            "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder" -> return Route.KEYGUARD_GLASS_BUILDER
            "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView" -> return Route.KEYGUARD_GRADIENT_BLUR_VIEW
            "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar" -> return Route.QS_BUSINESS_SEEKBAR
            "com.oplus.systemui.volume.OplusVolumeSeekBar" -> return Route.VOLUME_BUSINESS_SEEKBAR
            "com.oplus.systemui.qs.media.ProgressiveBlurOverlay" -> return Route.QS_PROGRESSIVE_BLUR_VIEW
            "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer" -> return Route.NOTIFICATION_TILT_SHIFT_VIEW
            "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams" -> return Route.QS_MULTI_LIGHT_SHADER
        }

        if (impl.startsWith("com.oplusos.systemui.common.adapter.MixColor")) return Route.SHIPPING_PRESET_BROWSER
        if (impl == "com.oplusos.systemui.common.util.QSBlurConfigProvider") return Route.BLUR_MIX_RECIPE_EXECUTOR
        if (impl == "com.oplusos.systemui.common.util.ShaderBlendParamHelper") return Route.BLUR_MIX_RECIPE_EXECUTOR

        if (impl.startsWith("com.oplus.posteffect.")) {
            return when {
                "continuousblurdrawable" in lower || "metaballblurdrawable" in lower -> Route.SURFACE_CONTROL_HOST
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> Route.MATERIAL_PARAMETER_AUDIT
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST -> Route.SYSTEMUI_HOST
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL -> Route.SURFACE_CONTROL_HOST
                lower.endsWith("cornerparams") -> Route.POST_EFFECT_SHAPE
                lower.endsWith("opticsparams") -> Route.POST_EFFECT_OPTICS
                lower.endsWith("gradientstrokelineparams") -> Route.POST_EFFECT_STROKE
                lower.endsWith("innershadowparams") -> Route.POST_EFFECT_INNER_SHADOW
                "metaballparams" in lower -> Route.POST_EFFECT_METABALL
                else -> Route.POST_EFFECT_COMPOSER
            }
        }

        if (impl.startsWith("com.oplusos.systemui.common.blurability.")) {
            return when (effectiveExecution) {
                ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL -> Route.SURFACE_CONTROL_HOST
                ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> {
                    if ("mix" in lower || "blurconfig" in lower) Route.BLUR_MIX_RECIPE_EXECUTOR else Route.MATERIAL_PARAMETER_AUDIT
                }
                else -> Route.SYSTEMUI_HOST
            }
        }

        if (".notification." in impl) {
            return when {
                "capsule.stroke.strokeshader" in lower -> Route.NOTIFICATION_STROKE_SHADER
                "gradientstrokelineadapter" in lower || "innershadowadapter" in lower -> Route.SHIPPING_PRESET_BROWSER
                "blurmixcolorparams" in lower || "platformblurparamsmanager" in lower -> Route.BLUR_MIX_RECIPE_EXECUTOR
                "metaball" in lower && effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL -> Route.SURFACE_CONTROL_HOST
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> Route.MATERIAL_PARAMETER_AUDIT
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW -> Route.NOTIFICATION_TILT_SHIFT_VIEW
                else -> Route.SYSTEMUI_HOST
            }
        }

        if (".qs." in impl) {
            return when {
                "gradientstrokeshader" in lower -> Route.QS_STROKE_SHADER
                "progressivebluroverlay" in lower -> Route.QS_PROGRESSIVE_BLUR_VIEW
                "multilightshaderparams" in lower -> Route.QS_MULTI_LIGHT_SHADER
                "mixcolortile" in lower -> Route.SHIPPING_PRESET_BROWSER
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> Route.MATERIAL_PARAMETER_AUDIT
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW -> Route.QS_BUSINESS_SEEKBAR
                else -> Route.SYSTEMUI_HOST
            }
        }

        if (".volume." in impl) {
            return when {
                "volumegradientstrokeshader" in lower -> Route.VOLUME_STROKE_SHADER
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> Route.MATERIAL_PARAMETER_AUDIT
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW -> Route.VOLUME_BUSINESS_SEEKBAR
                else -> Route.SYSTEMUI_HOST
            }
        }

        if (".wallpaperblur." in impl) {
            return when {
                "wallpaperblurdrawable" in lower -> Route.WALLPAPER_BLUR_DRAWABLE
                effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> Route.MATERIAL_PARAMETER_AUDIT
                else -> Route.SYSTEMUI_HOST
            }
        }
        if (".biometrics.material." in impl) {
            return if (effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY) Route.MATERIAL_PARAMETER_AUDIT else Route.SYSTEMUI_HOST
        }
        if (".panelanimation.platformblur." in impl) {
            return if (effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY) Route.MATERIAL_PARAMETER_AUDIT else Route.SYSTEMUI_HOST
        }
        if (impl.startsWith("com.oplusos.systemui.common.shader.") && "metaball" in lower) {
            return if (effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY) Route.MATERIAL_PARAMETER_AUDIT else Route.SYSTEMUI_HOST
        }

        if (effectiveExecution == ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY) return Route.MATERIAL_PARAMETER_AUDIT
        return when (effectiveExecution) {
            ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST -> Route.SYSTEMUI_HOST
            ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL -> Route.SURFACE_CONTROL_HOST
            else -> null
        }
    }

    fun routeIsCompatible(
        route: Route,
        effectiveExecution: ColorOsSystemUiLiquidGlassCatalog.ExecutionMode,
    ): Boolean = when (effectiveExecution) {
        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW ->
            route.kind == Kind.DIRECT_EXECUTABLE || route.kind == Kind.PARAMETER_EXECUTOR
        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.GL_PIPELINE -> route.kind == Kind.GL_PIPELINE
        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY -> route.kind == Kind.PARAMETER_EXECUTOR
        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SYSTEM_UI_HOST -> route.kind == Kind.HOST_BOUND
        ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.SURFACE_CONTROL -> route.kind == Kind.SURFACE_CONTROL_BOUND
    }
}
