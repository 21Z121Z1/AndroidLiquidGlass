package com.kyant.backdrop.catalog.destinations

import androidx.compose.runtime.Composable

/**
 * Platform entry point for the ColorOS native-material comparison.
 *
 * Android provides the real runtime bridge. Other targets deliberately render
 * an unsupported message instead of substituting a lookalike implementation.
 */
@Composable
expect fun ColorOsNativeComparisonContent()
