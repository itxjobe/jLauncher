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

import android.util.Log
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adapter that exposes the system unfold progress as a Kotlin [Flow] of
 * `Float` in `[0, 1]`, matching the contract of
 * [UnfoldTransitionProgressProvider.TransitionProgressListener.onTransitionProgress]
 * (0 = fully folded, 1 = fully unfolded).
 *
 * The launcher activity itself is the source: [QuickstepLauncher] already
 * builds a [UnfoldTransitionProgressProvider] in its `onCreate` and exposes
 * it via [QuickstepLauncher.getUnfoldTransitionProgressProvider]. We just
 * subscribe.
 *
 * The provider can be null when the device does not have system unfold
 * animation support (the underlying `ResourceUnfoldTransitionConfig`
 * reads `config_unfoldTransitionEnabled` from `android:bool` and returns
 * `false` on most non-foldable hardware). In that case [progress] returns
 * `null` and the controller falls back to a fixed-duration animation.
 *
 * The flow emits the START value once on subscribe so consumers know the
 * initial state, then forwards each frame's progress, then completes after
 * `onTransitionFinished`. Cancellation removes the underlying listener
 * cleanly.
 */
class FoldProgressSource(private val launcher: QuickstepLauncher) {

    /**
     * @return a flow of fold progress, or `null` if this launcher has no
     *         attached progress provider (animation disabled by system config).
     */
    fun progress(): Flow<Float>? {
        val provider = launcher.unfoldTransitionProgressProvider ?: return null
        Log.i(TAG, "F3a progress provider attached: ${provider.javaClass.simpleName}")
        return callbackFlow {
            val listener = object : TransitionProgressListener {
                override fun onTransitionStarted() {
                    Log.i(TAG, "F3a transition started")
                }

                override fun onTransitionProgress(progress: Float) {
                    trySend(progress)
                }

                override fun onTransitionFinished() {
                    Log.i(TAG, "F3a transition finished")
                    close()
                }
            }
            provider.addCallback(listener)
            awaitClose { provider.removeCallback(listener) }
        }
    }

    companion object {
        private const val TAG = "jLauncherFold"
    }
}
