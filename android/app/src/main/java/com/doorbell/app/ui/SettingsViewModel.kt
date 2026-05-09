package com.doorbell.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doorbell.app.data.ConfigRepository
import com.doorbell.app.model.DoorbellConfig
import com.doorbell.app.service.DoorbellService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepository = ConfigRepository(application)

    private val _config = MutableStateFlow(DoorbellConfig())
    val config: StateFlow<DoorbellConfig> = _config

    init {
        viewModelScope.launch {
            configRepository.configFlow.collect {
                _config.value = it
            }
        }
    }

    fun updateConfig(newConfig: DoorbellConfig) {
        viewModelScope.launch {
            configRepository.updateConfig(newConfig)
            DoorbellService.currentInstance?.updateConfig(newConfig)
        }
    }
}
