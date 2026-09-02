package com.qmurzik.animetv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.VideoQuality
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.theme.TvBackground

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(TvBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.LANGUAGE -> LanguageStep(state.language, viewModel::setLanguage)
                OnboardingStep.QUALITY -> QualityStep(state.quality, viewModel::setQuality)
                OnboardingStep.SOURCE -> SourceStep(state.preferredSourceId, viewModel::setPreferredSource)
            }

            Row(modifier = Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    text = stringResource(R.string.action_continue),
                    primary = true,
                    onClick = { viewModel.next(onFinished) },
                )
                TvButton(
                    text = stringResource(R.string.action_skip),
                    onClick = { viewModel.skip(onFinished) },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun LanguageStep(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Text(text = stringResource(R.string.onboarding_language_title), style = MaterialTheme.typography.headlineMedium)
    Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TvButton(
            text = stringResource(R.string.language_system),
            primary = selected == AppLanguage.SYSTEM,
            onClick = { onSelect(AppLanguage.SYSTEM) },
        )
        TvButton(
            text = stringResource(R.string.language_english),
            primary = selected == AppLanguage.ENGLISH,
            onClick = { onSelect(AppLanguage.ENGLISH) },
        )
        TvButton(
            text = stringResource(R.string.language_russian),
            primary = selected == AppLanguage.RUSSIAN,
            onClick = { onSelect(AppLanguage.RUSSIAN) },
        )
    }
}

@Composable
private fun QualityStep(selected: VideoQuality, onSelect: (VideoQuality) -> Unit) {
    Text(text = stringResource(R.string.onboarding_quality_title), style = MaterialTheme.typography.headlineMedium)
    Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        VideoQuality.entries.forEach { quality ->
            TvButton(
                text = quality.name,
                primary = quality == selected,
                onClick = { onSelect(quality) },
            )
        }
    }
}

@Composable
private fun SourceStep(selected: String?, onSelect: (String?) -> Unit) {
    Text(text = stringResource(R.string.onboarding_source_title), style = MaterialTheme.typography.headlineMedium)
    Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TvButton(
            text = stringResource(R.string.source_auto),
            primary = selected == null,
            onClick = { onSelect(null) },
        )
        TvButton(
            text = stringResource(R.string.source_jikan),
            primary = selected == "jikan",
            onClick = { onSelect("jikan") },
        )
        TvButton(
            text = stringResource(R.string.source_anilist),
            primary = selected == "anilist",
            onClick = { onSelect("anilist") },
        )
    }
}
