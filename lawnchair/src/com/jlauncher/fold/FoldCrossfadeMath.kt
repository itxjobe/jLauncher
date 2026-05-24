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

/**
 * Pure-function math for the F3a crossfade overlay.
 *
 * Given a fold progress value in `[0, 1]` (0 = source posture fully visible,
 * 1 = target posture fully visible), produces the alpha pair to apply to the
 * source / target ImageView layers inside [FoldCrossfadeOverlay].
 *
 * No Android dependencies; safe to unit-test on the JVM once a test source set
 * is wired up. Behaviour:
 *  - input is clamped to `[0, 1]` (no NaN or out-of-range propagation)
 *  - NaN input collapses to the source layer (alphaSource = 1, alphaTarget = 0)
 *    so a broken progress signal degrades to "show what we had" instead of a
 *    black flash
 *  - alphas always sum to `1.0f` in the valid range (linear crossfade)
 *
 * Picked over a curved easing (smoothstep / cubic) for two reasons:
 *  1. The underlying progress provider already applies physics-based smoothing
 *     ([com.android.systemui.unfold.progress.PhysicsBasedUnfoldTransitionProgressProvider]),
 *     so a second easing layer would compound and feel mushy.
 *  2. F3a is a throwaway prototype; if F3b switches to per-icon translate/scale,
 *     it can swap in its own curve without inheriting one we baked in here.
 */
object FoldCrossfadeMath {

    /**
     * @param progress fold transition progress; clamped to `[0, 1]` if out of range.
     * @return `Pair(alphaSource, alphaTarget)` where both are in `[0, 1]` and
     *         sum to `1.0f` for any finite input.
     */
    fun alphas(progress: Float): Pair<Float, Float> {
        if (progress.isNaN()) return 1f to 0f
        val clamped = progress.coerceIn(0f, 1f)
        return (1f - clamped) to clamped
    }
}
