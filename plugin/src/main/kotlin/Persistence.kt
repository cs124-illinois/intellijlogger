package edu.illinois.cs.cs125.intellijlogger

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.time.Instant

@Suppress("unused")
@State(name = "Component", storages = [(Storage(file = "edu.illinois.cs.cs125.intellijlogger.xml"))])
class Persistence : PersistentStateComponent<Persistence.State> {
    class State {
        var activeCounters = mutableListOf<Counter>()
        var savedCounters = mutableListOf<Counter>()
        var counterIndex = 0L
        @Suppress("VariableNaming", "PropertyName")
        var UUID: String = ""
        var lastSave: Long = -1
        val pluginVersion: String = version
    }

    var persistentState = State()
    override fun getState(): State {
        persistentState.lastSave = Instant.now().toEpochMilli()
        return persistentState
    }

    override fun loadState(state: State) {
        persistentState = state
    }

    companion object {
        fun getInstance(): Persistence {
            return ServiceManager.getService(Persistence::class.java)
        }
    }
}
