/*
 * Copyright 2026 itxjobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

/**
 * jLauncher F6 - folded-layout grid editor.
 *
 * Mirrors [HomeScreenGridPreferences] but writes the three folded-posture
 * prefs (`workspaceColumnsFolded`, `workspaceRowsFolded`, `hotseatColumnsFolded`)
 * that [com.jlauncher.fold.PostureGridOverride] reads when the device is in a
 * separating Book or TableTop posture. Independent from the unfolded grid
 * editor; saved arrangements per posture persist independently.
 *
 * No live preview yet (GridOverridesPreview is keyed off the active IDP,
 * which is the unfolded grid while editing). Applying writes the prefs and
 * calls `onPreferencesChanged` so the change takes effect on the next fold.
 */
@Composable
fun FoldedScreenGridPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.folded_layout),
        modifier = modifier,
        isExpandedScreen = true,
    ) {
        val prefs = preferenceManager()
        val columnsAdapter = prefs.workspaceColumnsFolded.getAdapter()
        val rowsAdapter = prefs.workspaceRowsFolded.getAdapter()
        val hotseatAdapter = prefs.hotseatColumnsFolded.getAdapter()

        val originalColumns = remember { columnsAdapter.state.value }
        val originalRows = remember { rowsAdapter.state.value }
        val originalHotseat = remember { hotseatAdapter.state.value }
        val columns = rememberSaveable { mutableIntStateOf(originalColumns) }
        val rows = rememberSaveable { mutableIntStateOf(originalRows) }
        val hotseat = rememberSaveable { mutableIntStateOf(originalHotseat) }

        PreferenceGroup(description = stringResource(id = R.string.folded_layout_description)) {
            SliderPreference(
                label = stringResource(id = R.string.columns),
                adapter = columns.asPreferenceAdapter(),
                step = 1,
                valueRange = 1..6,
            )
            SliderPreference(
                label = stringResource(id = R.string.rows),
                adapter = rows.asPreferenceAdapter(),
                step = 1,
                valueRange = 3..8,
            )
            SliderPreference(
                label = stringResource(id = R.string.folded_hotseat),
                adapter = hotseat.asPreferenceAdapter(),
                step = 1,
                valueRange = 1..6,
            )
        }

        val navController = LocalNavController.current
        val context = LocalContext.current
        val applyOverrides = {
            prefs.batchEdit {
                columnsAdapter.onChange(columns.intValue)
                rowsAdapter.onChange(rows.intValue)
                hotseatAdapter.onChange(hotseat.intValue)
            }
            LauncherAppState.getIDP(context).onPreferencesChanged(context)
            navController.popBackStack()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp),
        ) {
            Button(
                onClick = { applyOverrides() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(),
                enabled = columns.intValue != originalColumns ||
                    rows.intValue != originalRows ||
                    hotseat.intValue != originalHotseat,
            ) {
                Text(text = stringResource(id = R.string.action_apply))
            }
        }
    }
}
