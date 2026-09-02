package com.qmurzik.animetv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.VideoQuality
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.theme.TvBackground

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = TvSafeHorizontalPadding, vertical = 32.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium) }

        item {
            SettingsSection(title = stringResource(R.string.settings_playback)) {
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_quality),
                    options = VideoQuality.entries.map { it to qualityLabel(it) },
                    selected = settings.playback.preferredQuality,
                    onSelect = viewModel::setQuality,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_autoplay_next),
                    checked = settings.playback.autoPlayNext,
                    onCheckedChange = viewModel::setAutoPlayNext,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_skip_opening),
                    checked = settings.playback.skipOpening,
                    onCheckedChange = viewModel::setSkipOpening,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_skip_ending),
                    checked = settings.playback.skipEnding,
                    onCheckedChange = viewModel::setSkipEnding,
                )
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_preferred_source),
                    options = listOf(
                        null to stringResource(R.string.source_auto),
                        "jikan" to stringResource(R.string.source_jikan),
                        "anilist" to stringResource(R.string.source_anilist),
                    ),
                    selected = settings.playback.preferredSourceId,
                    onSelect = viewModel::setPreferredSource,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_language),
                    options = listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.language_system),
                        AppLanguage.ENGLISH to stringResource(R.string.language_english),
                        AppLanguage.RUSSIAN to stringResource(R.string.language_russian),
                    ),
                    selected = settings.appearance.language,
                    onSelect = viewModel::setLanguage,
                )
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_text_size),
                    options = listOf(0.9f to "S", 1.0f to "M", 1.15f to "L", 1.3f to "XL"),
                    selected = settings.appearance.textScale,
                    onSelect = viewModel::setTextScale,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_animations),
                    checked = settings.appearance.animationsEnabled,
                    onCheckedChange = viewModel::setAnimationsEnabled,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_network)) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_wifi_only),
                    checked = settings.network.wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_storage)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(text = stringResource(R.string.action_clear_image_cache), onClick = viewModel::clearImageCache)
                    TvButton(text = stringResource(R.string.action_clear_metadata_cache), onClick = viewModel::clearMetadataCache)
                    TvButton(text = stringResource(R.string.action_clear_history), onClick = viewModel::clearHistory)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (value, optionLabel) ->
                TvButton(text = optionLabel, primary = value == selected, onClick = { onSelect(value) })
            }
        }
    }
}

@Composable
private fun qualityLabel(quality: VideoQuality): String = when (quality) {
    VideoQuality.AUTO -> stringResource(R.string.quality_auto)
    VideoQuality.HIGH -> stringResource(R.string.quality_high)
    VideoQuality.MEDIUM -> stringResource(R.string.quality_medium)
    VideoQuality.LOW -> stringResource(R.string.quality_low)
}
