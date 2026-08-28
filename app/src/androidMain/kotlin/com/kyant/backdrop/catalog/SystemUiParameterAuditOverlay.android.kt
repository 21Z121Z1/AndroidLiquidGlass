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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiLiquidGlassCatalog
import com.kyant.backdrop.catalog.coloros.ColorOsSystemUiParameterAuditBridge

/**
 * Live evidence page for every CORE_MATERIAL item whose SystemUI role is CAPABILITY_ONLY.
 * It reads the installed SystemUI class, not copied constants, and only invokes safe scalar getters.
 */
@Composable
fun SystemUiParameterAuditOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            ParamButton("SYSUI 参数") { open = true }
        }
        return
    }

    val context = LocalContext.current
    val inventory = remember(context) { ColorOsSystemUiCompleteInventory(context) }
    val bridge = remember(context) { ColorOsSystemUiParameterAuditBridge(context) }
    val rows = remember(inventory) {
        ColorOsSystemUiAuditScope.classifyAll(inventory.mappings())
            .filter { item ->
                item.scope == ColorOsSystemUiAuditScope.Scope.CORE_MATERIAL &&
                    ColorOsSystemUiAuditScope.effectiveExecution(item.mapping) ==
                    ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY
            }
    }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val selected = rows.getOrNull(safeIndex)
    val snapshot = remember(selected?.mapping?.systemUiImplementation) {
        selected?.let { bridge.inspect(it.mapping.systemUiImplementation) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF090B10))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(14.dp)
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("SystemUI 材质参数实证", style = TextStyle(Color.White, 21.sp, FontWeight.SemiBold))
                ParamButton("关闭") { open = false }
            }
            BasicText(
                "仅针对严格核心里的 CAPABILITY_ONLY 类。读取当前安装 SystemUI 的 enum/static final 值、无副作用标量 getter 与方法签名；不会调用任意控制器动作，也不会用 Demo 自制常量替换 shipping 参数。",
                style = paramInfo(),
            )
            BasicText("CAPABILITY_ONLY · ${rows.size}", style = TextStyle(Color(0xFF90CAF9), 13.sp, FontWeight.Medium))

            selected?.let { item ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.34f)).padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    BasicText(item.mapping.systemUiImplementation, style = TextStyle(Color.White, 11.sp, FontWeight.SemiBold))
                    BasicText("ColorOS route · ${item.executionRoute?.name ?: "MISSING_ROUTE"}", style = paramDiag())
                    BasicText("Kyant · ${item.parityContract?.kind ?: "MISSING"} · ${item.parityContract?.recipe ?: "MISSING"}", style = paramDiag())
                    item.parityContract?.let { BasicText(it.apiSummary, style = paramDiag()) }
                }

                when {
                    snapshot == null -> BasicText("No selection", style = paramDiag())
                    snapshot.isFailure -> BasicText(
                        "UNAVAILABLE · ${describeParamError(snapshot.exceptionOrNull())}",
                        style = TextStyle(Color(0xFFFFB4A9), 10.sp),
                    )
                    else -> {
                        val value = snapshot.getOrThrow()
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            BasicText("PASS · runtime evidence ${value.evidenceCount}", style = TextStyle(Color(0xFF9DE7AA), 11.sp, FontWeight.SemiBold))
                            BasicText("super=${value.superClass} · instance=${value.instanceSource ?: "none"}", style = paramDiag())
                            EvidenceSection("enum", value.enumConstants)
                            EvidenceSection("static constants", value.staticConstants)
                            EvidenceSection("safe getters", value.getterValues)
                            EvidenceSection("method signatures", value.methodSignatures)
                        }
                    }
                }
            }

            BasicText("所有参数/配置/适配层", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
            rows.forEachIndexed { index, item ->
                val selectedRow = index == safeIndex
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedRow) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.22f))
                        .clickable { selectedIndex = index }
                        .padding(8.dp),
                ) {
                    BasicText(item.mapping.systemUiImplementation, style = TextStyle(Color.White, 9.sp, FontWeight.Medium))
                    BasicText("${item.parityContract?.recipe ?: "MISSING_CONTRACT"} · ${item.executionRoute?.name ?: "MISSING_ROUTE"}", style = paramDiag())
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun EvidenceSection(title: String, values: List<String>) {
    if (values.isEmpty()) return
    BasicText("$title · ${values.size}", style = TextStyle(Color(0xFFB9E6C2), 10.sp, FontWeight.Medium))
    values.forEach { BasicText(it, style = paramDiag()) }
}

@Composable
private fun ParamButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.76f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 11.sp, FontWeight.SemiBold))
    }
}

private fun paramInfo() = TextStyle(Color.White.copy(alpha = 0.78f), 11.sp)
private fun paramDiag() = TextStyle(Color.White.copy(alpha = 0.67f), 9.sp)
private fun describeParamError(t: Throwable?): String {
    if (t == null) return "unknown"
    val root = generateSequence(t) { it.cause }.last()
    return "${root.javaClass.simpleName}:${root.message}"
}
