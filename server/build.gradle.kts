import java.util.Properties
import java.io.StringWriter
import java.io.File

group = "edu.illinois.cs.cs125"
version = "2021.2.1"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.palantir.docker") version "0.26.0"
    id("org.jmailen.kotlinter")
}
dependencies {
    implementation(project(":plugin"))
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:1.5.3")
    implementation("org.mongodb:mongodb-driver:3.12.8")
    implementation("io.ktor:ktor-gson:1.5.3")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:2.0.6")

    testImplementation("io.kotest:kotest-runner-junit5:4.4.3")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
    testImplementation("io.ktor:ktor-server-test-host:1.5.3")
}
application {
    @Suppress("DEPRECATION")
    mainClassName = "edu.illinois.cs.cs125.intellijlogger.server.MainKt"
}
docker {
    name = "cs125/intellijlogger"
    tag("latest", "cs125/intellijlogger:latest")
    tag(version.toString(), "cs125/intellijlogger:$version")
    files(tasks["shadowJar"].outputs)
}
tasks.test {
    useJUnitPlatform()
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback.xml").absolutePath
    environment("MONGODB", "mongodb://localhost:27017/testing")
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(
            projectDir,
            "src/main/resources/edu.illinois.cs.cs125.intellijlogger.server.version"
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
