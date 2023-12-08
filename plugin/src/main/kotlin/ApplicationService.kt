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
@State(name = "Component", storages = [(Storage("edu.illinois.cs.cs125.intellijlogger.2023.12.0.223.xml"))])
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

        if (_state.UUID == "") {
            _state.UUID = UUID.randomUUID().toString()
            if (_state.savedCounters.size != 0) {
                log.warn("Must be updating plugin since saved counters exist before UUID is set")
            }
        }
        for (counter in _state.savedCounters) {
            if (counter.UUID != _state.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${_state.UUID}")
                counter.UUID = _state.UUID
            }
        }
        for (counter in _state.activeCounters) {
            if (counter.UUID != _state.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${_state.UUID}")
                counter.UUID = _state.UUID
            }
            counter.end = _state.lastSave
            synchronized(_state.savedCounters) {
                _state.savedCounters.add(counter)
            }
        }
        _state.activeCounters.clear()
    }

    @Suppress("PropertyName", "VariableNaming")
    var _state = State()

    override fun getState(): State {
        log.trace("Saving state")
        _state.lastSave = Instant.now().toEpochMilli()
        return _state
    }

    override fun loadState(state: State) {
        _state = state
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
