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

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.PixelCopy.OnPixelCopyFinishedListener
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.launcher3.uioverrides.QuickstepLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * F3a fold-transition controller.
 *
 * Watches [posture] for transitions, snapshots the workspace before and
 * after the layout swap, inserts a [FoldCrossfadeOverlay] above the
 * launcher's content, and animates the alpha crossfade.
 *
 * Two animation drivers, in priority order:
 *  1. System unfold progress (via [FoldProgressSource]) - real per-frame
 *     callbacks tied to the physical fold motion. Used when available.
 *  2. Fixed-duration fallback (300ms ValueAnimator) - used when the system
 *     does not expose unfold progress (config_unfoldTransitionEnabled=false).
 *
 * Snapshot strategy: per the F3a engineering review D2 decision, snapshot
 * during the swap and accept that the first fold per posture per session
 * may show snap-then-crossfade. The overlay starts at alpha=0 and only
 * becomes visible once both bitmaps are loaded, so a missing bitmap shows
 * nothing rather than a half-rendered frame.
 *
 * Cancel-in-flight guard: if a second posture transition arrives while a
 * first crossfade is still animating, the in-flight animation is cancelled
 * and the overlay is torn down before the new transition starts. Prevents
 * black flashes from overlapping bitmap captures.
 *
 * @param launcher  the activity. Used as Context, LifecycleOwner, and view
 *                  parent. Cast to QuickstepLauncher to read the system
 *                  unfold progress provider.
 * @param posture   StateFlow of [Posture] from [PostureObserver]. The first
 *                  emission is dropped (the initial Flat value is not a
 *                  transition).
 */
class FoldTransitionController<T>(
    private val launcher: T,
    private val posture: StateFlow<Posture>,
) where T : QuickstepLauncher, T : LifecycleOwner {

    private val window: Window = launcher.window
    private val rootView: ViewGroup get() = window.decorView as ViewGroup

    private val progressSource: Flow<Float>? = FoldProgressSource(launcher).progress()

    private val pixelCopyThread = HandlerThread("jLauncherFoldPixelCopy").apply { start() }
    private val pixelCopyHandler = Handler(pixelCopyThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeOverlay: FoldCrossfadeOverlay? = null
    private var activeAnimationJob: Job? = null
    private var fallbackAnimator: ValueAnimator? = null

    /**
     * Start watching for posture transitions. Safe to call once from
     * [com.android.launcher3.Launcher.onCreate]; the coroutine cancels with
     * the launcher's lifecycle.
     */
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.i(TAG, "F3a: API < O, skipping")
            return
        }
        Log.i(TAG, "F3a controller started; progress source=${progressSource != null}")
        launcher.lifecycleScope.launch {
            launcher.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                posture
                    .drop(1) // initial Flat is not a transition
                    .distinctUntilChanged()
                    .collect { next -> onPostureTransition(next) }
            }
        }
    }

    @MainThread
    private fun onPostureTransition(next: Posture) {
        Log.i(TAG, "F3a transition -> $next")
        cancelInFlight()
        captureSnapshot { sourceBitmap ->
            // Source captured. The PostureObserver subscription in
            // LawnchairLauncher.onCreate has already run by the time we
            // get here and will trigger the IDP / DP rebuild. Wait one
            // frame for layout to settle, then capture the target.
            mainHandler.post {
                captureSnapshot { targetBitmap ->
                    if (sourceBitmap == null && targetBitmap == null) {
                        Log.w(TAG, "F3a: both snapshots failed, skipping crossfade")
                        return@captureSnapshot
                    }
                    installOverlay(sourceBitmap, targetBitmap)
                }
            }
        }
    }

    @MainThread
    private fun installOverlay(source: Bitmap?, target: Bitmap?) {
        val overlay = FoldCrossfadeOverlay(launcher).apply {
            setSourceBitmap(source)
            setTargetBitmap(target)
            setProgress(0f)
        }
        rootView.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activeOverlay = overlay
        Log.i(TAG, "F3a overlay installed")

        // F3a v2: always drive via the fallback timer for visual stability.
        // The system progress provider is captured by FoldProgressSource and
        // ready to wire in F3a v3 once the overlay pipeline is verified on
        // real hardware. Correlating system progress to crossfade direction
        // needs explicit transition-direction tracking (folding vs unfolding)
        // that we do not have yet; the fallback's 300ms ValueAnimator gives
        // a clean source -> target fade regardless.
        driveWithFallbackTimer(overlay)
    }

    @MainThread
    private fun driveWithFallbackTimer(overlay: FoldCrossfadeOverlay) {
        fallbackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FALLBACK_DURATION_MS
            addUpdateListener { animator ->
                overlay.setProgress(animator.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    tearDown()
                }
            })
            start()
        }
    }

    @MainThread
    private fun cancelInFlight() {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        fallbackAnimator?.cancel()
        fallbackAnimator = null
        activeOverlay?.let { rootView.removeView(it) }
        activeOverlay = null
    }

    @MainThread
    private fun tearDown() {
        activeOverlay?.let { overlay ->
            rootView.removeView(overlay)
            overlay.setSourceBitmap(null)
            overlay.setTargetBitmap(null)
            Log.i(TAG, "F3a overlay torn down")
        }
        activeOverlay = null
        activeAnimationJob = null
        fallbackAnimator = null
    }

    /**
     * Capture the launcher window via [PixelCopy] off the main thread.
     * Wraps the result in a 200ms timeout - on failure, [onResult] is
     * called with `null` so the controller can degrade gracefully rather
     * than hang the transition.
     *
     * `PixelCopy` was chosen over `View.draw(Canvas)` per the engineering
     * review D1 decision: View.draw blocks the main thread for ~30-100ms
     * on a hi-res foldable, which is exactly the wrong artifact at the
     * exact wrong moment. PixelCopy off-thread keeps the main thread
     * responsive.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun captureSnapshot(onResult: (Bitmap?) -> Unit) {
        val width = rootView.width
        val height = rootView.height
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "F3a: root view not laid out, snapshot skipped")
            onResult(null)
            return
        }

        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "F3a: bitmap allocation OOM (${width}x${height})", oom)
            onResult(null)
            return
        }

        var resolved = false
        val resolve = { result: Bitmap? ->
            if (!resolved) {
                resolved = true
                if (result == null) bitmap.recycle()
                mainHandler.post { onResult(result) }
            }
        }

        val timeout = Runnable {
            Log.w(TAG, "F3a: PixelCopy timeout")
            resolve(null)
        }
        mainHandler.postDelayed(timeout, PIXEL_COPY_TIMEOUT_MS)

        val listener = OnPixelCopyFinishedListener { copyResult ->
            mainHandler.removeCallbacks(timeout)
            if (copyResult == PixelCopy.SUCCESS) {
                resolve(bitmap)
            } else {
                Log.w(TAG, "F3a: PixelCopy failed code=$copyResult")
                resolve(null)
            }
        }

        try {
            PixelCopy.request(window, bitmap, listener, pixelCopyHandler)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "F3a: PixelCopy.request rejected", e)
            mainHandler.removeCallbacks(timeout)
            resolve(null)
        }
    }

    companion object {
        private const val TAG = "jLauncherFold"
        private const val PIXEL_COPY_TIMEOUT_MS = 200L
        private const val FALLBACK_DURATION_MS = 300L
    }
}
