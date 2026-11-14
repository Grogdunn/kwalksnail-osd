import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.0"
}

group = "dev.grogdunn.kwalksnailosd"
version = "1.0-SNAPSHOT"

val appPackageVersion = (findProperty("appVersion") as String?) ?: "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val bytedecoPlatform = bytedecoPlatform()

dependencies {
    testImplementation(kotlin("test"))
    implementation(compose.desktop.currentOs)
    implementation("org.bytedeco:javacv:1.5.14") {
        exclude(group = "org.bytedeco", module = "openblas")
        exclude(group = "org.bytedeco", module = "opencv")
        exclude(group = "org.bytedeco", module = "flycapture")
        exclude(group = "org.bytedeco", module = "libdc1394")
        exclude(group = "org.bytedeco", module = "libfreenect")
        exclude(group = "org.bytedeco", module = "libfreenect2")
        exclude(group = "org.bytedeco", module = "librealsense")
        exclude(group = "org.bytedeco", module = "librealsense2")
        exclude(group = "org.bytedeco", module = "videoinput")
        exclude(group = "org.bytedeco", module = "artoolkitplus")
        exclude(group = "org.bytedeco", module = "leptonica")
        exclude(group = "org.bytedeco", module = "tesseract")
    }
    implementation("org.bytedeco:ffmpeg:8.1.2-1.5.14:$bytedecoPlatform")
    implementation("org.bytedeco:javacpp:1.5.14:$bytedecoPlatform")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    implementation("ch.qos.logback:logback-classic:1.6.3")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

fun bytedecoPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm = arch == "aarch64" || arch == "arm64"
    return when {
        os.contains("win") -> "windows-x86_64"
        os.contains("mac") -> if (arm) "macosx-arm64" else "macosx-x86_64"
        else -> if (arm) "linux-arm64" else "linux-x86_64"
    }
}

val packagedJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.get()

compose.desktop {
    application {
        mainClass = "dev.grogdunn.kwalksnailosd.MainKt"
        javaHome = packagedJdk.metadata.installationPath.asFile.absolutePath
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            packageName = "Walksnail-osd"
            packageVersion = appPackageVersion
            description = "Walksnail OSD preview"
            vendor = "Grogdunn"
            // Temurin 25 has no jmods and cannot re-bundle jdk.jlink, so the runtime is an explicit module set.
            modules(
                "java.xml",
                "java.datatransfer",
                "java.prefs",
                "java.net.http",
                "jdk.unsupported",
                "jdk.localedata",
            )
            macOS {
                bundleID = "dev.grogdunn.kwalksnailosd"
            }
        }
    }
}
