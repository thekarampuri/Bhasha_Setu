package com.example.voicetranslate.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.voicetranslate.util.Constants

/**
 * Repository for managing app preferences
 */
class PreferencesRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.Prefs.NAME,
        Context.MODE_PRIVATE
    )
    
    /**
     * Get saved backend URL
     */
    fun getBackendUrl(): String {
        return prefs.getString(
            Constants.Prefs.KEY_BACKEND_URL,
            Constants.Network.DEFAULT_BACKEND_URL
        ) ?: Constants.Network.DEFAULT_BACKEND_URL
    }
    
    /**
     * Save backend URL
     */
    fun saveBackendUrl(url: String) {
        prefs.edit()
            .putString(Constants.Prefs.KEY_BACKEND_URL, url)
            .apply()
    }
}
