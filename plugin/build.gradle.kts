import java.util.Properties

plugins {
  kotlin("jvm")
  idea
  id("org.jetbrains.intellij") version "0.4.10"
}

group = "edu.illinois.cs.cs125"
version = "2019.9"

val intellijVersion = "2019.1"
intellij {
    pluginName = "CS 125 Plugin"
    version = intellijVersion
    sandboxDirectory = File(projectDir, "sandbox").absolutePath
}
tasks.patchPluginXml {
    sinceBuild(intellijVersion)
    untilBuild("$intellijVersion.*")
}
dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.yaml:snakeyaml:1.25")
    implementation("org.apache.httpcomponents:httpclient:4.5.9")
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
