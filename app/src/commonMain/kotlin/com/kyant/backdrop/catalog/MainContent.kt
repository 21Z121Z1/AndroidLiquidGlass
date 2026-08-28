package com.kyant.backdrop.catalog

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.catalog.destinations.AdaptiveLuminanceGlassContent
import com.kyant.backdrop.catalog.destinations.BottomTabsContent
import com.kyant.backdrop.catalog.destinations.ButtonsContent
import com.kyant.backdrop.catalog.destinations.ColorOsNativeComparisonContent
import com.kyant.backdrop.catalog.destinations.ControlCenterContent
import com.kyant.backdrop.catalog.destinations.DialogContent
import com.kyant.backdrop.catalog.destinations.GlassPlaygroundContent
import com.kyant.backdrop.catalog.destinations.HomeContent
import com.kyant.backdrop.catalog.destinations.LazyScrollContainerContent
import com.kyant.backdrop.catalog.destinations.LockScreenContent
import com.kyant.backdrop.catalog.destinations.MagnifierContent
import com.kyant.backdrop.catalog.destinations.ProgressiveBlurContent
import com.kyant.backdrop.catalog.destinations.ScrollContainerContent
import com.kyant.backdrop.catalog.destinations.SliderContent
import com.kyant.backdrop.catalog.destinations.ToggleContent
import com.kyant.backdrop.catalog.utils.BackHandler

@Composable
fun MainContent() {
    val isLightTheme = !isSystemInDarkTheme()

    CompositionLocalProvider(
        LocalIndication provides ripple(color = if (isLightTheme) Color.Black else Color.White)
    ) {
        var destination by rememberSaveable { mutableStateOf(CatalogDestination.Home) }

        // Android first tries the semantic ColorOS mapping. The older platform
        // route remains as a compatibility/diagnostic fallback but is no longer
        // reached for mapped Android destinations.
        val semanticHandled = SemanticColorOsCatalogContent(
            destination = destination,
            onNavigate = { destination = it },
        )
        val platformHandled = if (semanticHandled) {
            true
        } else {
            PlatformCatalogContent(
                destination = destination,
                onNavigate = { destination = it },
            )
        }

        if (!platformHandled) {
            when (destination) {
                CatalogDestination.Home -> HomeContent(onNavigate = { destination = it })

                CatalogDestination.Buttons -> ButtonsContent()
                CatalogDestination.Toggle -> ToggleContent()
                CatalogDestination.Slider -> SliderContent()
                CatalogDestination.BottomTabs -> BottomTabsContent()
                CatalogDestination.Dialog -> DialogContent()

                CatalogDestination.LockScreen -> LockScreenContent()
                CatalogDestination.ControlCenter -> ControlCenterContent()
                CatalogDestination.Magnifier -> MagnifierContent()

                CatalogDestination.GlassPlayground -> GlassPlaygroundContent()
                CatalogDestination.ColorOsNativeComparison -> ColorOsNativeComparisonContent()
                CatalogDestination.AdaptiveLuminanceGlass -> AdaptiveLuminanceGlassContent()
                CatalogDestination.ProgressiveBlur -> ProgressiveBlurContent()
                CatalogDestination.ScrollContainer -> ScrollContainerContent()
                CatalogDestination.LazyScrollContainer -> LazyScrollContainerContent()
            }
        }

        BackHandler(destination != CatalogDestination.Home) {
            destination = CatalogDestination.Home
        }
    }
}
