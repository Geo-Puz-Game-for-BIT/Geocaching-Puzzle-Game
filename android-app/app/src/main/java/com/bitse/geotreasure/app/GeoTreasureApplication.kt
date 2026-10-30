package com.bitse.geotreasure.app

import android.app.Application

class GeoTreasureApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
