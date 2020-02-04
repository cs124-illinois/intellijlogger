import java.util.Properties
import java.io.StringWriter
import java.io.File

val majorIntelliJVersion = "191"
group = "edu.illinois.cs.cs125"
version = "2020.2.1.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
    id("org.jetbrains.intellij") version "0.4.16"
    id("org.jmailen.kotlinter")
}
intellij {
    pluginName = "CS 125 IntelliJ Activity Logger"
    version = "2019.3"
    sandboxDirectory = File(projectDir, "sandbox").absolutePath
    setPlugins("java")
}
tasks.patchPluginXml {
    sinceBuild(majorIntelliJVersion)
    untilBuild("193.*")
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.9.2")

    implementation(kotlin("stdlib"))
    implementation("org.yaml:snakeyaml:1.25")
    implementation("org.apache.httpcomponents:httpclient:4.5.11")
    implementation("com.squareup.moshi:moshi:1.9.2")
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(
            projectDir,
            "src/main/resources/edu.illinois.cs.cs125.intellijlogger.version"
        )
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
