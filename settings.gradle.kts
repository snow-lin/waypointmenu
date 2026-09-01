pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // 26.x is unobfuscated -> the non-remapping Loom plugin (no mappings needed).
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter {
    create(rootProject) {
        versions("26.1", "26.2")
        vcsVersion = "26.2"
    }
}

rootProject.name = "waypointmenu"
