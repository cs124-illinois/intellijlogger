package edu.illinois.cs.cs125.intellijlogger

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class TestStatusHandler : TestStatusListener() {
    override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?) { }
    private fun countTests(abstractTestProxy: AbstractTestProxy, projectCounter: Counter) {
        if (!abstractTestProxy.isLeaf) {
            for (child in abstractTestProxy.children) {
                countTests(child, projectCounter)
            }
            return
        }
        val name = abstractTestProxy.name
            .replace("\\.test$".toRegex(), "")
            .replace(".", "_")
        if (!(projectCounter.testCounts.containsKey(name))) {
            projectCounter.testCounts[name] = TestCounter()
        }
        val testCounter = projectCounter.testCounts[name] ?: return
        when {
            abstractTestProxy.isPassed -> testCounter.passed++
            abstractTestProxy.isDefect -> testCounter.failed++
            abstractTestProxy.isIgnored -> testCounter.ignored++
            abstractTestProxy.isInterrupted -> testCounter.interrupted++
        }
        projectCounter.totalTestCount++
    }

    override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?, project: Project) {
        if (abstractTestProxy == null) {
            return
        }
        ApplicationManager
            .getApplication()
            .getComponent(Component::class.java).currentProjectCounters[project]?.let { counters ->
            log.trace("testSuiteFinished")
            countTests(abstractTestProxy, counters)
        } ?: run {
            log.warn("can't get counters for project")
        }
    }
}
