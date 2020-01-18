package edu.illinois.cs.cs125.intellijlogger

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import java.lang.Exception

@Suppress("EmptyFunctionBlock")
class ExternalSystemTaskNotificationHandler : ExternalSystemTaskNotificationListener {
    override fun onSuccess(externalSystemTaskId: ExternalSystemTaskId) {
        val project = externalSystemTaskId.findProject() ?: return
        getCounters(project)?.let { counters ->
            log.trace("onSuccess")
            counters.externalTaskCount++
        } ?: run {
            log.warn("can't get counters for project")
        }
    }

    override fun onFailure(externalSystemTaskId: ExternalSystemTaskId, e: Exception) {
        val project = externalSystemTaskId.findProject() ?: return
        getCounters(project)?.let { counters ->
            log.trace("onFailure")
            counters.externalTaskCount++
        } ?: run {
            log.warn("can't get counters for project")
        }
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {}
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {}
    override fun onCancel(id: ExternalSystemTaskId) {}
    override fun onEnd(id: ExternalSystemTaskId) {}
    override fun beforeCancel(id: ExternalSystemTaskId) {}
    @Suppress("UnstableApiUsage")
    override fun onStart(externalSystemTaskId: ExternalSystemTaskId) {}
}
