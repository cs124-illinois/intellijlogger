@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.intellijlogger.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant

@Suppress("unused")
@JvmField
val Adapters = setOf(InstantAdapter())

@Suppress("unused")

class InstantAdapter {
    @FromJson
    fun instantFromJson(timestamp: String): Instant {
        return Instant.parse(timestamp)
    }
    @ToJson
    fun instantToJson(instant: Instant): String {
        return instant.toString()
    }
}
