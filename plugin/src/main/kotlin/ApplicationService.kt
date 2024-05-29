@file:Suppress("INLINE_FROM_HIGHER_PLATFORM")

package edu.illinois.cs.cs125.intellijlogger

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.time.Instant
import java.util.UUID

data class ProjectConfiguration(
    val destination: String,
    val name: String,
    val emailLocation: String?,
    var email: String?,
    val networkAddress: String?,
    val buttonAction: String?,
    val trustSelfSignedCertificates: Boolean,
    val uploadOnClose: Boolean,
)

@Suppress("unused")
@State(name = "Component", storages = [(Storage("edu.illinois.cs.cs125.intellijlogger.2024.5.0.223.xml"))])
class ApplicationService : PersistentStateComponent<ApplicationService.State>, Disposable {
    data class State(
        var activeCounters: MutableList<Counter> = mutableListOf(),
        var savedCounters: MutableList<Counter> = mutableListOf(),
        var counterIndex: Long = 0L,
        @Suppress("ConstructorParameterNaming", "PropertyName")
        var UUID: String = "",
        var lastSave: Long = -1,
        val pluginVersion: String = version,
    )

    var projectCounters = mutableMapOf<Project, Counter>()
    val projectConfigurations = mutableMapOf<Project, ProjectConfiguration>()

    override fun initializeComponent() {
        super.initializeComponent()

        if (actualState.UUID == "") {
            actualState.UUID = UUID.randomUUID().toString()
            if (actualState.savedCounters.size != 0) {
                log.warn("Must be updating plugin since saved counters exist before UUID is set")
            }
        }
        for (counter in actualState.savedCounters) {
            if (counter.UUID != actualState.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${actualState.UUID}")
                counter.UUID = actualState.UUID
            }
        }
        for (counter in actualState.activeCounters) {
            if (counter.UUID != actualState.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${actualState.UUID}")
                counter.UUID = actualState.UUID
            }
            counter.end = actualState.lastSave
            synchronized(actualState.savedCounters) {
                actualState.savedCounters.add(counter)
            }
        }
        actualState.activeCounters.clear()
    }

    var actualState = State()

    override fun getState(): State {
        log.trace("Saving state")
        actualState.lastSave = Instant.now().toEpochMilli()
        return actualState
    }

    override fun loadState(state: State) {
        actualState = state
    }

    override fun dispose() {
        return
    }
}

fun getCounters(project: Project?) = project?.let {
    service<ApplicationService>().projectCounters[project]
}.also { counter ->
    if (project != null && counter == null) {
        log.warn("can't get counters for project")
    }
}
fun getConfiguration(project: Project?) = project?.let {
    service<ApplicationService>().projectConfigurations[project]
}.also { configuration ->
    if (project != null && configuration == null) {
        log.warn("can't get configuration for project")
    }
}
