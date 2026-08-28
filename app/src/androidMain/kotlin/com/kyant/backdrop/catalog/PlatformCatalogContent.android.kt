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
 * Android-only catalog backend. Every material surface rendered from here is
 * backed by ColorOS code loaded from the device. The upstream Kyant glass is
 * kept only for non-Android KMP targets and is never composed on Android once a
 * destination leaves Home.
 */
@Composable
actual fun PlatformCatalogContent(
    destination: CatalogDestination,
    onNavigate: (CatalogDestination) -> Unit,
): Boolean {
    if (destination == CatalogDestination.Home) return false

    when (destination) {
        CatalogDestination.Home -> Unit
        CatalogDestination.Buttons -> ColorOsButtonsDemo()
        CatalogDestination.Toggle -> ColorOsToggleDemo()
        CatalogDestination.Slider -> ColorOsSliderDemo()
        CatalogDestination.BottomTabs -> ColorOsBottomTabsDemo()
        CatalogDestination.Dialog -> ColorOsDialogDemo()
        CatalogDestination.LockScreen -> ColorOsLockScreenDemo()
        CatalogDestination.ControlCenter -> ColorOsControlCenterDemo()
        CatalogDestination.Magnifier -> ColorOsMagnifierDemo()
        CatalogDestination.GlassPlayground -> ColorOsGlassPlaygroundDemo()
        CatalogDestination.ColorOsNativeComparison -> ColorOsDiagnosticsDemo()
        CatalogDestination.AdaptiveLuminanceGlass -> ColorOsAdaptiveDemo()
        CatalogDestination.ProgressiveBlur -> ColorOsProgressiveBlurDemo()
        CatalogDestination.ScrollContainer -> ColorOsScrollDemo(lazyStyle = false)
        CatalogDestination.LazyScrollContainer -> ColorOsScrollDemo(lazyStyle = true)
    }
    return true
}

private enum class NativeMaskKind { RoundRect, Capsule, Circle, Text }
private enum class NativeMaterialMode { Blur, GradientBlur, Toolbar }

@Composable
private fun ColorOsDemoScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.(Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var wallpaper by remember(context) { mutableStateOf(createDemoWallpaper(context)) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: error("Bitmap decode returned null")
            }.onSuccess {
                wallpaper = normalizeWallpaper(context, it)
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
            BasicText(subtitle, style = TextStyle(Color.White.copy(alpha = 0.82f), 13.sp))
            loadError?.let { BasicText(it, style = TextStyle(Color(0xFFFF8A80), 12.sp)) }
            content(wallpaper)
            Spacer(Modifier.height(6.dp))
            NativeGlassButton(
                wallpaper = wallpaper,
                label = "Pick an image",
                overlayColor = Color(0xFF0088FF).copy(alpha = 0.24f),
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

@Composable
private fun ColorOsButtonsDemo() = ColorOsDemoScaffold(
    title = "Buttons · ColorOS",
    subtitle = "All four surfaces run the installed ColorOS GlassEffectBuilder. No Kyant lens is composed on Android.",
) { wallpaper ->
    NativeGlassButton(wallpaper, "Transparent glass", Color.Transparent, {})
    NativeGlassButton(wallpaper, "Surface glass", Color.White.copy(alpha = 0.10f), {})
    NativeGlassButton(wallpaper, "Blue glass", Color(0xFF0088FF).copy(alpha = 0.22f), {})
    NativeGlassButton(wallpaper, "Orange glass", Color(0xFFFF8D28).copy(alpha = 0.22f), {})
}

@Composable
private fun ColorOsToggleDemo() = ColorOsDemoScaffold(
    title = "Toggle · ColorOS",
    subtitle = "The track and thumb are native ColorOS glass masks; the switch state only changes the overlay tint and position.",
) { wallpaper ->
    var selected by remember { mutableStateOf(false) }
    NativeToggle(wallpaper, selected, { selected = !selected }, Modifier.align(Alignment.CenterHorizontally))
    BasicText(if (selected) "ON" else "OFF", Modifier.align(Alignment.CenterHorizontally), style = TextStyle(Color.White, 14.sp))
}

@Composable
private fun ColorOsSliderDemo() = ColorOsDemoScaffold(
    title = "Slider · ColorOS",
    subtitle = "The movable thumb is the real lock-screen glass RenderEffect; the track remains ordinary UI content.",
) { wallpaper ->
    var value by remember { mutableFloatStateOf(0.5f) }
    NativeSlider(value, { value = it }, wallpaper, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    BasicText("${(value * 100).roundToInt()}%", style = TextStyle(Color.White, 14.sp))
}

@Composable
private fun ColorOsBottomTabsDemo() = ColorOsDemoScaffold(
    title = "Bottom tabs · ColorOS",
    subtitle = "The bar and selected indicator are both ColorOS glass surfaces.",
) { wallpaper ->
    var selected by remember { mutableIntStateOf(0) }
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.Capsule,
        radius = 32.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        overlayColor = Color.White.copy(alpha = 0.06f),
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Home", "Glass", "More").forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp))
                        .background(if (selected == index) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { selected = index },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = TextStyle(Color.White, 14.sp, if (selected == index) FontWeight.SemiBold else FontWeight.Normal))
                }
            }
        }
    }
}

