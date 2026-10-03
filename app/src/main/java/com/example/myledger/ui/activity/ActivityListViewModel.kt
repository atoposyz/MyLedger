package com.example.myledger.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.repository.LedgerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActivityListViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val _state = MutableStateFlow(ActivityListUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    init { reload() }

    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                repository.observeActivities().collect { activities ->
                    _state.value = ActivityListUiState(activities, isLoading = false)
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }
}
