import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.StringWriter
import java.io.File

val majorIntelliJVersion = "223"
group = "edu.illinois.cs.cs125"
version = "2024.5.0.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    idea
    id("org.jetbrains.intellij") version "1.17.3"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
intellij {
    pluginName.set("CS 124 IntelliJ Activity Logger")
    version.set("2022.3.3")
    // sandboxDirectory.set(File(projectDir, "sandbox").absolutePath)
    plugins.set(listOf("java"))
}
tasks.patchPluginXml {
    sinceBuild.set(majorIntelliJVersion)
    untilBuild.set("241.*")
}
java {
    targetCompatibility = JavaVersion.VERSION_11
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}
dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
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
