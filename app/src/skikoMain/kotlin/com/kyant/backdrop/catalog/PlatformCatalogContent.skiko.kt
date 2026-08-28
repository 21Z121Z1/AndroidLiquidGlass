package com.kyant.backdrop.catalog

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformCatalogContent(
    destination: CatalogDestination,
    onNavigate: (CatalogDestination) -> Unit,
): Boolean = false