@Composable
private fun ColorOsDialogDemo() = ColorOsDemoScaffold(
    title = "Dialog · ColorOS",
    subtitle = "A dialog-sized arbitrary mask is fed through the same native GlassEffectBuilder used by the new lock screen.",
) { wallpaper ->
    var open by remember { mutableStateOf(true) }
    NativeGlassButton(
        wallpaper = wallpaper,
        label = if (open) "Hide dialog" else "Show dialog",
        overlayColor = Color.White.copy(alpha = 0.08f),
        onClick = { open = !open },
    )
    if (open) {
        NativeGlassSurface(
            wallpaper = wallpaper,
            kind = NativeMaskKind.RoundRect,
            radius = 36.dp,
            modifier = Modifier.fillMaxWidth().height(260.dp),
            overlayColor = Color.Black.copy(alpha = 0.08f),
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Native ColorOS glass dialog", style = TextStyle(Color.White, 20.sp, FontWeight.SemiBold))
                BasicText(
                    "Refraction, RGB dispersion, edge light, inner shadow and adaptive blending are generated by the installed personality-clocks package.",
                    style = TextStyle(Color.White.copy(alpha = 0.84f), 14.sp),
                )
            }
        }
    }
}

@Composable
private fun ColorOsLockScreenDemo() = ColorOsDemoScaffold(
    title = "Lock screen · ColorOS",
    subtitle = "This is the closest path to the shipping implementation: a text alpha mask is processed by ColorOS's own soft-field/SDF-like pipeline.",
) { wallpaper ->
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.Text,
        radius = 0.dp,
        text = "08\n24",
        modifier = Modifier.fillMaxWidth().height(440.dp),
    ) { Box(Modifier.fillMaxSize()) }
    BasicText("8月28日  周五   26°", style = TextStyle(Color.White.copy(alpha = 0.86f), 18.sp, FontWeight.Medium))
}

@Composable
private fun ColorOsControlCenterDemo() = ColorOsDemoScaffold(
    title = "Control center · ColorOS",
    subtitle = "Control-center geometry using the native ColorOS liquid-glass shader instead of the generic demo lens.",
) { wallpaper ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlTile(wallpaper, "Wi-Fi", Modifier.weight(1f), Color(0xFF168CFF).copy(alpha = 0.20f))
        ControlTile(wallpaper, "Bluetooth", Modifier.weight(1f), Color.White.copy(alpha = 0.06f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlTile(wallpaper, "Brightness", Modifier.weight(1f), Color.White.copy(alpha = 0.08f))
        ControlTile(wallpaper, "Volume", Modifier.weight(1f), Color.White.copy(alpha = 0.08f))
    }
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.RoundRect,
        radius = 28.dp,
        modifier = Modifier.fillMaxWidth().height(110.dp),
        overlayColor = Color.White.copy(alpha = 0.06f),
    ) { BasicText("Media controls", style = TextStyle(Color.White, 16.sp, FontWeight.Medium)) }
}

@Composable
private fun ControlTile(wallpaper: Bitmap, label: String, modifier: Modifier, tint: Color) {
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.RoundRect,
        radius = 28.dp,
        modifier = modifier.height(128.dp),
        overlayColor = tint,
    ) { BasicText(label, style = TextStyle(Color.White, 15.sp, FontWeight.Medium)) }
}

@Composable
private fun ColorOsMagnifierDemo() = ColorOsDemoScaffold(
    title = "Magnifier · ColorOS",
    subtitle = "Drag the circular ColorOS glass mask. Screen-space wallpaper cropping follows the host View, matching the Codex-verified crop fix.",
) { wallpaper ->
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().height(360.dp).pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offset += dragAmount
            }
        },
    ) {
        NativeGlassSurface(
            wallpaper = wallpaper,
            kind = NativeMaskKind.Circle,
            radius = 80.dp,
            modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }.size(160.dp).align(Alignment.Center),
            overlayColor = Color.White.copy(alpha = 0.04f),
        ) { BasicText("Drag", style = TextStyle(Color.White, 14.sp)) }
    }
}

