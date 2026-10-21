package io.github.ernisn.applingo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.ui.MainScreen
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.theme.ApplingoTheme
import io.github.ernisn.applingo.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(settings)
            // Fully transparent system bars whose icons follow the app's theme (which may differ from the
            // system's). Explicit light/dark styles also stop the system from adding a contrast scrim behind
            // three-button navigation; content keeps clear of the bars itself (see MainScreen).
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ApplingoTheme(settings) {
                MainScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }
}
