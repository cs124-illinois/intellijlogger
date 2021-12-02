import java.util.Properties
import java.io.StringWriter
import java.io.File

val majorIntelliJVersion = "203"
group = "edu.illinois.cs.cs125"
version = "2021.12.0.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
    id("org.jetbrains.intellij") version "1.3.0"
    id("org.jmailen.kotlinter")
}
intellij {
    pluginName.set("CS 124 IntelliJ Activity Logger")
    version.set("2020.3")
    // sandboxDirectory.set(File(projectDir, "sandbox").absolutePath)
    plugins.set(listOf("java"))
}
tasks.patchPluginXml {
    sinceBuild.set(majorIntelliJVersion)
    untilBuild.set("213.*")
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.12.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("com.squareup.moshi:moshi:1.12.0")
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
kotlin {
    kotlinDaemonJvmArgs = listOf("-Dfile.encoding=UTF-8", "--illegal-access=permit")
}
kapt {
    javacOptions {
        option("--illegal-access", "permit")
    }
}
