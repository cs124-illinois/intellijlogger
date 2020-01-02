import java.util.*

val majorIntelliJVersion = "191"
group = "edu.illinois.cs.cs125"
version = "2019.12.1.$majorIntelliJVersion"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
    id("org.jetbrains.intellij") version "0.4.15"
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
    implementation("org.apache.httpcomponents:httpclient:4.5.10")
    implementation("com.squareup.moshi:moshi:1.9.2")
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/intellijplugin_version.properties").printWriter().use {
            properties.store(it, null)
        }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
