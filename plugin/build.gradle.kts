@file:Suppress("SpellCheckingInspection")

import java.util.Properties
import java.io.StringWriter
import java.io.File

val majorIntelliJVersion = "231"
group = "edu.illinois.cs.cs125"
version = "2025.10.2.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    idea
    id("org.jetbrains.intellij.platform") version "2.10.1"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
repositories {
    intellijPlatform {
        defaultRepositories()
    }
}
intellijPlatform {
    pluginConfiguration {
        name = "CS 124 Activity Logger"
        ideaVersion {
            sinceBuild = majorIntelliJVersion
            untilBuild = provider { null }
        }
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
dependencies {
    intellijPlatform {
        @Suppress("DEPRECATION")
        intellijIdeaCommunity("2023.1")
        bundledPlugin("com.intellij.java")
    }
    implementation("org.yaml:snakeyaml:2.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.4.0.202509020913-r") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}
tasks.register("createProperties") {
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
