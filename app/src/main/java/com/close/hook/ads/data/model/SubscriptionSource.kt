package com.close.hook.ads.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscription_source",
    indices = [Index(value = ["url"], unique = true)]
)
data class SubscriptionSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val name: String,
    val enabled: Boolean = true,
    val refreshIntervalMinutes: Long = DEFAULT_REFRESH_INTERVAL_MINUTES,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastAttemptAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val nextRefreshAt: Long = 0L,
    val refreshLeaseUntil: Long = 0L,
    val ruleCount: Int = 0,
    val skippedCount: Int = 0,
    val lastError: String? = null
) {
    companion object {
        const val MIN_REFRESH_INTERVAL_MINUTES = 15L
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 24L * 60L
    }
}
