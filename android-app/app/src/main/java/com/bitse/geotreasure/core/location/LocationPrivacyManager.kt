package com.bitse.geotreasure.core.location

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationPrivacyManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val acceptedState = MutableStateFlow(preferences.getBoolean(KEY_ACCEPTED, false))

    val isAccepted: StateFlow<Boolean> = acceptedState.asStateFlow()

    init {
        if (acceptedState.value) {
            applySdkConsent()
        }
    }

    fun accept() {
        applySdkConsent()
        preferences.edit { putBoolean(KEY_ACCEPTED, true) }
        acceptedState.value = true
    }

    private fun applySdkConsent() {
        MapsInitializer.updatePrivacyShow(appContext, true, true)
        MapsInitializer.updatePrivacyAgree(appContext, true)
        AMapLocationClient.updatePrivacyShow(appContext, true, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)
    }

    companion object {
        private const val PREFERENCES_NAME = "privacy_consent"
        private const val KEY_ACCEPTED = "amap_accepted"
    }
}
