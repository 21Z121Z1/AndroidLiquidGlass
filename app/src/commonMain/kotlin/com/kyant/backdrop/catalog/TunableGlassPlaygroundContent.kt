package com.kyant.backdrop.catalog

import androidx.compose.runtime.Composable

/**
 * Android-only high-priority research route for the ColorOS liquid-glass
 * parameter playground. Other KMP targets return false and keep the upstream
 * implementation.
 */
@Composable
expect fun TunableGlassPlaygroundContent(
    destination: CatalogDestination,
): Boolean
