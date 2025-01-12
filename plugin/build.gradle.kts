import java.util.Properties
import java.io.StringWriter
import java.io.File

val majorIntelliJVersion = "231"
group = "edu.illinois.cs.cs125"
version = "2025.1.0.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    idea
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
intellij {
    version = "2023.1"
    pluginName = "CS 124 IntelliJ Activity Logger"
    plugins = listOf("java")
    // sandboxDirectory.set(File(projectDir, "sandbox").absolutePath)
}
tasks.patchPluginXml {
    sinceBuild = majorIntelliJVersion
    untilBuild = provider { null }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
task("createProperties") {
    doLast {
        val properties = Properties().also { it["version"] = project.version.toString() }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.intellijlogger.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
