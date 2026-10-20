package app.pulse.client.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.pulse.client.di.AppContainer
import app.pulse.client.di.UserSession

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSession = staticCompositionLocalOf<UserSession> { error("No active session") }
