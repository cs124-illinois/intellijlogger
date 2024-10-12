package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.intellijlogger.server.intellijlogger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class TestHTTP :
    StringSpec({
        "should provide info in response to GET" {
            testApplication {
                application {
                    intellijlogger()
                }
                client.get("/") {
                    header("content-type", "application/json")
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    })
