package com.raisethebar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.nav.RaiseTheBarRoot
import com.raisethebar.app.ui.theme.RtbTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A check-in date from a tapped notification, waiting to be opened. */
    private val checkinRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RaiseTheBarApp
        checkinRequest.value = intent?.getStringExtra(EXTRA_CHECKIN_DATE)
        setContent {
            RtbTheme {
                CompositionLocalProvider(LocalRepo provides app.repo) {
                    RaiseTheBarRoot(app.ready, checkinRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CHECKIN_DATE)?.let { checkinRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { (application as RaiseTheBarApp).startup() }
    }

    companion object {
        const val EXTRA_CHECKIN_DATE = "checkin_date"
    }
}
