package edu.illinois.cs.cs125.intellijlogger

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import com.intellij.openapi.project.Project

class TestStatusHandler : TestStatusListener() {
    @Suppress("EmptyFunctionBlock")
    override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?) {
    }

    private fun countTests(abstractTestProxy: AbstractTestProxy, projectCounter: Counter) {
        if (!abstractTestProxy.isLeaf) {
            for (child in abstractTestProxy.children) {
                countTests(child, projectCounter)
            }
            return
        }
        val testCounter = projectCounter.testCounts.find { it.name === abstractTestProxy.name } ?: TestCounter(
            abstractTestProxy.name
        ).also {
            projectCounter.testCounts.add(it)
        }
        when {
            abstractTestProxy.isPassed -> testCounter.passed++
            abstractTestProxy.isDefect -> testCounter.failed++
            abstractTestProxy.isIgnored -> testCounter.ignored++
            abstractTestProxy.isInterrupted -> testCounter.interrupted++
        }
        projectCounter.totalTestCount++
    }

    override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?, project: Project?) {
        if (abstractTestProxy == null) {
            return
        }
        project?.counters()?.let { counters ->
            log.trace("testSuiteFinished")
            countTests(abstractTestProxy, counters)
        } ?: run {
            log.warn("can't get counters for project")
        }
    }
}
