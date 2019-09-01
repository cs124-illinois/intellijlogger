package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.intellijplugin.server.DEFAULT_HTTP
import edu.illinois.cs.cs125.intellijplugin.server.TopLevel
import edu.illinois.cs.cs125.intellijplugin.server.configuration
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class TestConfig : StringSpec({
    "should load defaults correctly" {
        configuration[TopLevel.http] shouldBe DEFAULT_HTTP
    }
})
