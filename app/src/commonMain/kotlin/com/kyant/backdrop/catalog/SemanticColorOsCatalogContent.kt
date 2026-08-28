package com.kyant.backdrop.catalog

import androidx.compose.runtime.Composable

/**
 * High-priority Android catalog route that maps every demo scene to the
 * closest semantic ColorOS material family/preset recovered from the vendor
 * packages. Other KMP targets return false and keep the upstream catalog.
 */
@Composable
expect fun SemanticColorOsCatalogContent(
    destination: CatalogDestination,
    onNavigate: (CatalogDestination) -> Unit,
): Boolean
