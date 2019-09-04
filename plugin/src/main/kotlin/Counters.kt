package edu.illinois.cs.cs125.intellijlogger

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import edu.illinois.cs.cs125.intellijlogger.moshi.Adapters
import java.time.Instant

@JsonClass(generateAdapter = true)
data class Counters(val counters: List<Counter>) {
    companion object {
        val adapter: JsonAdapter<Counters> = Moshi.Builder().also { builder ->
            Adapters.forEach { builder.add(it) }
        }.build().adapter(Counters::class.java)
    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
data class Counter(
        var UUID: String = "",
        var index: Long = 0,
        var previousIndex: Long = -1,
        var name: String? = null,
        var email: String? = null,
        var sentIPAddress: String? = null,
        var version: String = "",
        var intelliJVersion: String = "",
        var start: Long = Instant.now().toEpochMilli(),
        var end: Long = -1,
        var keystrokeCount: Int = 0,
        var caretAdded: Int = 0,
        var caretRemoved: Int = 0,
        var caretPositionChangedCount: Int = 0,
        var visibleAreaChangedCount: Int = 0,
        var mousePressedCount: Int = 0,
        var mouseActivityCount: Int = 0,
        var selectionChangedCount: Int = 0,
        var documentChangedCount: Int = 0,
        var compileCount: Int = 0,
        var successfulCompileCount: Int = 0,
        var failedCompileCount: Int = 0,
        var compilerErrorCount: Int = 0,
        var compilerWarningCount: Int = 0,
        var gradingCount: Int = 0,
        var totalTestCount: Int = 0,
        var testCounts: MutableMap<String, TestCounter> = mutableMapOf(),
        var fileOpenedCount: Int = 0,
        var fileClosedCount: Int = 0,
        var fileSelectionChangedCount: Int = 0,
        var openFiles: MutableList<FileInfo> = mutableListOf(),
        var openFileCount: Int = 0,
        var selectedFile: String = "",
        var opened: Boolean = false,
        var closed: Boolean = true
) {
    fun totalCount(): Int {
        return keystrokeCount +
                caretAdded +
                caretRemoved +
                caretPositionChangedCount +
                visibleAreaChangedCount +
                mousePressedCount +
                mouseActivityCount +
                visibleAreaChangedCount +
                documentChangedCount +
                successfulCompileCount +
                failedCompileCount +
                compilerErrorCount +
                compilerWarningCount +
                gradingCount +
                totalTestCount +
                fileOpenedCount +
                fileClosedCount +
                fileSelectionChangedCount
    }
    fun isEmpty(): Boolean {
        return totalCount() == 0
    }
}
data class TestCounter(var passed: Int = 0, var failed: Int = 0, var ignored: Int = 0, var interrupted: Int = 0)
data class FileInfo(var path: String = "", var lineCount: Int = 0)