@Composable
private fun ColorOsGlassPlaygroundDemo() = ColorOsDemoScaffold(
    title = "Glass playground · ColorOS native",
    subtitle = "Only controls that really exist on GlassEffectBuilder are exposed. Refraction and dispersion strengths stay at the firmware's own values.",
) { wallpaper ->
    var glass by remember { mutableFloatStateOf(1f) }
    var mix by remember { mutableFloatStateOf(1f) }
    var mask by remember { mutableFloatStateOf(1f) }
    var radiusFraction by remember { mutableFloatStateOf(0.45f) }
    var light by remember { mutableStateOf(true) }

    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.RoundRect,
        radius = (24 + 92 * radiusFraction).dp,
        modifier = Modifier.size(256.dp).align(Alignment.CenterHorizontally),
        glass = glass,
        mix = mix,
        mask = mask,
        light = light,
        overlayColor = Color.White.copy(alpha = 0.03f),
    ) { BasicText("ColorOS", style = TextStyle(Color.White, 20.sp, FontWeight.SemiBold)) }

    NativeSliderRow("Glass", glass, wallpaper) { glass = it }
    NativeSliderRow("State mix", mix, wallpaper) { mix = it }
    NativeSliderRow("Mask color mix", mask, wallpaper) { mask = it }
    NativeSliderRow("Corner radius", radiusFraction, wallpaper) { radiusFraction = it }
    NativeGlassButton(
        wallpaper = wallpaper,
        label = if (light) "Effect light: ON" else "Effect light: OFF",
        overlayColor = Color.White.copy(alpha = 0.06f),
        onClick = { light = !light },
    )
}

@Composable
private fun NativeSliderRow(label: String, value: Float, wallpaper: Bitmap, onValueChange: (Float) -> Unit) {
    BasicText("$label  ${(value * 100).roundToInt()}%", style = TextStyle(Color.White, 13.sp))
    NativeSlider(value, onValueChange, wallpaper, Modifier.fillMaxWidth())
}

@Composable
private fun ColorOsDiagnosticsDemo() = ColorOsDemoScaffold(
    title = "ColorOS native diagnostics",
    subtitle = "The old generic-vs-native panel has been removed from the Android path. This page now verifies only installed ColorOS implementations.",
) { wallpaper ->
    var status by remember { mutableStateOf("Waiting for native surface…") }
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.RoundRect,
        radius = 40.dp,
        modifier = Modifier.fillMaxWidth().height(180.dp),
        onStatus = { status = it },
    ) { BasicText("GlassEffectBuilder", style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold)) }
    BasicText(status, style = TextStyle(Color.White.copy(alpha = 0.84f), 11.sp))
    NativeMaterialSurface(
        mode = NativeMaterialMode.Toolbar,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        onStatus = { status = "$status\n$it" },
    ) { BasicText("COUI / Oplus material stack", style = TextStyle(Color.White, 15.sp, FontWeight.Medium)) }
}

@Composable
private fun ColorOsAdaptiveDemo() = ColorOsDemoScaffold(
    title = "Adaptive luminance · ColorOS",
    subtitle = "Two identical firmware glass surfaces sample different parts of the same high-contrast wallpaper, exercising ColorOS's own blend-mode adaptation.",
) { wallpaper ->
    NativeGlassButton(wallpaper, "Glass over upper region", Color.Transparent, {})
    Spacer(Modifier.height(150.dp))
    NativeGlassButton(wallpaper, "Glass over lower region", Color.Transparent, {})
}

@Composable
private fun ColorOsProgressiveBlurDemo() = ColorOsDemoScaffold(
    title = "Progressive blur · ColorOS",
    subtitle = "This page uses AppBarBlurHelper and OplusRenderEffect.createGradientBlurEffect from com.oplus.uxdesign, not a generic blur shader.",
) { wallpaper ->
    var fraction by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("") }
    NativeMaterialSurface(
        mode = NativeMaterialMode.GradientBlur,
        fraction = fraction,
        modifier = Modifier.fillMaxWidth().height(220.dp),
        onStatus = { status = it },
    ) { BasicText("ColorOS gradient blur", style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold)) }
    NativeSliderRow("Gradient fraction", fraction, wallpaper) { fraction = it }
    if (status.isNotBlank()) BasicText(status, style = TextStyle(Color.White.copy(alpha = 0.75f), 11.sp))
}

