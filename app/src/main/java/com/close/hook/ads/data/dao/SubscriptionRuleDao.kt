package com.close.hook.ads.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.close.hook.ads.data.model.SubscriptionRule

@Dao
interface SubscriptionRuleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<SubscriptionRule>)

    @Query("DELETE FROM subscription_rule WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)

    @Query(
        "SELECT subscription_rule.* FROM subscription_rule " +
            "INNER JOIN subscription_source ON subscription_source.id = subscription_rule.sourceId " +
            "WHERE subscription_source.enabled = 1"
    )
    fun findEnabledRules(): List<SubscriptionRule>
}
