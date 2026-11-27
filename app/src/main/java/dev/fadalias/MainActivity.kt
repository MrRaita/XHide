package dev.fadalias

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.content.Context
import dev.fadalias.app.Services
import dev.fadalias.ui.FadaliasApp
import dev.fadalias.ui.theme.FadaliasTheme
import dev.fadalias.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: FadaliasViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Services.get(newBase).prefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = resolveDarkTheme(vm.themeMode)
            val startLanguage = remember { vm.language }
            LaunchedEffect(vm.language) { if (vm.language != startLanguage) recreate() }
            LaunchedEffect(Unit) { vm.requestSync() }
            DisposableEffect(dark) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            FadaliasTheme(
                themeMode = vm.themeMode,
                dynamicColor = vm.dynamicColor,
                amoled = vm.amoled,
                uiScale = vm.uiScale,
            ) {
                FadaliasApp(vm)
            }
        }
    }
}
