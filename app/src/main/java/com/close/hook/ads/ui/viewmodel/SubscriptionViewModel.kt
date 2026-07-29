package com.close.hook.ads.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.close.hook.ads.data.model.SubscriptionSource
import com.close.hook.ads.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SubscriptionRepository.get(application)

    val sources: StateFlow<List<SubscriptionSource>> = repo.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 8)

    fun addSource(url: String, name: String, intervalMinutes: Long) =
        viewModelScope.launch {
            runCatching { repo.addSource(url, name, intervalMinutes) }
                .onSuccess { id -> doRefresh(id) }
                .onFailure { events.emit(Event.Error(it.message ?: "Failed to add subscription")) }
        }

    fun updateSource(source: SubscriptionSource) =
        viewModelScope.launch {
            runCatching { repo.updateSource(source) }
                .onFailure { events.emit(Event.Error(it.message ?: "Failed to update subscription")) }
        }

    fun deleteSource(source: SubscriptionSource) =
        viewModelScope.launch { repo.deleteSource(source) }

    fun refresh(sourceId: Long) = viewModelScope.launch {
        events.emit(Event.RefreshStarted)
        doRefresh(sourceId)
    }

    fun refreshDue() = viewModelScope.launch { repo.refreshDueSources() }

    fun toggleEnabled(source: SubscriptionSource) = updateSource(source.copy(enabled = !source.enabled))

    private suspend fun doRefresh(sourceId: Long) {
        runCatching { repo.runRefresh(sourceId) }
            .onFailure { events.emit(Event.Error(it.message ?: "Refresh failed")) }
    }

    sealed interface Event {
        data object RefreshStarted : Event
        data class Error(val message: String) : Event
    }
}
