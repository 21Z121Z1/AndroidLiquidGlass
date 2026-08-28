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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiAuditScope
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiCompleteInventory
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiExecutionRegistry

/**
 * Runtime audit for the complete SystemUI material set. A core item only passes when semantic
 * mapping, concrete Kyant contract, compatible ColorOS route and explicit delta all exist.
 */
@Composable
fun SystemUiScopedAuditOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    var showAdjacent by rememberSaveable { mutableStateOf(false) }
    var failuresOnly by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            ScopeButton("SYSUI 分层审计") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val inventory = remember(context) { ColorOsSystemUiCompleteInventory(context) }
    val mappings = remember(inventory) { inventory.mappings() }
    val classified = remember(mappings) { ColorOsSystemUiAuditScope.classifyAll(mappings) }
    val summary = remember(mappings) { ColorOsSystemUiAuditScope.summary(mappings) }
    val core = remember(classified) { classified.filter { it.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL } }
    val adjacent = remember(classified) { classified.filter { it.scope == ColorOsSystemUiAuditScope.Scope.ADJACENT_GRAPHICS } }
    val failedCore = remember(core) { core.filter(::isAuditFailure) }
    val visibleCore = if (failuresOnly) failedCore else core

    Box(Modifier.fillMaxSize().background(Color(0xFF080A0F))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("SystemUI Liquid Glass 严格审计", style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold))
                ScopeButton("关闭") { open = false }
            }

            BasicText(
                "严格清单由 SystemUI base/split DEX、shader 资源、已证明的业务 View，以及 SystemUI 实际调用的 Oplus 框架材质原语共同组成。CORE_MATERIAL 必须同时满足：语义映射、Kyant 强类型 recipe、兼容的 ColorOS 执行路由、以及明确的差异声明。未知 GLSL 不会因为扩展名就被当成已执行 GL 管线。",
                style = scopeInfoStyle(),
            )

            CoreGateCard(summary)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeButton(if (failuresOnly) "显示全部核心" else "只看失败 · ${failedCore.size}") {
                    failuresOnly = !failuresOnly
                }
                ScopeButton(if (showAdjacent) "隐藏相邻" else "相邻 · ${adjacent.size}") {
                    showAdjacent = !showAdjacent
                }
            }

            ScopeTitle(
                if (failuresOnly) "CORE FAILURES · ${failedCore.size}"
                else "CORE_MATERIAL · ${core.size}",
            )
            if (visibleCore.isEmpty()) {
                BasicText(
                    if (failuresOnly) "没有结构化覆盖失败项。真机直接执行结果仍需逐项观察 PASS/UNAVAILABLE。" else "没有核心材质项。",
                    style = TextStyle(Color(0xFF9DE7AA), 11.sp, FontWeight.Medium),
                )
            } else {
                visibleCore.groupBy { it.mapping.group }.forEach { (group, rows) ->
                    BasicText(group, style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                    rows.forEach { ScopedMappingCard(it) }
                }
            }

            if (showAdjacent) {
                ScopeTitle("ADJACENT_GRAPHICS · ${adjacent.size}")
                BasicText(
                    "这些命中只用于发现线索。没有 ColorOS 材质调用链证据时，不进入 Liquid Glass 覆盖率，也不会为了数字好看被强行映射。",
                    style = scopeInfoStyle(),
                )
                adjacent.groupBy { it.mapping.group }.forEach { (group, rows) ->
                    BasicText(group, style = TextStyle(Color.White.copy(alpha = 0.82f), 12.sp, FontWeight.Medium))
                    rows.forEach { ScopedMappingCard(it) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CoreGateCard(summary: ColorOsSystemUiAuditScope.ScopedSummary) {
    val color = if (summary.coreComplete) Color(0xFF8EE6A2) else Color(0xFFFF8A80)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            if (summary.coreComplete) {
                "CORE PASS · ${summary.coreCoveragePercent}% · semantic + Kyant + ColorOS route + delta"
            } else {
                "CORE FAIL · semantic ${summary.coreUnmapped} · contract ${summary.coreContractMissing} · route ${summary.coreRouteMissing} · incompatible ${summary.coreRouteIncompatible} · delta ${summary.coreDeltaMissing}"
            },
            style = TextStyle(color, 15.sp, FontWeight.SemiBold),
        )
        BasicText("全扫描 ${summary.total} · 核心 ${summary.core} · 相邻 ${summary.adjacent}", style = scopeDiagnosticsStyle())
        BasicText(
            "mapped ${summary.coreMapped}/${summary.core} · contract ${summary.coreContracted}/${summary.core} · route ${summary.coreRouted}/${summary.core} · delta ${summary.coreDeltaResolved}/${summary.core}",
            style = scopeDiagnosticsStyle(),
        )
        BasicText(
            "available ${summary.coreAvailable} · direct/GL ${summary.coreDirect} · host-bound ${summary.coreHostBound}",
            style = scopeDiagnosticsStyle(),
        )
        BasicText(
            "Kyant: mechanism ${summary.parityMechanism} · composite ${summary.parityComposite} · nearest ${summary.parityNearestOnly} · host ${summary.parityHostLifecycle}",
            style = scopeDiagnosticsStyle(),
        )
        BasicText(
            "Delta: exact ${summary.deltaExactMechanism} · composite ${summary.deltaCompositeEquivalent} · nearest ${summary.deltaNearestOnly} · host ${summary.deltaHostOnly}",
            style = scopeDiagnosticsStyle(),
        )
        summary.missingContracts.forEach { BasicText("MISSING_CONTRACT · $it", style = failureStyle()) }
        summary.missingRoutes.forEach { BasicText("MISSING_ROUTE · $it", style = failureStyle()) }
        summary.incompatibleRoutes.forEach {
            BasicText("INCOMPATIBLE_ROUTE · $it", style = TextStyle(Color(0xFFFFB74D), 9.sp))
        }
        summary.missingDeltas.forEach { BasicText("MISSING_DELTA · $it", style = failureStyle()) }
    }
}

@Composable
private fun ScopedMappingCard(item: ColorOsSystemUiAuditScope.Classified) {
    val row = item.mapping
    val effective = ColorOsSystemUiAuditScope.effectiveExecution(row)
    val available = row.status.startsWith("available")
    val statusColor = if (available) Color(0xFF9DE7AA) else Color(0xFFFFB4A9)
    val scopeColor = if (item.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL) Color(0xFF9CCBFF)
    else Color.White.copy(alpha = 0.55f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(row.systemUiImplementation, style = TextStyle(Color.White, 10.sp, FontWeight.Medium))
        BasicText("${item.scope} · ${item.reason}", style = TextStyle(scopeColor, 9.sp))

        if (item.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL) {
            val route = item.executionRoute
            if (route == null) {
                BasicText("ColorOS route · MISSING_ROUTE", style = failureStyle())
            } else {
                val compatible = ColorOsSystemUiExecutionRegistry.routeIsCompatible(route, effective)
                BasicText(
                    "ColorOS route · ${route.kind} · ${route.name}${if (compatible) "" else " · INCOMPATIBLE"}",
                    style = TextStyle(if (compatible) Color(0xFF90CAF9) else Color(0xFFFFB74D), 9.sp, FontWeight.Medium),
                )
                BasicText(route.implementation, style = scopeDiagnosticsStyle())
            }

            val contract = item.parityContract
            if (contract == null) {
                BasicText("Kyant · MISSING_CONTRACT", style = failureStyle())
            } else {
                BasicText(
                    "Kyant · ${contract.kind} · ${contract.recipe}",
                    style = TextStyle(Color(0xFFC5E1A5), 9.sp, FontWeight.Medium),
                )
                BasicText(contract.apiSummary, style = scopeDiagnosticsStyle())
                BasicText(contract.rationale, style = scopeDiagnosticsStyle())
            }

            val delta = item.delta
            if (delta == null) {
                BasicText("Delta · MISSING_DELTA", style = failureStyle())
            } else {
                BasicText("Delta · ${delta.grade}", style = TextStyle(Color(0xFFFFD180), 9.sp, FontWeight.Medium))
                BasicText(delta.note, style = scopeDiagnosticsStyle())
                delta.colorOsSpecific.forEach { BasicText("ColorOS 特有 · $it", style = scopeDiagnosticsStyle()) }
                delta.kyantLimit.forEach { BasicText("Kyant 边界 · $it", style = scopeDiagnosticsStyle()) }
            }
        } else {
            BasicText("↔ Kyant（相邻语义）：${row.kyantCounterpart}", style = scopeDiagnosticsStyle())
        }

        BasicText(
            if (effective == row.executionMode) "$effective · ${row.status}"
            else "$effective ← 原审计 ${row.executionMode} · ${row.status}",
            style = TextStyle(statusColor, 9.sp),
        )
        BasicText(row.note, style = scopeDiagnosticsStyle())
    }
}

private fun isAuditFailure(item: ColorOsSystemUiAuditScope.Classified): Boolean {
    if (item.scope != ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL) return false
    val mapping = item.mapping
    val semanticMissing = mapping.kyantCounterpart.isBlank() || mapping.kyantCounterpart.startsWith("UNMAPPED", ignoreCase = true)
    val route = item.executionRoute
    val routeMissing = route == null
    val incompatible = route != null && !ColorOsSystemUiExecutionRegistry.routeIsCompatible(
        route,
        ColorOsSystemUiAuditScope.effectiveExecution(mapping),
    )
    return semanticMissing || item.parityContract == null || routeMissing || incompatible || item.delta == null
}

@Composable
private fun ScopeButton(text: String, onClick: () -> Unit) {
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
private fun ScopeTitle(text: String) {
    Spacer(Modifier.height(3.dp))
    BasicText(text, style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
}

private fun failureStyle() = TextStyle(Color(0xFFFF8A80), 9.sp, FontWeight.SemiBold)
private fun scopeInfoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun scopeDiagnosticsStyle() = TextStyle(Color.White.copy(alpha = 0.67f), 9.sp)
