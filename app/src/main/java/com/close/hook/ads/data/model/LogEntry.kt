package com.close.hook.ads.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val tag: String,
    val message: String,
    val packageName: String,
    val stackTrace: String?,
    val hookScope: String? = null
) : Parcelable {
    companion object {
        const val GLOBAL_HOOK_SCOPE = "global"
    }
}
