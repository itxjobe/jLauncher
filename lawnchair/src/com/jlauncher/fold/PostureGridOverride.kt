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

/**
 * F6 - posture-aware grid dimensions, hinge-aware padding, AND per-posture
 * favorites database files for independent saved home-screen layouts.
 *
 * Two halves:
 *
 *  - [applyToIdp] mutates [InvariantDeviceProfile] during `initGrid` to (a)
 *    swap in posture-specific row/column/hotseat counts read from prefs and
 *    (b) repoint `idp.dbFile` to a posture-scoped SQLite file
 *    (`launcher_<rows>_<cols>_<hot>_folded.db` in the separating-fold case,
 *    the unmodified upstream filename in the flat case). Launcher3's existing
 *    `ModelDbController.migrateGridIfNeeded()` notices the dbFile change and
 *    opens the corresponding database, running grid migration the first time
 *    each posture is observed and then leaving each database alone on future
 *    swaps. Result: the user's unfolded arrangement and folded arrangement
 *    persist independently across fold events.
 *
 *  - [applyToDp] mutates [DeviceProfile.workspacePadding] to push workspace
 *    cells off the hinge. Unchanged from F2, kept for layout safety while the
 *    independently-persisted folded layout is being built up.
 *
 * Posture mapping:
 *  - Flat: no override. Unfolded prefs and unfolded dbFile are used.
 *  - Book / Separating  (vertical hinge): folded columns + folded hotseat
 *    substitute in; rows stay at the unfolded value.
 *  - TableTop / Separating (horizontal hinge): folded rows substitute in;
 *    columns + hotseat stay unfolded. Rare on a phone-format foldable, kept
 *    for symmetry.
 *  - Book / TableTop without `isSeparating`: no override. The device is
 *    either fully open (-> Flat) or close enough that the launcher should
 *    behave normally.
 *
 * Single-display assumption: this override runs inside the launcher activity
 * on the active display. Per-display IDPs (e.g. Razr cover screen vs main
 * display) are already handled upstream by Launcher3's display management;
 * F6 layers on top of whichever IDP is active.
 */
object PostureGridOverride {

    private const val TAG = "jLauncherFold"

    /**
     * Folded-posture grid configuration read from prefs at IDP-rebuild time.
     * Passed in rather than read inside this object so the prefs read happens
     * on the same thread as the rest of [DeviceProfileOverrides.Options].
     */
    data class FoldedConfig(
        val numRows: Int,
        val numColumns: Int,
        val numHotseatIcons: Int,
    )

    /**
     * Mutate [idp] in place to reflect [posture] and [foldedConfig].
     *
     * Idempotent under repeated calls with the same posture because
     * `initGrid` rewrites `idp` from the XML grid defaults before this hook
     * runs - successive calls see the same starting values.
     */
    fun applyToIdp(
        idp: InvariantDeviceProfile,
        posture: Posture,
        foldedConfig: FoldedConfig,
    ) {
        when (posture) {
            Posture.Flat -> Unit
            is Posture.Book -> if (posture.isSeparating) {
                idp.numColumns = foldedConfig.numColumns.coerceAtLeast(1)
                idp.numShownHotseatIcons = foldedConfig.numHotseatIcons.coerceAtLeast(1)
                idp.dbFile = foldedDbFile(idp)
                Log.i(
                    TAG,
                    "F6 applyToIdp Book/Sep: cols=${idp.numColumns} " +
                        "hot=${idp.numShownHotseatIcons} dbFile=${idp.dbFile}",
                )
            }
            is Posture.TableTop -> if (posture.isSeparating) {
                idp.numRows = foldedConfig.numRows.coerceAtLeast(1)
                idp.dbFile = foldedDbFile(idp)
                Log.i(
                    TAG,
                    "F6 applyToIdp TableTop/Sep: rows=${idp.numRows} dbFile=${idp.dbFile}",
                )
            }
        }
    }

    /**
     * Filename for the posture-scoped favorites DB. Matches Lawnchair's own
     * convention from `DeviceProfileOverrides.DBGridInfo.dbFile` with a
     * `_folded` suffix so the file is guaranteed distinct from any unfolded
     * grid the user may have configured.
     */
    private fun foldedDbFile(idp: InvariantDeviceProfile): String =
        "launcher_${idp.numRows}_${idp.numColumns}_${idp.numShownHotseatIcons}_folded.db"

    /**
     * Mutate [dp]'s `workspacePadding` to keep workspace cells off the hinge.
     *
     * Additive - preserves padding already computed for system insets /
     * taskbar. Pre-R devices are no-ops (foldables don't exist before API 30
     * and `currentWindowMetrics` requires R).
     */
    fun applyToDp(dp: DeviceProfile, posture: Posture, launcher: Launcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val inset = computeInset(launcher, posture)
        if (inset.right == 0 && inset.bottom == 0) return
        dp.workspacePadding.right += inset.right
        dp.workspacePadding.bottom += inset.bottom
        Log.i(TAG, "F6 applyToDp posture=$posture +inset=$inset -> pad=${dp.workspacePadding}")
    }

    /**
     * Calculate inset rect for [posture] in window coordinates. Returns an
     * empty rect when no avoidance is needed. Ported verbatim from F2; only
     * the [applyToIdp] half changed for F6.
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
