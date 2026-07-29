package com.close.hook.ads.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.close.hook.ads.data.database.UrlDatabase
import com.close.hook.ads.data.model.SubscriptionRule
import com.close.hook.ads.data.model.SubscriptionSource
import com.close.hook.ads.provider.UrlContentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionRepository private constructor(private val context: Context) {

    private val db = UrlDatabase.getDatabase(context)
    private val sourceDao = db.subscriptionSourceDao
    private val ruleDao = db.subscriptionRuleDao

    fun observeSources(): Flow<List<SubscriptionSource>> = sourceDao.observeAll()

    suspend fun addSource(url: String, name: String, intervalMinutes: Long): Long {
        val now = System.currentTimeMillis()
        val resolvedName = name.trim().ifBlank { defaultName() }
        return sourceDao.insert(
            SubscriptionSource(
                url = url.trim(),
                name = resolvedName,
                enabled = true,
                refreshIntervalMinutes = intervalMinutes.coerceAtLeast(SubscriptionSource.MIN_REFRESH_INTERVAL_MINUTES),
                nextRefreshAt = now
            )
        )
    }

    private fun defaultName() = "Sub ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}"

    suspend fun updateSource(source: SubscriptionSource) {
        sourceDao.update(source)
    }

    suspend fun deleteSource(source: SubscriptionSource) {
        sourceDao.delete(source)
        notifyChange()
    }

    suspend fun refreshDueSources() {
        val now = System.currentTimeMillis()
        sourceDao.findDueIds(now).forEach { runRefresh(it) }
    }

    internal suspend fun runRefresh(sourceId: Long) {
        val now = System.currentTimeMillis()
        val leaseDurationMs = 5 * 60 * 1000L
        val claimed = sourceDao.claimRefresh(sourceId, now, leaseUntil = now + leaseDurationMs)
        if (claimed == 0) return

        val source = sourceDao.findById(sourceId) ?: return
        try {
            val result = withContext(Dispatchers.IO) { SubscriptionFetcher.fetch(source) }
            when (result) {
                is SubscriptionFetcher.Result.NotModified -> {
                    val next = now + source.refreshIntervalMinutes * 60_000L
                    sourceDao.markNotModified(sourceId, now, next)
                }
                is SubscriptionFetcher.Result.Content -> {
                    val parsed = SubscriptionRuleParser.parse(result.input)
                    val rules = parsed.rules.map { SubscriptionRule(sourceId = sourceId, type = it.type, value = it.value) }
                    val next = now + source.refreshIntervalMinutes * 60_000L
                    db.withTransaction {
                        ruleDao.deleteBySourceId(sourceId)
                        ruleDao.insertAll(rules)
                        sourceDao.markRefreshSuccess(
                            id = sourceId,
                            etag = result.etag,
                            lastModified = result.lastModified,
                            now = now,
                            nextRefreshAt = next,
                            ruleCount = rules.size,
                            skippedCount = parsed.skippedCount
                        )
                    }
                    notifyChange()
                }
            }
        } catch (e: Exception) {
            val retryIn = 60 * 60_000L
            sourceDao.markRefreshFailure(sourceId, e.message ?: "Unknown error", now + retryIn)
        }
    }

    private fun notifyChange() {
        runCatching {
            context.contentResolver.notifyChange(
                Uri.parse("content://${UrlContentProvider.AUTHORITY}/${UrlContentProvider.URL_TABLE_NAME}"),
                null
            )
        }
    }

    companion object {
        @Volatile private var instance: SubscriptionRepository? = null

        fun get(context: Context): SubscriptionRepository =
            instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(context.applicationContext).also { instance = it }
            }
    }
}
