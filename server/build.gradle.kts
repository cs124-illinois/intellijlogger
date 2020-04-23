import java.util.Properties
import java.io.StringWriter
import java.io.File

group = "edu.illinois.cs.cs125"
version = "2020.4.0"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.palantir.docker") version "0.25.0"
    id("org.jmailen.kotlinter")
}
dependencies {
    val ktorVersion = "1.3.2"

    implementation(project(":plugin"))
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.12.3")
    implementation("io.ktor:ktor-gson:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.uchuhimo:konf-core:0.22.1")
    implementation("com.uchuhimo:konf-yaml:0.22.1")
    implementation("io.github.microutils:kotlin-logging:1.7.9")

    val kotlintestVersion = "3.4.2"
    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlintestVersion")
    testImplementation("io.kotlintest:kotlintest-assertions-ktor:$kotlintestVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
val mainClass = "edu.illinois.cs.cs125.intellijlogger.server.MainKt"
application {
    mainClassName = mainClass
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
tasks.jar {
    manifest {
        attributes["Main-Class"] = mainClass
    }
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
