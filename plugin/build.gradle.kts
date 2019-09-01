import java.util.*

plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
    id("org.jetbrains.intellij") version "0.4.10"
}

intellij {
    pluginName = "CS 125 Plugin"
    version = "2019.1"
    sandboxDirectory = File(projectDir, "sandbox").absolutePath
}
tasks.patchPluginXml {
    sinceBuild("191")
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")

    implementation(kotlin("stdlib"))
    implementation("org.yaml:snakeyaml:1.25")
    implementation("org.apache.httpcomponents:httpclient:4.5.9")
    implementation("com.squareup.moshi:moshi:1.8.0")
}
task("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/version.properties").printWriter().use { properties.store(it, null) }
    }
}
