package com.webshell.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.webshell.app.ui.MainScaffold
import com.webshell.core.designsystem.theme.WebShellTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchUrl = intent?.getStringExtra(EXTRA_URL)
        setContent {
            WebShellTheme {
                MainScaffold(launchUrl = launchUrl)
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
