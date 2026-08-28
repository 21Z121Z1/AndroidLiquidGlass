package com.kyant.backdrop.catalog.coloros

import android.content.Context

/** Runtime expansion for directly executable interactive SystemUI material recipes. */
internal class ColorOsSystemUiInteractiveRecipeInventory(context: Context) {
    companion object {
        const val PREFIX = "interactive://systemui/"
        const val NOTIFICATION_SPOTLIGHT_PREFIX = "${PREFIX}notification-spotlight/"
        const val QS_MEDIA_SPOTLIGHT_PREFIX = "${PREFIX}qs-media-spotlight/"
        const val VOLUME_SETTINGS_SPOTLIGHT = "${PREFIX}volume-settings-spotlight/default"
        const val SCENARIO_METABALL_LIGHT = "${PREFIX}scenario-metaball-light/default"
    }

    private val bridgeResult = runCatching {
        ColorOsSystemUiInteractiveEffectBridge(context.applicationContext)
    }

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = bridgeResult.fold(
        onSuccess = { bridge ->
            buildList {
                bridge.notificationSpotLightKinds().forEach { kind ->
                    add(
                        mapping(
                            group = "SystemUI interactive shipping recipe · Notification Spotlight",
                            implementation = "$NOTIFICATION_SPOTLIGHT_PREFIX$kind",
                            kyant = "InteractiveHighlight nearest mechanism",
                            note = "NotificationSpotLightParamsKind.$kind → NotificationSpotLightDelegate.markLazyParamsKind/draw/onTouchEvent；执行当前 SystemUI 自己的 spotlight drawable 与按压状态机。",
                        ),
                    )
                }
                bridge.qsMediaClipShapes().forEach { shape ->
                    add(
                        mapping(
                            group = "SystemUI interactive shipping recipe · QS Media Spotlight",
                            implementation = "$QS_MEDIA_SPOTLIGHT_PREFIX$shape",
                            kyant = "InteractiveHighlight nearest mechanism",
                            note = "QsMediaSpotLightHelper.ClipShape.$shape；直接构造 helper 并执行 ensureDrawable/updateDrawableBounds/drawSpotLightEffect/handleMotionEvent。",
                        ),
                    )
                }
                add(
                    mapping(
                        group = "SystemUI interactive shipping recipe · Volume Spotlight",
                        implementation = VOLUME_SETTINGS_SPOTLIGHT,
                        kyant = "InteractiveHighlight nearest mechanism",
                        note = "OplusVolumeSettingsButtonSpotLightHelper 的真实普通 View/Canvas 路径；保留 shipping drawable、clip path 与 MotionEvent 状态。",
                    ),
                )
                add(
                    mapping(
                        group = "SystemUI interactive shipping recipe · Metaball Light",
                        implementation = SCENARIO_METABALL_LIGHT,
                        kyant = "Metaball nearest: Shape/SDF + Highlight",
                        note = "ScenarioLightBackgroundDrawable → shipping MetaballLightConfig → MetaballLightRenderer；Demo 不复制 shader/纹理或手写 light config。",
                    ),
                )
            }.distinctBy { it.systemUiImplementation }
        },
        onFailure = { emptyList() },
    )

    private fun mapping(
        group: String,
        implementation: String,
        kyant: String,
        note: String,
    ) = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = group,
        systemUiImplementation = implementation,
        kyantCounterpart = kyant,
        executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.DIRECT_VIEW,
        status = "available:runtime-systemui-interactive-recipe",
        note = note,
    )
}
