package com.openjarvis.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openjarvis.R
import com.openjarvis.ui.settings.SettingsScreen
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.voice.VoiceManager
import com.openjarvis.ui.theme.OpenJarvisTheme

class SettingsActivity : ComponentActivity() {
    
    private lateinit var universalAdapter: UniversalAdapter
    private lateinit var voiceManager: VoiceManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        universalAdapter = UniversalAdapter(this)
        voiceManager = VoiceManager(this)
        
        setContent {
            OpenJarvisTheme {
                SettingsScreen(
                    onNavigateBack = { finish() },
                    initialProvider = universalAdapter.getProviderName(),
                    initialBaseUrl = universalAdapter.getSavedBaseUrl(),
                    initialApiKey = universalAdapter.getSavedApiKey(),
                    initialModel = universalAdapter.getSavedModel().ifBlank {
                        UniversalAdapter.getDefaultModel(universalAdapter.getProviderName())
                    },
                    initialVoiceEnabled = voiceManager.isVoiceEnabled(),
                    initialSpeakResults = voiceManager.isSpeakResultsEnabled(),
                    onVoiceEnabledChange = voiceManager::setVoiceEnabled,
                    onSpeakResultsChange = voiceManager::setSpeakResultsEnabled,
                    onSaveProvider = { name, baseUrl, apiKey, model ->
                        universalAdapter.saveSettings(name, baseUrl, apiKey, model)
                    },
                    onTestProvider = { name, baseUrl, apiKey, model ->
                        universalAdapter.testConnection(name, baseUrl, apiKey, model)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onDestroy() {
        if (::voiceManager.isInitialized) voiceManager.release()
        super.onDestroy()
    }
}
