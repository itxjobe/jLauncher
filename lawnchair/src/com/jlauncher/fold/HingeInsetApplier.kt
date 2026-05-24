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
import android.view.View
import androidx.annotation.RequiresApi
import com.android.launcher3.Launcher

/**
 * F1 — hinge avoidance.
 *
 * Computes the padding the workspace and hotseat should adopt so that no cell
 * sits beneath the device hinge in [Posture.Book] or [Posture.TableTop] when
 * the fold is reported as separating.
 *
 * Strategy (MVP): for a separating posture, push content to one side of the
 * hinge by adding padding equal to the hinge bounds on the opposite edge.
 * Launcher3 owns layout via [com.android.launcher3.DeviceProfile.workspacePadding],
 * so this padding may be partially overridden on the next measurement pass — a
 * deeper hook through DeviceProfile lands in a later phase. For now the wiring
 * proves the posture flow reaches the views and the calculations land.
 */
object HingeInsetApplier {

    private const val TAG = "jLauncherFold"

    fun apply(launcher: Launcher, posture: Posture) {
        // Foldable hardware all runs API 30+; currentWindowMetrics requires R.
        // No-op on older devices (they cannot enter Book/TableTop anyway).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val workspace = launcher.workspace ?: return
        val hotseat = launcher.hotseat ?: return

        val inset = insetFor(launcher, posture)
        Log.i(TAG, "F1 apply: posture=$posture inset=$inset")

        applyPadding(workspace, inset)
        applyPadding(hotseat, inset)
    }

    /**
     * Calculate inset rect for [posture] in window coordinates. Returns an empty rect
     * when no avoidance is needed.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun insetFor(launcher: Launcher, posture: Posture): Rect = when (posture) {
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

    private fun applyPadding(view: View, inset: Rect) {
        view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
        view.requestLayout()
    }
}
