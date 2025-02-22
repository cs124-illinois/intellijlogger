import java.util.Properties
import java.io.StringWriter
import java.io.File

version = "2025.2.0"

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
dependencies {
    val ktorVersion = "3.1.0"

    implementation(project(":plugin"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.12.14")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}
application {
    mainClass.set("edu.illinois.cs.cs125.intellijlogger.server.MainKt")
}
val dockerName = "cs124/intellijlogger"
tasks.register<Copy>("dockerCopyJar") {
    from(tasks["shadowJar"].outputs)
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Copy>("dockerCopyDockerfile") {
    from("${projectDir}/Dockerfile")
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("/usr/local/bin/docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("/usr/local/bin/docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
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
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
