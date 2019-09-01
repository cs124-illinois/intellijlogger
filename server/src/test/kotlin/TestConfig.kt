package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.intellijlogger.server.DEFAULT_HTTP
import edu.illinois.cs.cs125.intellijlogger.server.TopLevel
import edu.illinois.cs.cs125.intellijlogger.server.configuration
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class TestConfig : StringSpec({
    "should load defaults correctly" {
        configuration[TopLevel.http] shouldBe DEFAULT_HTTP
    }
})
