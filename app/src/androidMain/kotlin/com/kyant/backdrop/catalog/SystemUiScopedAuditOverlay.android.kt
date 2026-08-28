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
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog

/**
 * Two-layer view of the high-recall SystemUI scan. Only CORE_MATERIAL participates in the
 * Liquid-Glass coverage gate; ADJACENT_GRAPHICS remains visible for auditability.
 */
@Composable
fun SystemUiScopedAuditOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    var showAdjacent by rememberSaveable { mutableStateOf(false) }

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
    val catalog = remember(context) { ColorOsSystemUiLiquidGlassCatalog(context) }
    val mappings = remember(catalog) { catalog.mappings() }
    val classified = remember(mappings) { ColorOsSystemUiAuditScope.classifyAll(mappings) }
    val summary = remember(mappings) { ColorOsSystemUiAuditScope.summary(mappings) }
    val core = remember(classified) {
        classified.filter { it.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL }
    }
    val adjacent = remember(classified) {
        classified.filter { it.scope == ColorOsSystemUiAuditScope.Scope.ADJACENT_GRAPHICS }
    }

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
                BasicText(
                    "SystemUI 核心材质 / 相邻图形",
                    style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold),
                )
                ScopeButton("关闭") { open = false }
            }

            BasicText(
                "运行时仍对 SystemUI base/split DEX 和 shader 资源保持高召回扫描，但覆盖率只针对已经能接到 ColorOS 材质调用链的 CORE_MATERIAL。相邻 AOSP ripple、通用 shadow/gradient/shader 不被删除，只是不再拿它们冒充 Liquid Glass 子系统。",
                style = scopeInfoStyle(),
            )

            CoreGateCard(summary)

            ScopeTitle("CORE_MATERIAL · ${core.size}")
            BasicText(
                "这一层必须全部获得 Kyant 机制映射；能在普通 View/GL 中安全运行的项还会使用 effectiveExecution 下沉为 DIRECT_VIEW/GL_PIPELINE。",
                style = scopeInfoStyle(),
            )
            core.groupBy { it.mapping.group }.forEach { (group, rows) ->
                BasicText(group, style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                rows.forEach { ScopedMappingCard(it) }
            }

            ScopeButton(if (showAdjacent) "收起相邻图形" else "展开相邻图形 · ${adjacent.size}") {
                showAdjacent = !showAdjacent
            }

            if (showAdjacent) {
                ScopeTitle("ADJACENT_GRAPHICS · ${adjacent.size}")
                BasicText(
                    "这些命中用于发现潜在线索，但在没有 ColorOS 材质调用链证据前不进入核心覆盖率，也不会因为名称里有 Shader/Shadow/Gradient 就自动被视作 Liquid Glass。",
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
                "CORE PASS · ${summary.coreCoveragePercent}%"
            } else {
                "CORE FAIL · ${summary.coreUnmapped} 项未映射"
            },
            style = TextStyle(color, 15.sp, FontWeight.SemiBold),
        )
        BasicText(
            "全扫描 ${summary.total} · 核心 ${summary.core} · 相邻 ${summary.adjacent}",
            style = scopeDiagnosticsStyle(),
        )
        BasicText(
            "核心 mapped ${summary.coreMapped} · available ${summary.coreAvailable} · direct ${summary.coreDirect} · host-bound ${summary.coreHostBound}",
            style = scopeDiagnosticsStyle(),
        )
    }
}

@Composable
private fun ScopedMappingCard(item: ColorOsSystemUiAuditScope.Classified) {
    val row = item.mapping
    val effective = ColorOsSystemUiAuditScope.effectiveExecution(row)
    val available = row.status.startsWith("available")
    val statusColor = if (available) Color(0xFF9DE7AA) else Color(0xFFFFB4A9)
    val scopeColor = if (item.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL) {
        Color(0xFF9CCBFF)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

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
        BasicText("↔ Kyant：${row.kyantCounterpart}", style = scopeDiagnosticsStyle())
        BasicText(
            if (effective == row.executionMode) {
                "$effective · ${row.status}"
            } else {
                "$effective ← 原审计 ${row.executionMode} · ${row.status}"
            },
            style = TextStyle(statusColor, 9.sp),
        )
        BasicText(row.note, style = scopeDiagnosticsStyle())
    }
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

private fun scopeInfoStyle() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun scopeDiagnosticsStyle() = TextStyle(Color.White.copy(alpha = 0.67f), 9.sp)
