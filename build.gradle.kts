import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.4.32"
    kotlin("jvm") version kotlinVersion apply false
    kotlin("kapt") version kotlinVersion apply false
    id("org.jmailen.kotlinter") version "3.4.0" apply false
    id("com.github.ben-manes.versions") version "0.38.0"
    id("io.gitlab.arturbosch.detekt") version "1.16.0"
}
allprojects {
    repositories {
        jcenter()
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        val javaVersion = JavaVersion.VERSION_1_8.toString()
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        kotlinOptions {
            jvmTarget = javaVersion
        }
    }
}
subprojects {
    tasks.withType<Test> {
        enableAssertions = true
    }
}
tasks.dependencyUpdates {
    resolutionStrategy {
        componentSelection {
            all {
                if (listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "eap", "release").any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
                }) {
                    reject("Release candidate")
                }
            }
        }
    }
    gradleReleaseChannel = "current"
}
detekt {
    input = files("plugin/src/main/kotlin", "server/src/main/kotlin")
    config = files("config/detekt/detekt.yml")
}
tasks.register("check") {
    dependsOn("detekt")
}