@Composable
private fun ColorOsScrollDemo(lazyStyle: Boolean) = ColorOsDemoScaffold(
    title = if (lazyStyle) "Lazy scroll container · ColorOS" else "Scroll container · ColorOS",
    subtitle = "Every floating material surface is native ColorOS. Moving surfaces rebuild their screen-space crop so the wallpaper sampling stays aligned while scrolling.",
) { wallpaper ->
    repeat(if (lazyStyle) 16 else 9) { index ->
        NativeGlassSurface(
            wallpaper = wallpaper,
            kind = NativeMaskKind.RoundRect,
            radius = 24.dp,
            modifier = Modifier.fillMaxWidth().height(if (lazyStyle) 76.dp else 92.dp),
            overlayColor = if (index % 3 == 0) Color(0xFF0088FF).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.035f),
        ) { BasicText("Item ${index + 1}", style = TextStyle(Color.White, 15.sp, FontWeight.Medium)) }
    }
}

@Composable
private fun NativeGlassButton(
    wallpaper: Bitmap,
    label: String,
    overlayColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.Capsule,
        radius = 28.dp,
        modifier = modifier.height(52.dp).clickable(onClick = onClick),
        overlayColor = overlayColor,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
        }
    }
}

@Composable
private fun NativeToggle(
    wallpaper: Bitmap,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NativeGlassSurface(
        wallpaper = wallpaper,
        kind = NativeMaskKind.Capsule,
        radius = 18.dp,
        modifier = modifier.size(68.dp, 34.dp).clickable(onClick = onToggle),
        overlayColor = if (selected) Color(0xFF34C759).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f),
    ) {
        Box(Modifier.fillMaxSize()) {
            NativeGlassSurface(
                wallpaper = wallpaper,
                kind = NativeMaskKind.Circle,
                radius = 13.dp,
                modifier = Modifier.offset(x = if (selected) 36.dp else 4.dp, y = 4.dp).size(26.dp),
                overlayColor = Color.White.copy(alpha = 0.12f),
            ) { }
        }
    }
}

@Composable
private fun NativeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    wallpaper: Bitmap,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier.height(40.dp).pointerInput(onValueChange) {
            detectDragGestures(
                onDragStart = { position -> onValueChange((position.x / size.width).coerceIn(0f, 1f)) },
            ) { change, _ ->
                change.consume()
                onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.24f)))
        Box(Modifier.fillMaxWidth(clamped).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF168CFF).copy(alpha = 0.82f)))
        val travel = maxWidth - 30.dp
        NativeGlassSurface(
            wallpaper = wallpaper,
            kind = NativeMaskKind.Capsule,
            radius = 14.dp,
            modifier = Modifier.offset(x = travel * clamped).size(30.dp, 24.dp),
            overlayColor = Color.White.copy(alpha = 0.10f),
        ) { }
    }
}

