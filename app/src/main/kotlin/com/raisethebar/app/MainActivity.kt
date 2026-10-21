package com.raisethebar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raisethebar.app.ui.nav.RaiseTheBarRoot
import com.raisethebar.app.ui.theme.RtbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RtbTheme {
                RaiseTheBarRoot()
            }
        }
    }
}
