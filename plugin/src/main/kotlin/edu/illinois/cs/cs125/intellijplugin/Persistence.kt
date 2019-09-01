package edu.illinois.cs.cs125.intellijplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.time.Instant

@State(name = "Component", storages = [(Storage(file = "CS125Component.xml"))])
class Persistence : PersistentStateComponent<Persistence.State> {
    class State {
        var activeCounters = mutableListOf<Counter>()
        var savedCounters = mutableListOf<Counter>()
        var counterIndex = 0L
        var UUID: String = ""
        var lastSave: Long = -1
    }
    var persistentState = State()
    override fun getState() : State {
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
