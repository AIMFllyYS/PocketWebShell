package com.webshell.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.ui.AppSplash
import com.webshell.app.ui.AppThemeViewModel
import com.webshell.app.ui.MainScaffold
import com.webshell.core.designsystem.theme.WebShellTheme
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.WebViewPool
import com.webshell.feature.viewer.IncomingIntentParser
import com.webshell.feature.viewer.IncomingOpenCandidate
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()
    private var incoming by mutableStateOf<IncomingOpenCandidate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logAppLaunch()
        incoming = IncomingIntentParser.from(intent)
        val launchUrl = intent?.getStringExtra(EXTRA_URL)
        val skipSplash = launchUrl != null ||
            incoming != null ||
            WebViewPool.liveSessions().isNotEmpty() ||
            KeepAliveRegistry.entries.isNotEmpty()
        setContent {
            val theme by themeViewModel.theme.collectAsStateWithLifecycle()
            val incomingFile = incoming
            WebShellTheme(
                themeMode = theme.mode,
                photoWallpaperPath = theme.wallpaperPath,
                transitionStyle = theme.transitionStyle,
                appFontFamily = theme.appFontFamily,
                appFontScalePercent = theme.appFontScalePercent,
            ) {
                Box(Modifier.fillMaxSize()) {
                    var splashVisible by remember { mutableStateOf(!skipSplash) }
                    var mountShell by remember { mutableStateOf(skipSplash) }
                    LaunchedEffect(incomingFile) {
                        if (incomingFile != null) {
                            mountShell = true
                            splashVisible = false
                        }
                    }
                    if (mountShell) {
                        MainScaffold(
                            launchUrl = launchUrl,
                            incoming = incomingFile,
                            onIncomingLeave = {
                                IncomingIntentParser.markConsumed(intent)
                                incoming = null
                            },
                            suppressLiveGlass = splashVisible,
                        )
                    }
                    if (splashVisible) {
                        AppSplash(
                            onReadyForShell = { mountShell = true },
                            onFinished = { splashVisible = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = IncomingIntentParser.from(intent)
    }

    companion object {
        const val EXTRA_URL = "url"
    }

    private fun logAppLaunch() {
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppLog.log("app", "应用启动 v${info.versionName}($code)")
        }
    }
}
