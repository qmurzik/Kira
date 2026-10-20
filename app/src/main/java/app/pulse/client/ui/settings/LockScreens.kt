package app.pulse.client.ui.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

fun biometricsAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Shown over the whole app while it is locked. */
@Composable
fun LockScreen(biometricEnabled: Boolean) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val canBio = biometricEnabled && biometricsAvailable(context)

    fun askBiometrics() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    container.appLock.unlockedByBiometrics()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Разблокировка")
                .setNegativeButtonText("Ввести PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }

    LaunchedEffect(canBio) { if (canBio) askBiometrics() }

    PinPad(
        title = if (error) "Неверный PIN-код" else "Введите PIN-код",
        pin = pin,
        isError = error,
        onDigit = { d ->
            if (pin.length < PIN_LENGTH) {
                pin += d
                error = false
                if (pin.length == PIN_LENGTH) {
                    if (!container.appLock.verify(pin)) {
                        error = true
                        pin = ""
                    }
                }
            }
        },
        onDelete = { pin = pin.dropLast(1) },
        extra = if (canBio) ({ askBiometrics() }) else null,
    )
}

/** Create (twice) a new PIN. */
@Composable
fun PinSetupScreen() {
    val container = LocalAppContainer.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("PIN-код") }) { padding ->
        Box(Modifier.padding(padding)) {
            PinPad(
                title = when {
                    mismatch -> "PIN-коды не совпали, придумайте заново"
                    first == null -> "Придумайте PIN-код"
                    else -> "Повторите PIN-код"
                },
                pin = pin,
                isError = mismatch,
                onDigit = { d ->
                    if (pin.length < PIN_LENGTH) pin += d
                    if (pin.length == PIN_LENGTH) {
                        val f = first
                        when {
                            f == null -> {
                                first = pin
                                mismatch = false
                            }
                            f == pin -> {
                                container.appLock.setPin(pin)
                                scope.launch {
                                    container.settings.update { it.copy(pinEnabled = true) }
                                    nav.back()
                                }
                            }
                            else -> {
                                first = null
                                mismatch = true
                            }
                        }
                        pin = ""
                    }
                },
                onDelete = { pin = pin.dropLast(1) },
                extra = null,
            )
        }
    }
}

@Composable
private fun PinPad(
    title: String,
    pin: String,
    isError: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    extra: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (key.length == 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                            .clickable(enabled = key.length == 1 || key == "del" || (key == "bio" && extra != null)) {
                                when (key) {
                                    "del" -> onDelete()
                                    "bio" -> extra?.invoke()
                                    else -> onDigit(key)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "del" -> Icon(Icons.Outlined.Backspace, "Стереть")
                            "bio" -> if (extra != null) Icon(Icons.Outlined.Fingerprint, "Биометрия")
                            else -> Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}
