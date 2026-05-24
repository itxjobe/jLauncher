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
 * Process-singleton holding the most recently observed [Posture].
 *
 * Written by the launcher activity's posture-flow subscription and read by:
 *  - [PostureGridOverride.applyToIdp], invoked inside
 *    [com.android.launcher3.InvariantDeviceProfile.initGrid] (worker thread).
 *  - [PostureGridOverride.applyToDp], invoked from the Launcher's
 *    OnDeviceProfileChangeListener (main thread).
 *
 * Declared @Volatile because the IDP rebuild path hops executors; a torn read
 * here would only flip between two valid Posture instances, but @Volatile makes
 * the happens-before explicit so the latest write is always seen.
 */
object PostureGridRegister {
    @Volatile
    var current: Posture = Posture.Flat
}
