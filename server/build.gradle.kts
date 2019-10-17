import java.util.*

group = "edu.illinois.cs.cs125"
version = "2019.10.2"

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.palantir.docker") version "0.22.1"
}
dependencies {
    val ktorVersion = "1.2.5"

    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")

    implementation(project(":plugin"))
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.11.1")
    implementation("com.squareup.moshi:moshi:1.8.0")
    implementation("com.ryanharter.ktor:ktor-moshi:1.0.1")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.uchuhimo:konf-core:0.20.0")
    implementation("com.uchuhimo:konf-yaml:0.20.0")
    implementation("io.github.microutils:kotlin-logging:1.7.6")

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
    environment("MONGODB", "mongodb://localhost:27048/testing")
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
        File(projectDir, "src/main/resources/intellijlogger_version.properties").printWriter().use {
            properties.store(it, null)
        }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
