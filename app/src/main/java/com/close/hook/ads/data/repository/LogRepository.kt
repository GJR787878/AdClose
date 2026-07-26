package com.close.hook.ads.data.repository

import com.close.hook.ads.data.model.LogEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
object LogRepository {

    private val _logFlow = MutableSharedFlow<List<LogEntry>>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logFlow = _logFlow.asSharedFlow()

    private val logCache = ArrayDeque<LogEntry>()
    const val MAX_CACHE_SIZE = 1000

    suspend fun addLogs(logEntries: List<LogEntry>) {
        if (logEntries.isEmpty()) return
        val newestFirst = logEntries.reversed()
        synchronized(logCache) {
            logCache.addAll(0, newestFirst)
            val excess = logCache.size - MAX_CACHE_SIZE
            repeat(excess.coerceAtLeast(0)) { logCache.removeLast() }
        }
        _logFlow.emit(newestFirst)
    }

    fun getLogsForPackage(packageName: String?): List<LogEntry> {
        synchronized(logCache) {
            return logCache.filter { it.matchesHookScope(packageName) }
        }
    }

    fun belongsToHookScope(entry: LogEntry, packageName: String?): Boolean =
        entry.matchesHookScope(packageName)

    private fun LogEntry.matchesHookScope(packageName: String?): Boolean =
        if (packageName == null) {
            hookScope == LogEntry.GLOBAL_HOOK_SCOPE
        } else {
            this.packageName == packageName && hookScope == packageName
        }

    fun clearLogs() {
        synchronized(logCache) { logCache.clear() }
        _logFlow.tryEmit(emptyList())
    }

    fun clearLogsForPackage(packageName: String?) {
        synchronized(logCache) {
            logCache.removeAll { it.matchesHookScope(packageName) }
        }
    }
}
