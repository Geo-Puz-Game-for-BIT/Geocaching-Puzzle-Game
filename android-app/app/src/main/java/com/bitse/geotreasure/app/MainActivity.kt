package com.bitse.geotreasure.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.bitse.geotreasure.core.ui.theme.GeoTreasureTheme
import com.bitse.geotreasure.navigation.GeoTreasureNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GeoTreasureTheme {
                GeoTreasureNavHost()
            }
        }
    }
}
