package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.intellijlogger.server.intellijlogger
import io.kotest.assertions.ktor.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication

class TestHTTP : StringSpec({
    "should provide info in response to GET" {
        withTestApplication(Application::intellijlogger) {
            handleRequest(HttpMethod.Get, "/") {
                addHeader("content-type", "application/json")
            }.apply {
                response.shouldHaveStatus(HttpStatusCode.OK.value)
            }
        }
    }
})
