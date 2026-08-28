package com.kyant.backdrop.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.catalog.coloros.ColorOsClockGlassBridge
import com.kyant.backdrop.catalog.coloros.ColorOsMaterialBridge
import kotlin.math.roundToInt

/**
 * Semantic ColorOS mapping used by the Android catalog.
 *
 * Important distinction:
 * - Generic COUI controls use the matching COUIMaterial* / Toolbar / AppBar
 *   family and the closest named preset recovered from com.oplus.uxdesign.
 * - GlassEffectBuilder is reserved for scenes that actually exercise the
 *   ColorOS lock-screen refractive glass path (lock screen and the dedicated
 *   adaptive/refraction sample). It is no longer the default backend for every
 *   rounded rectangle in the demo.
 */
@Composable
actual fun SemanticColorOsCatalogContent(
    destination: CatalogDestination,
    onNavigate: (CatalogDestination) -> Unit,
): Boolean {
    if (destination == CatalogDestination.Home) return false

    when (destination) {
        CatalogDestination.Home -> Unit
        CatalogDestination.Buttons -> SemanticButtonsDemo()
        CatalogDestination.Toggle -> SemanticToggleDemo()
        CatalogDestination.Slider -> SemanticSliderDemo()
        CatalogDestination.BottomTabs -> SemanticBottomTabsDemo()
        CatalogDestination.Dialog -> SemanticDialogDemo()
        CatalogDestination.LockScreen -> SemanticLockScreenDemo()
        CatalogDestination.ControlCenter -> SemanticControlCenterDemo()
        CatalogDestination.Magnifier -> SemanticMagnifierDemo()
        CatalogDestination.GlassPlayground -> SemanticPlaygroundDemo()
        CatalogDestination.ColorOsNativeComparison -> SemanticDiagnosticsDemo()
        CatalogDestination.AdaptiveLuminanceGlass -> SemanticAdaptiveDemo()
        CatalogDestination.ProgressiveBlur -> SemanticProgressiveBlurDemo()
        CatalogDestination.ScrollContainer -> SemanticScrollDemo(lazyStyle = false)
        CatalogDestination.LazyScrollContainer -> SemanticScrollDemo(lazyStyle = true)
    }
    return true
}

private enum class SemanticShape { Capsule, Circle, RoundRect, Bar, Rect }

private data class SemanticMaterialSpec(
    val label: String,
    val blur: String? = null,
    val stroke: String? = null,
    val spotLight: String? = null,
    val toolbarCategory: String? = null,
    val gradientBlur: Boolean = false,
)

/**
 * Mappings are intentionally based on vendor type names rather than visual
 * imitation. Where SystemUI does not expose an exact public semantic type, the
 * closest size/opacity family from the recovered catalog is used and named as
 * such in the page copy.
 */
