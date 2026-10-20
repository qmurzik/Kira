package app.pulse.client

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.pulse.client.ui.AppRoot

/** Single activity. FragmentActivity is required by BiometricPrompt (app lock). */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PulseApp).container
        setContent { AppRoot(container) }
    }
}
