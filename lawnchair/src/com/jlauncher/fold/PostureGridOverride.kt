/*
 * Copyright 2026 itxjobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jlauncher.fold

import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import kotlin.math.max

/**
 * F2 — adaptive workspace grid + hinge-aware padding.
 *
 * Two halves:
 *
 *  - [applyToIdp] mutates [InvariantDeviceProfile]'s row/column counts during
 *    `initGrid`, so the per-bounds [DeviceProfile]s built afterward inherit the
 *    posture-adjusted grid shape. Hooked from
 *    `DeviceProfileOverrides.Options.applyUi(idp)` (the same insertion point
 *    Lawnchair uses for user grid preferences).
 *
 *  - [applyToDp] mutates a freshly-built [DeviceProfile]'s `workspacePadding`
 *    to push the workspace + hotseat onto the active panel of a separating
 *    fold. Hooked from `Launcher.addOnDeviceProfileChangeListener` so it runs
 *    after `Launcher.initDeviceProfile` and before `reapplyUi` — exactly when
 *    `Workspace.setInsets` re-reads `dp.workspacePadding`.
 *
 * Halving strategy (user-chosen):
 *  - Flat → no change.
 *  - Book / Separating  → halve columns (vertical hinge: keep left panel).
 *                         Hotseat icons halved to match.
 *  - TableTop / Separating → halve rows (horizontal hinge: keep top panel).
 *  - Book / TableTop without `isSeparating` → no change.
 */
object PostureGridOverride {

    private const val TAG = "jLauncherFold"

    /**
     * Mutate [idp] in place to reflect [posture].
     *
     * Safe on all API levels (only touches ints). Idempotent for a fixed
     * posture only because `initGrid` rewrites the IDP from the XML grid
     * defaults before our hook runs — successive calls with the same posture
     * therefore see the same starting values and halve the same way.
     */
    fun applyToIdp(idp: InvariantDeviceProfile, posture: Posture) {
        when (posture) {
            Posture.Flat -> Unit
            is Posture.Book -> if (posture.isSeparating) {
                idp.numColumns = max(1, idp.numColumns / 2)
                idp.numShownHotseatIcons = max(1, idp.numShownHotseatIcons / 2)
                Log.i(TAG, "F2 applyToIdp Book/Sep: cols=${idp.numColumns} hot=${idp.numShownHotseatIcons}")
            }
            is Posture.TableTop -> if (posture.isSeparating) {
                idp.numRows = max(1, idp.numRows / 2)
                Log.i(TAG, "F2 applyToIdp TableTop/Sep: rows=${idp.numRows}")
            }
        }
    }

    /**
     * Mutate [dp]'s `workspacePadding` to keep workspace cells off the hinge.
     *
     * Additive — preserves padding already computed for system insets / taskbar.
     * Pre-R devices are no-ops (foldables don't exist before API 30; also
     * `currentWindowMetrics` requires R).
     */
    fun applyToDp(dp: DeviceProfile, posture: Posture, launcher: Launcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val inset = computeInset(launcher, posture)
        if (inset.right == 0 && inset.bottom == 0) return
        dp.workspacePadding.right += inset.right
        dp.workspacePadding.bottom += inset.bottom
        Log.i(TAG, "F2 applyToDp posture=$posture +inset=$inset → pad=${dp.workspacePadding}")
    }

    /**
     * Calculate inset rect for [posture] in window coordinates. Returns an
     * empty rect when no avoidance is needed. Ported verbatim from the F1
     * implementation so behavior is preserved on the math side; only the
     * application target (DP padding instead of View.setPadding) changes.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun computeInset(launcher: Launcher, posture: Posture): Rect = when (posture) {
        Posture.Flat -> Rect()
        is Posture.Book -> {
            if (!posture.isSeparating) {
                Rect()
            } else {
                // Vertical hinge: keep content in the left panel.
                val display = launcher.windowManager.currentWindowMetrics.bounds
                Rect(0, 0, (display.width() - posture.bounds.left).coerceAtLeast(0), 0)
            }
        }
        is Posture.TableTop -> {
            if (!posture.isSeparating) {
                Rect()
            } else {
                // Horizontal hinge: keep content in the top panel.
                val display = launcher.windowManager.currentWindowMetrics.bounds
                Rect(0, 0, 0, (display.height() - posture.bounds.top).coerceAtLeast(0))
            }
        }
    }
}