private object SemanticSpecs {
    val TransparentButton = SemanticMaterialSpec(
        label = "transparent button",
        blur = "TYPE_CONTENT_TRANSPARENT_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_TRANSPARENT_SMALL_1",
    )
    val SecondaryButton = SemanticMaterialSpec(
        label = "secondary button",
        blur = "TYPE_CONTENT_SECONDARY_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_TRANSLUCENT_SMALL_1",
    )
    val SegmentButton = SemanticMaterialSpec(
        label = "segment button",
        blur = "TYPE_CONTENT_SEGMENT_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_SEGMENT_BUTTON",
    )
    val Chip = SemanticMaterialSpec(
        label = "chip tab",
        blur = "TYPE_CONTENT_CHIP_TAB",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_CHIP_TAB",
    )
    val ChipSelected = SemanticMaterialSpec(
        label = "selected chip tab",
        blur = "TYPE_CONTENT_CHIP_TAB_SELECTED",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_CHIP_TAB",
    )
    val Switch = SemanticMaterialSpec(
        label = "switch",
        blur = "TYPE_CONTENT_TRANSPARENT_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_2",
        spotLight = "TYPE_SWITCH",
    )
    val SeekBar = SemanticMaterialSpec(
        label = "seekbar",
        blur = "TYPE_CONTENT_SEGMENT_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_2",
        spotLight = "TYPE_SEEKBAR",
    )
    val CircleControl = SemanticMaterialSpec(
        label = "circle control",
        blur = "TYPE_CONTENT_TRANSPARENT_BUTTON",
        stroke = "TYPE_FRAMEWORK_CIRCLE_1",
        spotLight = "TYPE_CIRCLE_1",
    )
    val BottomNavigation = SemanticMaterialSpec(
        label = "bottom navigation",
        blur = "TYPE_FRAMEWORK_BOTTOM_BAR",
        stroke = "TYPE_FRAMEWORK_CAPSULE_6",
        spotLight = "TYPE_BOTTOM_NAVIGATION",
    )
    val LargeTranslucentPanel = SemanticMaterialSpec(
        label = "large translucent panel",
        blur = "TYPE_FRAMEWORK_TOP_BAR_BLUR",
        stroke = "TYPE_FRAMEWORK_CAPSULE_10",
        spotLight = "TYPE_TRANSLUCENT_LARGE_1",
    )
    val ControlTile = SemanticMaterialSpec(
        label = "control tile (closest translucent medium family)",
        blur = "TYPE_CONTENT_TRANSPARENT_BUTTON",
        stroke = "TYPE_FRAMEWORK_CAPSULE_10",
        spotLight = "TYPE_TRANSPARENT_MEDIUM_1",
    )
    val ControlTileActive = SemanticMaterialSpec(
        label = "active control tile (closest translucent medium family)",
        blur = "TYPE_CONTENT_SECONDARY_BUTTON",
        stroke = "TYPE_FRAMEWORK_CAPSULE_10",
        spotLight = "TYPE_TRANSLUCENT_MEDIUM_2",
    )
    val ScrollItem = SemanticMaterialSpec(
        label = "floating content surface",
        blur = "TYPE_CONTENT_TRANSPARENT_BUTTON",
        stroke = "TYPE_CONTENT_CAPSULE_3",
        spotLight = "TYPE_TRANSLUCENT_SMALL_1",
    )
    val ToolbarButton = SemanticMaterialSpec(
        label = "toolbar button",
        toolbarCategory = "TOOLBAR_BUTTON",
    )
    val MenuItem = SemanticMaterialSpec(
        label = "menu item",
        toolbarCategory = "MENU_ITEM",
    )
    val OverflowButton = SemanticMaterialSpec(
        label = "overflow button",
        toolbarCategory = "MENU_OVERFLOW_BUTTON",
    )
    val GradientTopBar = SemanticMaterialSpec(
        label = "AppBar gradient blur",
        gradientBlur = true,
    )
}

