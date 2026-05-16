package com.project.vortex.callsagent.presentation.common

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * App-wide source of truth for the current Material3 WindowSizeClass.
 *
 * Set once at the root of the Compose tree from MainActivity using
 * `calculateWindowSizeClass(activity)`. Every screen consumes this rather
 * than recomputing or measuring its own size — one calculation, one
 * provider, single source of truth.
 *
 * Default error guards against accidental consumption outside the provider
 * scope (e.g. previews); call sites should not silently fall back.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error(
        "LocalWindowSizeClass not provided. Wrap your content with " +
            "CompositionLocalProvider(LocalWindowSizeClass provides ...).",
    )
}

/**
 * Convenience accessors so call sites don't need to import the enum types
 * just to ask a yes/no question. Keep the surface intentionally small —
 * width buckets cover ~95% of branching needs; height buckets matter mostly
 * for full-screen modals and landing screens with hero art.
 */
object WindowSize {

    val isCompactWidth: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.widthSizeClass ==
            WindowWidthSizeClass.Compact

    val isMediumWidth: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.widthSizeClass ==
            WindowWidthSizeClass.Medium

    val isExpandedWidth: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.widthSizeClass ==
            WindowWidthSizeClass.Expanded

    /**
     * True for Medium OR Expanded — the "tablet-ish" bucket. Useful when a
     * screen just needs to know "do I have horizontal room to spread out?"
     * without caring about the precise breakpoint.
     */
    val isWideWidth: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.widthSizeClass !=
            WindowWidthSizeClass.Compact

    val isCompactHeight: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.heightSizeClass ==
            WindowHeightSizeClass.Compact
}
