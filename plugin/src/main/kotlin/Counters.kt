package edu.illinois.cs.cs125.intellijlogger

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import edu.illinois.cs.cs125.intellijlogger.moshi.Adapters
import java.time.Instant

@Suppress("unused", "MemberVisibilityCanBePrivate")
@JsonClass(generateAdapter = true)
data class Counter(
    var destination: String = "",
    var trustSelfSignedCertificates: Boolean = false,
    @Suppress("ConstructorParameterNaming") var UUID: String = "",
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
    var testCounts: MutableList<TestCounter> = mutableListOf(),
    var totalRunCount: Int = 0,
    var runCounts: MutableList<RunCounter> = mutableListOf(),
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
            totalRunCount +
            fileOpenedCount +
            fileClosedCount +
            fileSelectionChangedCount
    }

    fun isEmpty(): Boolean {
        return totalCount() == 0
    }

    companion object {
        val adapter: JsonAdapter<Counter> = Moshi.Builder().also { builder ->
            Adapters.forEach { builder.add(it) }
        }.build().adapter(Counter::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class TestCounter(
    val name: String,
    var passed: Int = 0,
    var failed: Int = 0,
    var ignored: Int = 0,
    var interrupted: Int = 0
)

@JsonClass(generateAdapter = true)
data class FileInfo(var path: String = "", var lineCount: Int = 0)

@JsonClass(generateAdapter = true)
data class RunCounter(
    val name: String,
    var started: Int = 0
)
