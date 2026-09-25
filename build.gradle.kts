plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.0"
}

group = "dev.grogdunn.kwalksnailosd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(compose.desktop.currentOs)
    implementation("org.bytedeco:javacv-platform:1.5.14")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    implementation("ch.qos.logback:logback-classic:1.6.3")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            packageName = "Walksnail-osd"
            packageVersion = version.toString()
        }
    }
}
