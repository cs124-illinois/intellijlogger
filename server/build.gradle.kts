import java.util.Properties
import java.io.StringWriter
import java.io.File

group = "edu.illinois.cs.cs125"
version = "2022.12.0"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
dependencies {
    implementation(project(":plugin"))
    implementation("io.ktor:ktor-server-netty:2.2.1")
    implementation("io.ktor:ktor-server-forwarded-header:2.2.1")
    implementation("io.ktor:ktor-server-cors:2.2.1")
    implementation("io.ktor:ktor-server-content-negotiation:2.2.1")
    implementation("org.mongodb:mongodb-driver:3.12.11")
    implementation("io.ktor:ktor-serialization-gson:2.2.1")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.4")

    testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
    testImplementation("io.ktor:ktor-server-test-host:2.2.1")
}
application {
    @Suppress("DEPRECATION")
    mainClassName = "edu.illinois.cs.cs125.intellijlogger.server.MainKt"
}
val dockerName = "cs125/intellijlogger"
tasks.register<Copy>("dockerCopyJar") {
    from(tasks["shadowJar"].outputs)
    into("${buildDir}/docker")
}
tasks.register<Copy>("dockerCopyDockerfile") {
    from("${projectDir}/Dockerfile")
    into("${buildDir}/docker")
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir("${buildDir}/docker")
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir("${buildDir}/docker")
    commandLine(
        ("docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
            "--builder multiplatform " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
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