@Composable
private fun SemanticScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.(Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var wallpaper by remember(context) { mutableStateOf(createSemanticWallpaper(context)) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: error("Bitmap decode returned null")
            }.onSuccess {
                wallpaper = normalizeSemanticWallpaper(context, it)
                loadError = null
            }.onFailure {
                loadError = "Image load failed: ${it.javaClass.simpleName}: ${it.message}"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = BitmapPainter(wallpaper.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 84.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(title, style = TextStyle(Color.White, 24.sp, FontWeight.SemiBold))
            BasicText(subtitle, style = TextStyle(Color.White.copy(alpha = 0.84f), 13.sp))
            loadError?.let { BasicText(it, style = TextStyle(Color(0xFFFF8A80), 12.sp)) }
            content(wallpaper)
            Spacer(Modifier.height(4.dp))
            SemanticToolbarButton(
                label = "Pick an image",
                categorySpec = SemanticSpecs.ToolbarButton,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

@Composable
private fun SemanticButtonsDemo() = SemanticScaffold(
    title = "Buttons · ColorOS semantic",
    subtitle = "Each button now uses the matching COUIMaterialBlurEffect family plus a capsule stroke and the corresponding COUISpotLightEffect preset; no lock-screen GlassEffectBuilder is used here.",
) { _ ->
    SemanticMaterialButton("Transparent button", SemanticSpecs.TransparentButton)
    SemanticMaterialButton("Secondary button", SemanticSpecs.SecondaryButton)
    SemanticMaterialButton("Segment button", SemanticSpecs.SegmentButton)
    SemanticMaterialButton("Chip tab", SemanticSpecs.Chip)
    SemanticToolbarButton("Toolbar delegate", SemanticSpecs.ToolbarButton)
}

@Composable
private fun SemanticToggleDemo() = SemanticScaffold(
    title = "Toggle · ColorOS semantic",
    subtitle = "The track uses the exact TYPE_SWITCH spotlight family; the thumb uses the circle edge/spotlight family. The only non-vendor layer is the state tint.",
) { _ ->
    var selected by remember { mutableStateOf(false) }
    SemanticToggle(
        selected = selected,
        onToggle = { selected = !selected },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    BasicText(if (selected) "ON" else "OFF", Modifier.align(Alignment.CenterHorizontally), style = TextStyle(Color.White, 14.sp))
}

@Composable
private fun SemanticSliderDemo() = SemanticScaffold(
    title = "Slider · ColorOS semantic",
    subtitle = "The control surface is mapped to TYPE_SEEKBAR instead of a lock-screen glass thumb.",
) { _ ->
    var value by remember { mutableFloatStateOf(0.5f) }
    SemanticSlider(value, { value = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    BasicText("${(value * 100).roundToInt()}%", style = TextStyle(Color.White, 14.sp))
}

@Composable
private fun SemanticBottomTabsDemo() = SemanticScaffold(
    title = "Bottom tabs · ColorOS semantic",
    subtitle = "Bar: TYPE_FRAMEWORK_BOTTOM_BAR + TYPE_BOTTOM_NAVIGATION. Selected item: TYPE_CONTENT_CHIP_TAB_SELECTED + TYPE_CHIP_TAB.",
) { _ ->
    var selected by remember { mutableIntStateOf(0) }
    SemanticMaterialSurface(
        spec = SemanticSpecs.BottomNavigation,
        shape = SemanticShape.Bar,
        modifier = Modifier.fillMaxWidth().height(68.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Home", "Glass", "More").forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(27.dp)).clickable { selected = index },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == index) {
                        SemanticMaterialSurface(
                            spec = SemanticSpecs.ChipSelected,
                            shape = SemanticShape.Capsule,
                            modifier = Modifier.fillMaxSize(),
                            overlayColor = Color.White.copy(alpha = 0.035f),
                        ) { }
                    }
                    BasicText(label, style = TextStyle(Color.White.copy(alpha = if (selected == index) 1f else 0.68f), 14.sp))
                }
            }
        }
    }
}

@Composable
private fun SemanticDialogDemo() = SemanticScaffold(
    title = "Dialog · ColorOS semantic",
    subtitle = "Large floating surface uses the recovered large translucent stack: TOP_BAR_BLUR + FRAMEWORK_CAPSULE_10 + TRANSLUCENT_LARGE_1.",
) { _ ->
    var open by remember { mutableStateOf(true) }
    SemanticToolbarButton(
        label = if (open) "Hide dialog" else "Show dialog",
        categorySpec = SemanticSpecs.MenuItem,
        onClick = { open = !open },
    )
    if (open) {
        SemanticMaterialSurface(
            spec = SemanticSpecs.LargeTranslucentPanel,
            shape = SemanticShape.RoundRect,
            modifier = Modifier.fillMaxWidth().height(250.dp),
            overlayColor = Color.Black.copy(alpha = 0.035f),
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("COUI translucent dialog", style = TextStyle(Color.White, 20.sp, FontWeight.SemiBold))
                BasicText(
                    "This page intentionally uses the COUI material stack rather than the keyguard refractive builder.",
                    style = TextStyle(Color.White.copy(alpha = 0.82f), 14.sp),
                )
            }
        }
    }
}

@Composable
private fun SemanticLockScreenDemo() = SemanticScaffold(
    title = "Lock screen · ColorOS refractive glass",
    subtitle = "This is one of the few pages that deliberately keeps GlassEffectBuilder because the recovered ColorOS 17 keyguard implementation actually uses that path for glass clock masks.",
) { wallpaper ->
    SemanticGlassSurface(
        wallpaper = wallpaper,
        shape = SemanticShape.Rect,
        radius = 0.dp,
        text = "08\n24",
        modifier = Modifier.fillMaxWidth().height(440.dp),
    ) { }
    BasicText("8月28日  周五   26°", style = TextStyle(Color.White.copy(alpha = 0.88f), 18.sp, FontWeight.Medium))
}

@Composable
private fun SemanticControlCenterDemo() = SemanticScaffold(
    title = "Control center · ColorOS semantic",
    subtitle = "No keyguard glass reuse. Tiles use the closest recovered medium translucent families; brightness/volume use TYPE_SEEKBAR; the media panel uses the large translucent COUI stack.",
) { _ ->
    var wifi by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SemanticControlTile("Wi-Fi", wifi, { wifi = !wifi }, Modifier.weight(1f))
        SemanticControlTile("Bluetooth", bluetooth, { bluetooth = !bluetooth }, Modifier.weight(1f))
    }
    var brightness by remember { mutableFloatStateOf(0.72f) }
    var volume by remember { mutableFloatStateOf(0.45f) }
    BasicText("Brightness", style = TextStyle(Color.White, 13.sp))
    SemanticSlider(brightness, { brightness = it }, Modifier.fillMaxWidth())
    BasicText("Volume", style = TextStyle(Color.White, 13.sp))
    SemanticSlider(volume, { volume = it }, Modifier.fillMaxWidth())
    SemanticMaterialSurface(
        spec = SemanticSpecs.LargeTranslucentPanel,
        shape = SemanticShape.RoundRect,
        modifier = Modifier.fillMaxWidth().height(110.dp),
    ) { BasicText("Media controls", style = TextStyle(Color.White, 16.sp, FontWeight.Medium)) }
}

@Composable
private fun SemanticControlTile(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    SemanticMaterialSurface(
        spec = if (active) SemanticSpecs.ControlTileActive else SemanticSpecs.ControlTile,
        shape = SemanticShape.RoundRect,
        modifier = modifier.height(126.dp),
        overlayColor = if (active) Color(0xFF168CFF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f),
        onClick = onClick,
    ) {
        BasicText(label, style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
    }
}

@Composable
private fun SemanticMagnifierDemo() = SemanticScaffold(
    title = "Magnifier · closest ColorOS circle material",
    subtitle = "The recovered uxdesign catalog exposes no dedicated magnifier/refraction preset. This page therefore uses the closest native circle stack (transparent blur + framework circle edge + TYPE_CIRCLE_1 spotlight) instead of misusing the lock-screen builder.",
) { _ ->
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().height(360.dp).pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offset += dragAmount
            }
        },
    ) {
        SemanticMaterialSurface(
            spec = SemanticSpecs.CircleControl,
            shape = SemanticShape.Circle,
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(160.dp)
                .align(Alignment.Center),
            overlayColor = Color.White.copy(alpha = 0.025f),
        ) { BasicText("Drag", style = TextStyle(Color.White, 14.sp)) }
    }
}

