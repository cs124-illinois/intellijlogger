import java.util.*

val majorIntelliJVersion = "191"
group = "edu.illinois.cs.cs125"
version = "2019.9.2.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
    id("org.jetbrains.intellij") version "0.4.10"
}
intellij {
    pluginName = "CS 125 IntelliJ Activity Logger"
    version = "2019.2"
    sandboxDirectory = File(projectDir, "sandbox").absolutePath
    setPlugins("java")
}
tasks.patchPluginXml {
    sinceBuild(majorIntelliJVersion)
    untilBuild("192.*")
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")

    implementation(kotlin("stdlib"))
    implementation("org.yaml:snakeyaml:1.25")
    implementation("org.apache.httpcomponents:httpclient:4.5.10")
    implementation("com.squareup.moshi:moshi:1.8.0")
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/version.properties").printWriter().use { properties.store(it, null) }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
