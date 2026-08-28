package com.kyant.backdrop.catalog

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.coloros.ColorOsKyantParityContract
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiAuditScope
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/**
 * Executable Kyant reference browser for every CORE_MATERIAL implementation discovered in the
 * installed SystemUI package. Existing ColorOS labs own direct vendor execution; this page makes
 * the Kyant side equally explicit and executable for every core row.
 */
@Composable
fun SystemUiKyantParityOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            ParityButton("SYSUI ↔ Kyant") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val catalog = remember(context) { ColorOsSystemUiLiquidGlassCatalog(context) }
    val mappings = remember(catalog) { catalog.mappings() }
    val core = remember(mappings) {
        ColorOsSystemUiAuditScope.classifyAll(mappings)
            .filter { it.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL }
    }
    val summary = remember(mappings) { ColorOsSystemUiAuditScope.summary(mappings) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var spotlightAngle by rememberSaveable { mutableFloatStateOf(45f) }
    val safeIndex = selectedIndex.coerceIn(0, (core.size - 1).coerceAtLeast(0))
    val selected = core.getOrNull(safeIndex)
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(Color(0xFF07090E))) {
        Box(
            Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF15366E),
                            Color(0xFF8A4DB2),
                            Color(0xFF10A8B8),
                            Color(0xFFF3A936),
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("SystemUI ↔ Kyant 全量对照", style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold))
                ParityButton("关闭") { open = false }
            }

            BasicText(
                "页面直接消费 CORE_MATERIAL 的强类型 parity contract。每个 SystemUI 核心项必须绑定到实际 Kyant API recipe；缺少契约的项既不能生成参考样本，也会让核心覆盖闸门失败。",
                style = parityInfo(),
            )

            ParityGateCard(summary)

            selected?.let { item ->
                val row = item.mapping
                val contract = item.parityContract
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.34f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(row.systemUiImplementation, style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold))
                    BasicText(
                        "ColorOS execution · ${ColorOsSystemUiAuditScope.effectiveExecution(row)} · ${row.status}",
                        style = parityDiag(),
                    )
                    if (contract == null) {
                        BasicText("MISSING_CONTRACT", style = TextStyle(Color(0xFFFF8A80), 11.sp, FontWeight.Bold))
                    } else {
                        BasicText(
                            "Kyant parity · ${contract.kind} · ${contract.recipe}",
                            style = TextStyle(Color(0xFFC5E1A5), 10.sp, FontWeight.Medium),
                        )
                        BasicText(contract.apiSummary, style = parityDiag())
                        BasicText(contract.rationale, style = parityDiag())
                    }
                }

                KyantRecipeSurface(contract, backdrop, spotlightAngle)

                if (contract?.recipe == ColorOsKyantParityContract.Recipe.SPOTLIGHT) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ParityButton("-30°") { spotlightAngle = -30f }
                        ParityButton("45°") { spotlightAngle = 45f }
                        ParityButton("120°") { spotlightAngle = 120f }
                    }
                    BasicText(
                        "Kyant 库没有 ColorOS COUISpotLightEffect 的 1:1 指针光照宿主；这里通过应用层状态驱动 HighlightStyle.Default(angle=...) 展示最近机制。",
                        style = parityDiag(),
                    )
                }
            }

            BasicText("所有 CORE_MATERIAL · ${core.size}", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
            core.forEachIndexed { index, item ->
                val row = item.mapping
                val contract = item.parityContract
                val selectedRow = index == safeIndex
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (selectedRow) Color.White.copy(alpha = 0.16f)
                            else Color.Black.copy(alpha = 0.24f),
                        )
                        .clickable { selectedIndex = index }
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BasicText(row.systemUiImplementation, style = TextStyle(Color.White, 9.sp, FontWeight.Medium))
                    if (contract == null) {
                        BasicText("MISSING_CONTRACT", style = TextStyle(Color(0xFFFF8A80), 9.sp, FontWeight.Bold))
                    } else {
                        BasicText("${contract.kind} · ${contract.recipe}", style = TextStyle(Color(0xFFB9E6C2), 9.sp))
                        BasicText(contract.primitives.joinToString(" + ") { it.name }, style = parityDiag())
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ParityGateCard(summary: ColorOsSystemUiAuditScope.ScopedSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val gateColor = if (summary.coreComplete) Color(0xFF93E7A6) else Color(0xFFFF8A80)
        BasicText(
            if (summary.coreComplete) "PARITY GATE PASS · ${summary.coreContracted}/${summary.core}" else
                "PARITY GATE FAIL · missing ${summary.coreContractMissing}",
            style = TextStyle(gateColor, 15.sp, FontWeight.SemiBold),
        )
        BasicText(
            "mechanism ${summary.parityMechanism} · composite ${summary.parityComposite} · nearest ${summary.parityNearestOnly} · host-lifecycle ${summary.parityHostLifecycle}",
            style = parityDiag(),
        )
        BasicText("direct ${summary.coreDirect} · host-bound ${summary.coreHostBound}", style = parityDiag())
        summary.missingContracts.forEach {
            BasicText("MISSING_CONTRACT · $it", style = TextStyle(Color(0xFFFF8A80), 9.sp))
        }
    }
}

@Composable
private fun KyantRecipeSurface(
    contract: ColorOsKyantParityContract.Contract?,
    backdrop: Backdrop,
    spotlightAngle: Float,
) {
    if (contract == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("No Kyant contract", style = TextStyle(Color(0xFFFF8A80), 14.sp, FontWeight.SemiBold))
        }
        return
    }

    val shape = { RoundedRectangle(34.dp) }
    val base = Modifier.fillMaxWidth().height(160.dp)

    when (contract.recipe) {
        ColorOsKyantParityContract.Recipe.BACKDROP_BLUR,
        ColorOsKyantParityContract.Recipe.BACKDROP_HOST -> Box(
            base.drawPlainBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = { blur(22.dp.toPx()) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.BLUR_COLOR_MIX -> Box(
            base.drawPlainBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    blur(22.dp.toPx())
                    vibrancy()
                    colorControls(brightness = 0.02f, contrast = 1.04f, saturation = 1.08f)
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.REFRACTION -> Box(
            base.drawPlainBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    lens(24.dp.toPx(), 42.dp.toPx(), depthEffect = true, chromaticAberration = false)
                    blur(2.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.035f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.CHROMATIC_REFRACTION -> Box(
            base.drawPlainBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = { lens(24.dp.toPx(), 42.dp.toPx(), depthEffect = true, chromaticAberration = true) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.025f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.INNER_SHADOW -> Box(
            base.drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = { blur(8.dp.toPx()) },
                highlight = null,
                shadow = null,
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.07f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.SPOTLIGHT -> {
            val highlight = Highlight(
                width = 1.dp,
                alpha = 0.90f,
                style = HighlightStyle.Default(angle = spotlightAngle, falloff = 0.75f),
            )
            Box(
                base.drawBackdrop(
                    backdrop = backdrop,
                    shape = shape,
                    effects = { blur(8.dp.toPx()) },
                    highlight = { highlight },
                    shadow = { Shadow.Default },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f)) },
                ),
                contentAlignment = Alignment.Center,
            ) { RecipeLabel(contract) }
        }

        ColorOsKyantParityContract.Recipe.STROKE,
        ColorOsKyantParityContract.Recipe.EDGE_OPTICS -> Box(
            base.drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = { blur(5.dp.toPx()) },
                highlight = { Highlight.Default },
                shadow = null,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.04f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.METABALL_NEAREST -> Box(
            base.drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = { blur(7.dp.toPx()) },
                highlight = { Highlight.Ambient },
                shadow = { Shadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                Box(Modifier.width(92.dp).height(72.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)))
                Box(Modifier.width(92.dp).height(72.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)))
            }
            RecipeLabel(contract)
        }

        ColorOsKyantParityContract.Recipe.SHAPE -> Box(
            base.clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.RUNTIME_EFFECT_GRAPH,
        ColorOsKyantParityContract.Recipe.MATERIAL_SURFACE -> Box(
            base.drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    blur(14.dp.toPx())
                    vibrancy()
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.075f)) },
            ),
            contentAlignment = Alignment.Center,
        ) { RecipeLabel(contract) }

        ColorOsKyantParityContract.Recipe.PROGRESSIVE_BLUR -> Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(3f, 10f, 24f).forEach { radius ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(18.dp) },
                            effects = { blur(radius.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.05f)) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Kyant blur · ${radius.toInt()}dp", style = TextStyle(Color.White, 11.sp, FontWeight.Medium))
                }
            }
        }
    }
}

@Composable
private fun RecipeLabel(contract: ColorOsKyantParityContract.Contract) {
    BasicText("Kyant · ${contract.recipe}", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
}

@Composable
private fun ParityButton(text: String, onClick: () -> Unit) {
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

private fun parityInfo() = TextStyle(Color.White.copy(alpha = 0.79f), 11.sp)
private fun parityDiag() = TextStyle(Color.White.copy(alpha = 0.68f), 9.sp)
