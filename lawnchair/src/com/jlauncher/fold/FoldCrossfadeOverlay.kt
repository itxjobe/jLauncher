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

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Two-layer crossfade overlay used during the F3a fold transition.
 *
 * Holds a `source` ImageView (the workspace as it looked BEFORE the posture
 * change) on the bottom and a `target` ImageView (the workspace as it looks
 * AFTER the swap) on top. Both fill the parent. [setProgress] drives their
 * alphas through [FoldCrossfadeMath], so the layout's alphas always sum to
 * 1.0 in the valid range.
 *
 * The overlay starts with both alphas at 0 so it stays invisible until both
 * bitmaps are loaded and `setProgress` is called at least once. This
 * mitigates the PixelCopy first-frame race (snapshot may arrive 1 frame
 * after the transition starts) noted in the F3a engineering review.
 *
 * Self-contained: no Android dependencies beyond what's required for view
 * inflation. Test in isolation by feeding it two static bitmaps and a
 * SeekBar.
 */
class FoldCrossfadeOverlay(context: Context) : FrameLayout(context) {

    private val sourceView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        alpha = 0f
    }
    private val targetView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        alpha = 0f
    }

    init {
        addView(
            sourceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            targetView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        // Block touch events from reaching the (real) workspace underneath
        // while the overlay is animating - the layout is mid-transition and
        // tapping anywhere should be a no-op.
        isClickable = true
        isFocusable = false
    }

    fun setSourceBitmap(bitmap: Bitmap?) {
        sourceView.setImageBitmap(bitmap)
        sourceView.visibility = if (bitmap == null) View.GONE else View.VISIBLE
    }

    fun setTargetBitmap(bitmap: Bitmap?) {
        targetView.setImageBitmap(bitmap)
        targetView.visibility = if (bitmap == null) View.GONE else View.VISIBLE
    }

    /**
     * @param progress crossfade progress in `[0, 1]` (source -> target).
     *                 0 = source visible, 1 = target visible. Out-of-range
     *                 values are clamped by [FoldCrossfadeMath].
     */
    fun setProgress(progress: Float) {
        val (alphaSource, alphaTarget) = FoldCrossfadeMath.alphas(progress)
        sourceView.alpha = alphaSource
        targetView.alpha = alphaTarget
    }
}