@Composable
private fun NativeGlassSurface(
    wallpaper: Bitmap,
    kind: NativeMaskKind,
    radius: Dp,
    modifier: Modifier = Modifier,
    glass: Float = 1f,
    mix: Float = 1f,
    mask: Float = 1f,
    light: Boolean = true,
    text: String? = null,
    overlayColor: Color = Color.Transparent,
    onStatus: ((String) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> ColorOsGlassHostView(context).also { host -> host.onStatus = onStatus } },
            update = { host ->
                host.onStatus = onStatus
                host.configure(wallpaper, kind, radiusPx, text, glass, mix, mask, light)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (overlayColor.alpha > 0f && kind != NativeMaskKind.Text) {
            val shape = if (kind == NativeMaskKind.Circle) CircleShape else RoundedCornerShape(radius)
            Box(Modifier.fillMaxSize().clip(shape).background(overlayColor))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun NativeMaterialSurface(
    mode: NativeMaterialMode,
    modifier: Modifier,
    fraction: Float = 1f,
    onStatus: ((String) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> ColorOsMaterialHostView(context).also { it.onStatus = onStatus } },
            update = { host ->
                host.onStatus = onStatus
                host.configure(mode, fraction)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

private class ColorOsGlassHostView(context: Context) : View(context) {
    private val bridge = ColorOsClockGlassBridge(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var markerColor: Int? = null
    private var wallpaper: Bitmap? = null
    private var kind = NativeMaskKind.RoundRect
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
        isClickable = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                when (kind) {
                    NativeMaskKind.Circle -> outline.setOval(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                    NativeMaskKind.Capsule -> outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), view.height / 2f)
                    NativeMaskKind.RoundRect -> outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radiusPx)
                    NativeMaskKind.Text -> outline.setRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                }
            }
        }
        markerColor = bridge.locationColor().getOrNull()
        paint.color = markerColor ?: AndroidColor.TRANSPARENT
    }

    fun configure(
        wallpaper: Bitmap,
        kind: NativeMaskKind,
        radiusPx: Float,
        text: String?,
        glass: Float,
        mix: Float,
        mask: Float,
        light: Boolean,
    ) {
        this.wallpaper = wallpaper
        this.kind = kind
        this.radiusPx = radiusPx
        this.text = text
        this.glass = glass.coerceIn(0f, 1f)
        this.mix = mix.coerceIn(0f, 1f)
        this.mask = mask.coerceIn(0f, 1f)
        this.light = light
        clipToOutline = kind != NativeMaskKind.Text
        paint.color = markerColor ?: bridge.locationColor().getOrNull()?.also { markerColor = it } ?: AndroidColor.TRANSPARENT
        invalidateOutline()
        invalidate()
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
        when (kind) {
            NativeMaskKind.Circle -> canvas.drawOval(0f, 0f, width.toFloat(), height.toFloat(), paint)
            NativeMaskKind.Capsule -> {
                val r = height / 2f
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
            }
            NativeMaskKind.RoundRect -> canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radiusPx, radiusPx, paint)
            NativeMaskKind.Text -> drawGlassText(canvas, text ?: "08\n24")
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
            val centerY = lineHeight * (index + 0.5f)
            canvas.drawText(line, width / 2f, centerY + baselineAdjust, paint)
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
            onStatus?.invoke("ColorOS marker lookup failed: ${error.javaClass.simpleName}: ${error.message}")
            return
        }.also { markerColor = it }
        if (width <= 0 || height <= 0 || !isAttachedToWindow) return

        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = buildString {
            append(System.identityHashCode(bg)).append(':')
            append(width).append(':').append(height).append(':')
            append(location[0]).append(':').append(location[1]).append(':')
            append(kind).append(':').append(radiusPx).append(':').append(text).append(':')
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

private class ColorOsMaterialHostView(context: Context) : View(context) {
    private val bridge = ColorOsMaterialBridge(context)
    private var mode: NativeMaterialMode? = null
    private var fraction = Float.NaN
    private var scheduled = false
    var onStatus: ((String) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = false
    }

    fun configure(mode: NativeMaterialMode, fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (this.mode == mode && this.fraction == f) return
        this.mode = mode
        this.fraction = f
        scheduleApply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleApply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scheduleApply()
    }

    override fun onDetachedFromWindow() {
        bridge.clear(this)
        super.onDetachedFromWindow()
    }

    private fun scheduleApply() {
        if (!isAttachedToWindow || width <= 0 || height <= 0 || scheduled) return
        scheduled = true
        post {
            scheduled = false
            val current = mode ?: return@post
            val result = when (current) {
                NativeMaterialMode.GradientBlur -> bridge.applyGradientBlur(this, if (fraction.isNaN()) 1f else fraction)
                NativeMaterialMode.Blur -> bridge.applyBlur(this, "TYPE_FRAMEWORK_TOP_BAR_BLUR")
                    .recoverCatching { bridge.applyBlur(this, "TYPE_CONTENT_TRANSPARENT_BUTTON").getOrThrow() }
                NativeMaterialMode.Toolbar -> bridge.applyToolbarStack(
                    view = this,
                    categoryName = "TOOLBAR_BUTTON",
                    blur = true,
                    stroke = true,
                    spotLight = true,
                    caustic = true,
                )
            }
            result.onSuccess {
                onStatus?.invoke("PASS — ColorOS ${current.name}; ${bridge.diagnostics().joinToString(" | ")}")
            }.onFailure { error ->
                onStatus?.invoke("UNAVAILABLE — ${error.javaClass.simpleName}: ${error.message}; ${bridge.diagnostics().joinToString(" | ")}")
            }
        }
    }
}

private fun createDemoWallpaper(context: Context): Bitmap {
    val dm = context.resources.displayMetrics
    val bitmap = Bitmap.createBitmap(dm.widthPixels.coerceAtLeast(720), dm.heightPixels.coerceAtLeast(1280), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
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

private fun normalizeWallpaper(context: Context, source: Bitmap): Bitmap {
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
