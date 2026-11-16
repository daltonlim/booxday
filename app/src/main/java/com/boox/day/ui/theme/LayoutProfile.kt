@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.boox.day.ui.theme

import android.content.res.Resources
import com.boox.day.AppDensity

/**
 * Screen layout profile (plan Phase 1.5, Oct 2026).
 *
 * - [Regular]: the original layout (calendar month grid + events panel above the page).
 * - [Compact]: the Go 6 "layout B" (a header, a two-week strip and Events/Due boxes above the page;
 *   month grid and lists as popups). Retired for tablets on 13 Oct 2026: narrow tablets adapt sizes
 *   only, through [LayoutProfiles.isNarrowScreen]. Pocket screens (Palma) use it again (Nov 2026)
 *   with a one-week strip: the calendar switches between that week, the month over the page and
 *   none (full screen).
 *
 * Fixed per device: the app is portrait-locked and runs at the fixed [AppDensity.dpi], so
 * nothing here changes at runtime. That matters because the Tasks rows (canvas height ÷ 10) and the
 * control-column boundary must never move under existing ink.
 */
enum class LayoutProfile { Regular, Compact }

object LayoutProfiles {
    /** Screens narrower than this (in dp at the app density) are narrow screens. */
    private const val NARROW_MAX_WIDTH_DP = 600f

    /** Screens narrower than this (in dp at the app density) are pocket screens. */
    private const val POCKET_MAX_WIDTH_DP = 500f

    /** Compact on pocket screens, Regular on every tablet. */
    val current: LayoutProfile by lazy { if (isPocketScreen) LayoutProfile.Compact else LayoutProfile.Regular }

    /** Layout B (see [LayoutProfile]): pocket screens only. */
    val isCompact: Boolean
        get() = current == LayoutProfile.Compact

    /**
     * Narrow screens such as the BOOX Go 6 (572 dp wide at 300 dpi): same layout, adapted sizes
     * (smaller popups, the 116 dp Tasks control column, the toolbar that moves what doesn't fit into
     * the toolbar menu). The Go 10.3 is 773 dp wide at 385 dpi.
     */
    val isNarrowScreen: Boolean by lazy { screenWidthDp < NARROW_MAX_WIDTH_DP }

    /**
     * Phone-sized screens such as the BOOX Palma (824 px wide, 439 dp at 300 dpi): narrow screens
     * where even the six narrow header buttons don't fit, so the header keeps three and moves the
     * rest into a ⋮ popup.
     */
    val isPocketScreen: Boolean by lazy { screenWidthDp < POCKET_MAX_WIDTH_DP }

    /**
     * Tablets smaller than 10.3 inches (Go 6, Go 7: [AppDensity.isSmallScreen]). Not the same as
     * [isNarrowScreen]: the Go 7 is 674 dp wide, but its calendar area is as short as the Go 6's.
     */
    val isSmallTablet: Boolean
        get() = AppDensity.isSmallScreen

    /**
     * Portrait screen size in dp at the app density (Go 6: 572 x 772, Go 10.3: 773 x 1031); fixed per
     * device. Popup size tokens in EInkTheme follow it (Oct 2026).
     */
    val screenWidthDp: Float by lazy { screenSidePx(short = true) / (AppDensity.dpi / 160f) }
    val screenHeightDp: Float by lazy { screenSidePx(short = false) / (AppDensity.dpi / 160f) }

    private fun screenSidePx(short: Boolean): Int {
        // System metrics are the physical display (the app overrides only its own density).
        // The short side is the portrait width even if the device was held in landscape at launch.
        val metrics = Resources.getSystem().displayMetrics
        return if (short) minOf(metrics.widthPixels, metrics.heightPixels) else maxOf(metrics.widthPixels, metrics.heightPixels)
    }
}
