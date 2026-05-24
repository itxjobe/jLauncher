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

import android.app.Activity
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware observer of the current device [Posture].
 *
 * Collects [WindowInfoTracker.windowLayoutInfo] while the host is at least STARTED
 * and exposes the latest posture via [posture]. Also logs every transition with tag
 * [TAG] so device fold/unfold can be verified end-to-end via:
 *
 *     adb logcat -s jLauncherFold
 *
 * Wire one instance per Launcher activity in `onCreate` and call [start].
 */
class PostureObserver<T>(private val host: T) where T : Activity, T : LifecycleOwner {

    private val _posture = MutableStateFlow<Posture>(Posture.Flat)
    val posture: StateFlow<Posture> get() = _posture

    fun start() {
        host.lifecycleScope.launch {
            host.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(host)
                    .windowLayoutInfo(host)
                    .map { info ->
                        Posture.from(
                            info.displayFeatures
                                .filterIsInstance<FoldingFeature>()
                                .firstOrNull()
                        )
                    }
                    .distinctUntilChanged()
                    .collect { next ->
                        _posture.value = next
                        Log.i(TAG, "posture=$next")
                    }
            }
        }
    }

    companion object {
        const val TAG = "jLauncherFold"
    }
}
