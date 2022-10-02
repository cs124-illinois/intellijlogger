import java.util.Properties
import java.io.StringWriter
import java.io.File

group = "edu.illinois.cs.cs125"
version = "2022.9.0"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.palantir.docker") version "0.34.0"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
dependencies {
    implementation(project(":plugin"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("io.ktor:ktor-server-netty:2.1.1")
    implementation("io.ktor:ktor-server-forwarded-header:2.1.1")
    implementation("io.ktor:ktor-server-cors:2.1.1")
    implementation("io.ktor:ktor-server-content-negotiation:2.1.1")
    implementation("org.mongodb:mongodb-driver:3.12.11")
    implementation("io.ktor:ktor-serialization-gson:2.1.1")
    implementation("ch.qos.logback:logback-classic:1.4.1")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.4.2")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
    testImplementation("io.ktor:ktor-server-test-host:2.1.1")
}
application {
    @Suppress("DEPRECATION")
    mainClassName = "edu.illinois.cs.cs125.intellijlogger.server.MainKt"
}
docker {
    name = "cs125/intellijlogger"
    @Suppress("DEPRECATION")
    tags("latest")
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
