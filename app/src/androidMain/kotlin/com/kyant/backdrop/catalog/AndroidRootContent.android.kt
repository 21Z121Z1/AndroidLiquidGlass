package com.kyant.backdrop.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable

/** Android root: normal catalog plus the exhaustive SystemUI audit overlay. */
@Composable
fun AndroidRootContent() {
    Box {
        MainContent()
        SystemUiExhaustiveLabOverlay()
    }
}
