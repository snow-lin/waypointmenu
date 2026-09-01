plugins {
    id("fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

// Minecraft 1.20.5+ runs on Java 21; 1.20.0-1.20.4 run on Java 17.
val requiredJava: JavaVersion = if (sc.current.parsed >= "1.20.5") JavaVersion.VERSION_21 else JavaVersion.VERSION_17

// Resolved from stonecutter.properties.toml (version-scoped for the active version).
val mcCompat = property("mod.mc_compat") as String
val mcRange = property("mod.mc_range") as String
val modVersion = property("mod.version") as String
val javaCompat = if (requiredJava >= JavaVersion.VERSION_21) ">=21" else ">=17"
val javaLevel = "JAVA_${requiredJava.majorVersion}"

// Each era exposes its own render-layer mixin:
//   >=1.21.11  RenderSetup exists -> RenderLayerInvoker + RenderPipelinesAccessor
//   >=1.21.5   RenderSetup absent, but POSITION_COLOR_SNIPPET exists ->
//              only RenderPipelinesAccessor (layer built via a same-package
//              helper reaching RenderLayer.MultiPhase)
//   <1.21.5    classic MultiPhase layer via same-package helper (no mixin)
val mixinClient = when {
    sc.current.parsed >= "1.21.11" -> "[\"RenderLayerInvoker\", \"RenderPipelinesAccessor\"]"
    sc.current.parsed >= "1.21.5" -> "[\"RenderPipelinesAccessor\"]"
    else -> "[]"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://libraries.minecraft.net/") { name = "Mojang" }
    maven("https://maven.terraformersmc.com/") { name = "Terraformers" }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings("net.fabricmc:yarn:${property("deps.yarn")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("com.terraformersmc:modmenu:${property("deps.modmenu")}")
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks {
    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft", mcCompat)
        inputs.property("java", javaCompat)
        inputs.property("java_level", javaLevel)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version, "minecraft" to mcCompat, "java" to javaCompat)
        }
        filesMatching("*.mixins.json") {
            expand("java_level" to javaLevel, "mixin_client" to mixinClient)
        }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(requiredJava.majorVersion.toInt())
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }

    // Name the distributable jar after the Minecraft versions it supports
    // (e.g. waypointmenu-1.0.0+1.20.2-1.20.4.jar). The mod's internal version
    // stays "1.0.0+<build target>" so Fabric Loader can still compare versions.
    remapJar {
        archiveVersion.set("$modVersion+$mcRange")
    }
}
