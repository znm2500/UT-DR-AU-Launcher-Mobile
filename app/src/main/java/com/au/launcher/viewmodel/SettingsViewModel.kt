package com.au.launcher.viewmodel

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.au.launcher.api.RetrofitClient
import com.au.launcher.utils.Constants
import com.au.launcher.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _bgImageUri = MutableStateFlow(prefs.getString("bg_image", null))
    val bgImageUri: StateFlow<String?> = _bgImageUri

    private val _language = MutableStateFlow(prefs.getString("language", "en") ?: "en")
    val language: StateFlow<String> = _language

    private val _isRegionDetected = MutableStateFlow(false)
    val isRegionDetected: StateFlow<Boolean> = _isRegionDetected

    private val _bgBlur = MutableStateFlow(prefs.getFloat("bg_blur", 0f))
    val bgBlur: StateFlow<Float> = _bgBlur

    private val _bgOpacity = MutableStateFlow(prefs.getFloat("bg_opacity", 0.5f))
    val bgOpacity: StateFlow<Float> = _bgOpacity

    private val _maskColor = MutableStateFlow(prefs.getInt("mask_color", 0xFF000000.toInt()))
    val maskColor: StateFlow<Int> = _maskColor

    init {
        // Apply saved language or detect
        viewModelScope.launch {
            val hasSavedLang = prefs.contains("language")
            
            // Detect region
            val isCN = NetworkUtils.isNetworkInChina()
            Constants.isChinaRegion = isCN
            RetrofitClient.refreshApi()
            
            if (!hasSavedLang) {
                val detectedLang = if (isCN) "zh" else "en"
                setLanguage(detectedLang, save = false)
            } else {
                applyLocale(_language.value)
            }
            _isRegionDetected.value = true
        }
    }

    fun setBgImage(uri: String?) {
        _bgImageUri.value = uri
        prefs.edit().putString("bg_image", uri).apply()
    }

    fun setBgBlur(value: Float) {
        _bgBlur.value = value
        prefs.edit().putFloat("bg_blur", value).apply()
    }

    fun setBgOpacity(value: Float) {
        _bgOpacity.value = value
        prefs.edit().putFloat("bg_opacity", value).apply()
    }

    fun setMaskColor(color: Int) {
        _maskColor.value = color
        prefs.edit().putInt("mask_color", color).apply()
    }

    fun setLanguage(lang: String, save: Boolean = true) {
        _language.value = lang
        if (save) {
            prefs.edit().putString("language", lang).apply()
        }
        applyLocale(lang)
    }

    private fun applyLocale(lang: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
