package edu.illinois.cs.cs125.intellijlogger

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class ProjectService : Disposable {
    override fun dispose() {
        return
    }
}
