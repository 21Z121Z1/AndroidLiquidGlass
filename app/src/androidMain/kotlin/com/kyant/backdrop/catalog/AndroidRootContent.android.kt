package com.kyant.backdrop.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable

/** Android root: normal catalog plus exhaustive and direct-execution SystemUI labs. */
@Composable
fun AndroidRootContent() {
    Box {
        MainContent()
        SystemUiExhaustiveLabOverlay()
        SystemUiDeepDiveOverlay()
        SystemUiBlurMixOverlay()
        SystemUiBusinessViewsOverlay()
        SystemUiScopedAuditOverlay()
    }
}