@Composable
private fun SemanticPlaygroundDemo() = SemanticScaffold(
    title = "ColorOS material playground",
    subtitle = "The playground now demonstrates real vendor semantic families instead of treating every shape as keyguard glass. The final sample is explicitly the keyguard refractive path for comparison.",
) { wallpaper ->
    SemanticMaterialButton("Transparent button", SemanticSpecs.TransparentButton)
    SemanticMaterialButton("Segment button", SemanticSpecs.SegmentButton)
    SemanticToggle(selected = true, onToggle = {}, modifier = Modifier.align(Alignment.CenterHorizontally))
    SemanticSlider(0.62f, {}, Modifier.fillMaxWidth())
    SemanticMaterialSurface(
        spec = SemanticSpecs.BottomNavigation,
        shape = SemanticShape.Bar,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) { BasicText("TYPE_BOTTOM_NAVIGATION", style = TextStyle(Color.White, 13.sp)) }
    SemanticToolbarButton("Toolbar stack + caustic", SemanticSpecs.ToolbarButton)
    BasicText("Keyguard-only refractive path", style = TextStyle(Color.White.copy(alpha = 0.74f), 12.sp))
    SemanticGlassSurface(
        wallpaper = wallpaper,
        shape = SemanticShape.RoundRect,
        radius = 34.dp,
        text = null,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    ) { BasicText("GlassEffectBuilder", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold)) }
}

