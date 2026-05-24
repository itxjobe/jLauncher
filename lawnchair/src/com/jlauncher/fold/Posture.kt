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
import androidx.window.layout.FoldingFeature

/**
 * Coarse-grained device posture derived from [FoldingFeature].
 *
 * Flat     — not a foldable, or foldable fully open / fully closed.
 * Book     — half-open with a vertical hinge: left/right page split.
 * TableTop — half-open with a horizontal hinge: top/bottom split.
 *
 * The hinge [bounds] are in window coordinates; layout consumers should pad / inset
 * around them to avoid placing UI directly under the hinge.
 */
sealed class Posture {
    object Flat : Posture()
    data class Book(val bounds: Rect, val isSeparating: Boolean) : Posture()
    data class TableTop(val bounds: Rect, val isSeparating: Boolean) : Posture()

    companion object {
        fun from(feature: FoldingFeature?): Posture {
            if (feature == null || feature.state != FoldingFeature.State.HALF_OPENED) {
                return Flat
            }
            return when (feature.orientation) {
                FoldingFeature.Orientation.VERTICAL ->
                    Book(feature.bounds, feature.isSeparating)
                FoldingFeature.Orientation.HORIZONTAL ->
                    TableTop(feature.bounds, feature.isSeparating)
                else -> Flat
            }
        }
    }
}
