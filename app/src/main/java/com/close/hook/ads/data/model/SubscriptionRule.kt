package com.close.hook.ads.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscription_rule",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["sourceId", "type", "value"], unique = true)
    ]
)
data class SubscriptionRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sourceId: Long,
    val type: String,
    val value: String
)