@Composable
private fun SemanticDiagnosticsDemo() = SemanticScaffold(
    title = "ColorOS native diagnostics",
    subtitle = "Checks the semantic COUI families separately from the keyguard refractive builder so a failure cannot be hidden by another backend.",
) { wallpaper ->
    val context = LocalContext.current
    val bridge = remember(context) { ColorOsMaterialBridge(context.applicationContext) }
    val catalog = remember(bridge) { bridge.catalog() }
    BasicText(
        "Runtime catalog: blur=${catalog.blur.size}, stroke=${catalog.stroke.size}, spotlight=${catalog.spotLight.size}, toolbar=${catalog.toolbarCategories.size}",
        style = TextStyle(Color.White.copy(alpha = 0.82f), 12.sp),
    )
    var materialStatus by remember { mutableStateOf("Waiting for COUI material…") }
    SemanticMaterialSurface(
        spec = SemanticSpecs.ToolbarButton,
        shape = SemanticShape.Capsule,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        onStatus = { materialStatus = it },
    ) { BasicText("ToolbarMaterialEffectDelegate", style = TextStyle(Color.White, 14.sp)) }
    BasicText(materialStatus, style = TextStyle(Color.White.copy(alpha = 0.72f), 11.sp))
    var glassStatus by remember { mutableStateOf("Waiting for keyguard glass…") }
    SemanticGlassSurface(
        wallpaper = wallpaper,
        shape = SemanticShape.RoundRect,
        radius = 30.dp,
        modifier = Modifier.fillMaxWidth().height(100.dp),
        onStatus = { glassStatus = it },
    ) { BasicText("GlassEffectBuilder", style = TextStyle(Color.White, 14.sp)) }
    BasicText(glassStatus, style = TextStyle(Color.White.copy(alpha = 0.72f), 11.sp))
}

@Composable
private fun SemanticAdaptiveDemo() = SemanticScaffold(
    title = "Adaptive luminance · keyguard glass",
    subtitle = "This experiment intentionally stays on GlassEffectBuilder: two identical refractive masks sample different regions of the same wallpaper to isolate ColorOS's own glass blending response.",
) { wallpaper ->
    SemanticGlassSurface(
        wallpaper = wallpaper,
        shape = SemanticShape.Capsule,
        radius = 28.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { BasicText("Upper-region glass", style = TextStyle(Color.White, 14.sp)) }
    Spacer(Modifier.height(150.dp))
    SemanticGlassSurface(
        wallpaper = wallpaper,
        shape = SemanticShape.Capsule,
        radius = 28.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { BasicText("Lower-region glass", style = TextStyle(Color.White, 14.sp)) }
}

@Composable
private fun SemanticProgressiveBlurDemo() = SemanticScaffold(
    title = "Progressive blur · ColorOS AppBar",
    subtitle = "Uses AppBarBlurHelper / OplusRenderEffect.createGradientBlurEffect, which is the recovered ColorOS progressive-blur path.",
) { _ ->
    var fraction by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("") }
    SemanticMaterialSurface(
        spec = SemanticSpecs.GradientTopBar,
        shape = SemanticShape.Rect,
        modifier = Modifier.fillMaxWidth().height(220.dp),
        fraction = fraction,
        onStatus = { status = it },
    ) { BasicText("ColorOS gradient blur", style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold)) }
    BasicText("Gradient fraction ${(fraction * 100).roundToInt()}%", style = TextStyle(Color.White, 13.sp))
    SemanticSlider(fraction, { fraction = it }, Modifier.fillMaxWidth())
    if (status.isNotBlank()) BasicText(status, style = TextStyle(Color.White.copy(alpha = 0.72f), 11.sp))
}

@Composable
private fun SemanticScrollDemo(lazyStyle: Boolean) = SemanticScaffold(
    title = if (lazyStyle) "Lazy scroll container · ColorOS semantic" else "Scroll container · ColorOS semantic",
    subtitle = "Floating rows now use the content-surface COUI stack (transparent-button blur + content capsule stroke + translucent-small spotlight), not the keyguard glass builder.",
) { _ ->
    repeat(if (lazyStyle) 16 else 9) { index ->
        SemanticMaterialSurface(
            spec = SemanticSpecs.ScrollItem,
            shape = SemanticShape.RoundRect,
            modifier = Modifier.fillMaxWidth().height(if (lazyStyle) 76.dp else 92.dp),
            overlayColor = if (index % 3 == 0) Color(0xFF168CFF).copy(alpha = 0.055f) else Color.Transparent,
        ) { BasicText("Item ${index + 1}", style = TextStyle(Color.White, 15.sp, FontWeight.Medium)) }
    }
}

