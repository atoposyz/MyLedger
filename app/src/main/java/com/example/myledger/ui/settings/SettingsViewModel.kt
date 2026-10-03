package com.example.myledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.settings.AppSettings
import com.example.myledger.data.settings.SettingsRepository
import com.example.myledger.data.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: AppSettings = AppSettings(), val isLoading: Boolean = true,
    val isSaving: Boolean = false, val readFailed: Boolean = false, val saveFailed: Boolean = false)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private var readJob: Job? = null
    init { reload() }
    fun reload() {
        readJob?.cancel(); _state.update { it.copy(isLoading = true, readFailed = false) }
        readJob = viewModelScope.launch {
            try { repository.settings.collect { settings -> _state.update { it.copy(settings = settings, isLoading = false, readFailed = false) } }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isLoading = false, readFailed = true) } }
        }
    }
    fun setTheme(theme: ThemeMode) {
        if (_state.value.isSaving || _state.value.isLoading || _state.value.readFailed) return
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try { repository.setTheme(theme)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(saveFailed = true) }
            } finally { _state.update { it.copy(isSaving = false) } }
        }
    }
}
