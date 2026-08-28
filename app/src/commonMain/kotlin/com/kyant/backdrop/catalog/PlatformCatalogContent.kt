package com.kyant.backdrop.catalog

import androidx.compose.runtime.Composable

/**
 * Platform catalog replacement hook.
 *
 * Android uses this to route every material demo through the installed
 * ColorOS implementation. Other KMP targets return false and keep the upstream
 * generic Backdrop demos.
 */
@Composable
expect fun PlatformCatalogContent(
    destination: CatalogDestination,
    onNavigate: (CatalogDestination) -> Unit,
): Boolean