@Composable
private fun SemanticMaterialButton(
    label: String,
    spec: SemanticMaterialSpec,
    onClick: () -> Unit = {},
) {
    SemanticMaterialSurface(
        spec = spec,
        shape = SemanticShape.Capsule,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
        }
    }
}

@Composable
private fun SemanticToolbarButton(
    label: String,
    categorySpec: SemanticMaterialSpec,
    onClick: () -> Unit = {},
) {
    SemanticMaterialSurface(
        spec = categorySpec,
        shape = SemanticShape.Capsule,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        onClick = onClick,
    ) { BasicText(label, style = TextStyle(Color.White, 15.sp, FontWeight.Medium)) }
}

@Composable
private fun SemanticToggle(
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SemanticMaterialSurface(
        spec = SemanticSpecs.Switch,
        shape = SemanticShape.Capsule,
        modifier = modifier.size(72.dp, 38.dp),
        overlayColor = if (selected) Color(0xFF34C759).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.025f),
        onClick = onToggle,
    ) {
        Box(Modifier.fillMaxSize()) {
            SemanticMaterialSurface(
                spec = SemanticSpecs.CircleControl,
                shape = SemanticShape.Circle,
                modifier = Modifier.offset(x = if (selected) 38.dp else 4.dp, y = 4.dp).size(30.dp),
                overlayColor = Color.White.copy(alpha = 0.06f),
                onClick = onToggle,
            ) { }
        }
    }
}

@Composable
private fun SemanticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier.height(42.dp).pointerInput(onValueChange) {
            detectDragGestures(
                onDragStart = { position -> onValueChange((position.x / size.width).coerceIn(0f, 1f)) },
            ) { change, _ ->
                change.consume()
                onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        SemanticMaterialSurface(
            spec = SemanticSpecs.SeekBar,
            shape = SemanticShape.Capsule,
            modifier = Modifier.fillMaxWidth().height(18.dp),
        ) { }
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF168CFF).copy(alpha = 0.28f)),
        )
        val travel = maxWidth - 28.dp
        SemanticMaterialSurface(
            spec = SemanticSpecs.CircleControl,
            shape = SemanticShape.Circle,
            modifier = Modifier.offset(x = travel * clamped).size(28.dp),
            overlayColor = Color.White.copy(alpha = 0.06f),
        ) { }
    }
}

