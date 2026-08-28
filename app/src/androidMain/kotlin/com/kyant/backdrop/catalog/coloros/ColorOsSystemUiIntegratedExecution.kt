package com.kyant.backdrop.catalog.coloros

/**
 * Shipping material helpers that are not meaningful standalone Views, but are proven to execute
 * inside a real SystemUI consumer that the demo can instantiate and draw.
 *
 * This is deliberately different from pretending the helper itself is DIRECT_VIEW. The strict row
 * keeps the helper class as its implementation identity, while the note and route record the exact
 * shipping consumer that exercises it.
 */
internal object ColorOsSystemUiIntegratedExecution {
    data class Binding(
        val implementation: String,
        val consumer: String,
        val route: ColorOsSystemUiExecutionRegistry.Route,
        val evidence: String,
    )

    private const val QS_CONSUMER = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar"
    private const val VOLUME_CONSUMER = "com.oplus.systemui.volume.OplusVolumeSeekBar"

    private val bindings = listOf(
        Binding(
            implementation = "com.oplus.systemui.qs.base.util.QsSeekBarBlurManager",
            consumer = QS_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.QS_BUSINESS_SEEKBAR,
            evidence = "OplusQsVerticalSeekBar.onDraw() -> QsSeekBarBlurManager.getSeekBarBackground()/drawActiveMixColorTrack()",
        ),
        Binding(
            implementation = "com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect",
            consumer = QS_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.QS_BUSINESS_SEEKBAR,
            evidence = "OplusQsVerticalSeekBar is a shipping SharedSpotLightEffect consumer; its own draw/touch path exercises the shared session",
        ),
        Binding(
            implementation = "com.oplus.systemui.volume.utils.material.OplusVolumeBarMaterialHost",
            consumer = VOLUME_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.VOLUME_BUSINESS_SEEKBAR,
            evidence = "OplusVolumeSeekBar constructor/draw chain owns OplusVolumeBarMaterialHost and calls drawEdgeStroke()",
        ),
        Binding(
            implementation = "com.oplus.systemui.volume.utils.material.OplusVolumeStrokeRenderer",
            consumer = VOLUME_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.VOLUME_BUSINESS_SEEKBAR,
            evidence = "OplusVolumeBarMaterialHost creates OplusVolumeStrokeRenderer; volume seekbar drawing reaches the renderer",
        ),
        Binding(
            implementation = "com.oplus.systemui.volume.utils.material.OplusVolumeStrokeShaderHost",
            consumer = VOLUME_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.VOLUME_BUSINESS_SEEKBAR,
            evidence = "OplusVolumeStrokeRenderer -> OplusVolumeStrokeShaderHost.draw() inside the shipping volume material chain",
        ),
        Binding(
            implementation = "com.oplus.systemui.volume.utils.spotlight.OplusVolumeSeekBarSpotLightHelper",
            consumer = VOLUME_CONSUMER,
            route = ColorOsSystemUiExecutionRegistry.Route.VOLUME_BUSINESS_SEEKBAR,
            evidence = "OplusVolumeSeekBar.onDraw()/touch lifecycle invokes its shipping spotlight helper",
        ),
    )

    private val byImplementation = bindings.associateBy { it.implementation }

    fun bindingFor(implementation: String): Binding? = byImplementation[implementation]

    fun routeFor(implementation: String): ColorOsSystemUiExecutionRegistry.Route? =
        bindingFor(implementation)?.route

    fun promote(
        mapping: ColorOsSystemUiLiquidGlassCatalog.Mapping,
    ): ColorOsSystemUiLiquidGlassCatalog.Mapping {
        val binding = bindingFor(mapping.systemUiImplementation) ?: return mapping
        return mapping.copy(
            executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW,
            status = if (mapping.status.startsWith("available")) {
                "available:direct-integrated"
            } else {
                mapping.status
            },
            note = buildString {
                append(mapping.note)
                if (isNotEmpty()) append(" ")
                append("DIRECT_INTEGRATED via ")
                append(binding.consumer)
                append(": ")
                append(binding.evidence)
                append(". helper 本身不伪装成独立 View。")
            },
        )
    }

    fun allBindings(): List<Binding> = bindings
}