@Composable
private fun SemanticMaterialSurface(
    spec: SemanticMaterialSpec,
    shape: SemanticShape,
    modifier: Modifier,
    fraction: Float = 1f,
    overlayColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    onStatus: ((String) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> SemanticMaterialHostView(context) },
            update = { host ->
                host.onStatus = onStatus
                host.configure(spec, shape, fraction, onClick)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (overlayColor.alpha > 0f) {
            val shapeModifier = when (shape) {
                SemanticShape.Circle -> Modifier.clip(CircleShape)
                SemanticShape.Capsule -> Modifier.clip(RoundedCornerShape(999.dp))
                SemanticShape.RoundRect, SemanticShape.Bar -> Modifier.clip(RoundedCornerShape(28.dp))
                SemanticShape.Rect -> Modifier
            }
            Box(Modifier.fillMaxSize().then(shapeModifier).background(overlayColor))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

private class SemanticMaterialHostView(context: Context) : View(context) {
    private val bridge = ColorOsMaterialBridge(context)
    private var appliedKey: String? = null
    private var shape = SemanticShape.Rect
    var onStatus: ((String) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width.coerceAtLeast(1)
                val h = view.height.coerceAtLeast(1)
                val density = resources.displayMetrics.density
                when (shape) {
                    SemanticShape.Circle -> outline.setOval(0, 0, w, h)
                    SemanticShape.Capsule -> outline.setRoundRect(0, 0, w, h, h / 2f)
                    SemanticShape.RoundRect -> outline.setRoundRect(0, 0, w, h, 28f * density)
                    SemanticShape.Bar -> outline.setRoundRect(0, 0, w, h, 30f * density)
                    SemanticShape.Rect -> outline.setRect(0, 0, w, h)
                }
            }
        }
    }

    fun configure(
        spec: SemanticMaterialSpec,
        shape: SemanticShape,
        fraction: Float,
        onClick: (() -> Unit)?,
    ) {
        this.shape = shape
        clipToOutline = shape != SemanticShape.Rect
        invalidateOutline()
        isClickable = onClick != null
        setOnClickListener(if (onClick == null) null else View.OnClickListener { onClick() })

        val key = "$spec:$shape:${fraction.coerceIn(0f, 1f)}"
        if (key == appliedKey) return
        appliedKey = key
        bridge.clear(this)

        val failures = mutableListOf<String>()
        if (spec.gradientBlur) {
            bridge.applyGradientBlur(this, fraction).exceptionOrNull()?.let { failures += "gradient:${describe(it)}" }
        } else if (spec.toolbarCategory != null) {
            bridge.applyToolbarStack(
                view = this,
                categoryName = spec.toolbarCategory,
                blur = true,
                stroke = true,
                spotLight = true,
                caustic = true,
                forceEnable = true,
            ).exceptionOrNull()?.let { failures += "toolbar:${describe(it)}" }
        } else {
            spec.blur?.let { name -> bridge.applyBlur(this, name).exceptionOrNull()?.let { failures += "blur:${describe(it)}" } }
            spec.stroke?.let { name -> bridge.applyStroke(this, name).exceptionOrNull()?.let { failures += "stroke:${describe(it)}" } }
            spec.spotLight?.let { name -> bridge.applySpotLight(this, name).exceptionOrNull()?.let { failures += "spot:${describe(it)}" } }
        }

        onStatus?.invoke(
            if (failures.isEmpty()) {
                "PASS — ${spec.label}"
            } else {
                appliedKey = null
                "UNAVAILABLE — ${spec.label}: ${failures.joinToString(" | ")}"
            },
        )
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        super.onDetachedFromWindow()
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}

@Composable
private fun SemanticGlassSurface(
    wallpaper: Bitmap,
    shape: SemanticShape,
    radius: Dp,
    modifier: Modifier,
    text: String? = null,
    glass: Float = 1f,
    mix: Float = 1f,
    mask: Float = 1f,
    light: Boolean = true,
    onStatus: ((String) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> SemanticGlassHostView(context) },
            update = { host ->
                host.onStatus = onStatus
                host.configure(wallpaper, shape, radiusPx, text, glass, mix, mask, light)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

private class SemanticGlassHostView(context: Context) : View(context) {
    private val bridge = ColorOsClockGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var markerColor: Int? = null
    private var wallpaper: Bitmap? = null
    private var shape = SemanticShape.RoundRect
    private var radiusPx = 0f
    private var text: String? = null
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
                applyIfReady()
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width.coerceAtLeast(1)
                val h = view.height.coerceAtLeast(1)
                when (shape) {
                    SemanticShape.Circle -> outline.setOval(0, 0, w, h)
                    SemanticShape.Capsule -> outline.setRoundRect(0, 0, w, h, h / 2f)
                    SemanticShape.RoundRect, SemanticShape.Bar -> outline.setRoundRect(0, 0, w, h, radiusPx)
                    SemanticShape.Rect -> outline.setRect(0, 0, w, h)
                }
            }
        }
        markerColor = bridge.locationColor().getOrNull()
        paint.color = markerColor ?: AndroidColor.TRANSPARENT
    }

    fun configure(
        wallpaper: Bitmap,
        shape: SemanticShape,
        radiusPx: Float,
        text: String?,
        glass: Float,
        mix: Float,
        mask: Float,
        light: Boolean,
    ) {
        this.wallpaper = wallpaper
        this.shape = shape
        this.radiusPx = radiusPx
        this.text = text
        this.glass = glass.coerceIn(0f, 1f)
        this.mix = mix.coerceIn(0f, 1f)
        this.mask = mask.coerceIn(0f, 1f)
        this.light = light
        clipToOutline = shape != SemanticShape.Rect || text == null
        paint.color = markerColor ?: bridge.locationColor().getOrNull()?.also { markerColor = it } ?: AndroidColor.TRANSPARENT
        invalidateOutline()
        invalidate()
        lastKey = null
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
        invalidateOutline()
        lastKey = null
        applyIfReady()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = markerColor ?: return
        paint.shader = null
        paint.color = color
        if (text != null) {
            drawGlassText(canvas, text!!)
            return
        }
        when (shape) {
            SemanticShape.Circle -> canvas.drawOval(0f, 0f, width.toFloat(), height.toFloat(), paint)
            SemanticShape.Capsule -> {
                val r = height / 2f
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
            }
            SemanticShape.RoundRect, SemanticShape.Bar -> canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
            SemanticShape.Rect -> canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private fun drawGlassText(canvas: Canvas, value: String) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        val lines = value.split('\n')
        val lineHeight = height / lines.size.toFloat()
        paint.textSize = minOf(width * 0.72f, lineHeight * 0.92f)
        val fm = paint.fontMetrics
        val baselineAdjust = -(fm.ascent + fm.descent) / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, lineHeight * (index + 0.5f) + baselineAdjust, paint)
        }
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        super.onDetachedFromWindow()
    }

    private fun applyIfReady() {
        val bg = wallpaper ?: return
        val marker = markerColor ?: bridge.locationColor().getOrElse { error ->
            onStatus?.invoke("UNAVAILABLE — marker: ${error.javaClass.simpleName}: ${error.message}")
            return
        }.also { markerColor = it }
        if (width <= 0 || height <= 0 || !isAttachedToWindow) return

        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = buildString {
            append(System.identityHashCode(bg)).append(':')
            append(width).append(':').append(height).append(':')
            append(location[0]).append(':').append(location[1]).append(':')
            append(shape).append(':').append(radiusPx).append(':').append(text).append(':')
            append(marker).append(':').append(glass).append(':').append(mix).append(':').append(mask).append(':').append(light)
        }
        if (key == lastKey) return
        lastKey = key
        paint.color = marker
        invalidate()

        bridge.apply(this, bg, glass, mix, mask, light)
            .onSuccess { result -> onStatus?.invoke("PASS — $result") }
            .onFailure { error ->
                lastKey = null
                bridge.clear(this)
                paint.color = AndroidColor.TRANSPARENT
                invalidate()
                onStatus?.invoke("UNAVAILABLE — ${error.javaClass.simpleName}: ${error.message}")
            }
    }
}

private fun createSemanticWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val bitmap = Bitmap.createBitmap(dm.widthPixels.coerceAtLeast(720), dm.heightPixels.coerceAtLeast(1280), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        bitmap.width.toFloat(),
        bitmap.height.toFloat(),
        intArrayOf(
            AndroidColor.rgb(13, 20, 55),
            AndroidColor.rgb(105, 75, 210),
            AndroidColor.rgb(204, 93, 196),
            AndroidColor.rgb(255, 104, 35),
        ),
        floatArrayOf(0f, 0.36f, 0.68f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.shader = null
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(bitmap.width * 0.22f, bitmap.height * 0.24f, bitmap.width * 0.13f, paint)
    paint.color = AndroidColor.rgb(255, 70, 100)
    canvas.drawCircle(bitmap.width * 0.78f, bitmap.height * 0.48f, bitmap.width * 0.19f, paint)
    paint.color = AndroidColor.rgb(60, 235, 190)
    canvas.drawCircle(bitmap.width * 0.38f, bitmap.height * 0.78f, bitmap.width * 0.17f, paint)
    return bitmap
}

private fun normalizeSemanticWallpaper(context: Context, source: Bitmap): Bitmap {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(1)
    val h = dm.heightPixels.coerceAtLeast(1)
    if (source.width == w && source.height == h) return source
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val scale = maxOf(w / source.width.toFloat(), h / source.height.toFloat())
    val sw = (w / scale).toInt().coerceAtLeast(1)
    val sh = (h / scale).toInt().coerceAtLeast(1)
    val left = ((source.width - sw) / 2).coerceAtLeast(0)
    val top = ((source.height - sh) / 2).coerceAtLeast(0)
    Canvas(out).drawBitmap(
        source,
        Rect(left, top, (left + sw).coerceAtMost(source.width), (top + sh).coerceAtMost(source.height)),
        Rect(0, 0, w, h),
        Paint(Paint.ANTI_ALIAS_FLAG),
    )
    return out
}
