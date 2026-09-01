plugins {
    id("net.fabricmc.fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

// Minecraft 26.x runs on Java 25.
val requiredJava = JavaVersion.VERSION_25

// Resolved from stonecutter.properties.toml (version-scoped for the active version).
val mcCompat = property("mod.mc_compat") as String
val mcRange = property("mod.mc_range") as String
val modVersion = property("mod.version") as String
val javaCompat = ">=25"
val javaLevel = "JAVA_25"

// 26.2 builds the highlight pipeline from the public BindGroupLayouts constants
// plus RenderType#create; 26.1 still needs the private GLOBALS_SNIPPET /
// MATRICES_PROJECTION_SNIPPET via RenderPipelinesAccessor.
val mixinClient = if (sc.current.parsed >= "26.2")
    "[\"RenderTypeInvoker\"]"
else
    "[\"RenderTypeInvoker\", \"RenderPipelinesAccessor\"]"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://libraries.minecraft.net/") { name = "Mojang" }
    maven("https://maven.terraformersmc.com/") { name = "Terraformers" }
}

dependencies {
    // 26.x is unobfuscated: no `mappings`, no remapping. Plain `implementation`.
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    implementation("com.terraformersmc:modmenu:${property("deps.modmenu")}")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
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
        options.release.set(25)
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
        // Name the distributable jar after the Minecraft version it targets
        // (e.g. waypointmenu-1.0.0+26.1.jar). No remapJar on unobfuscated 26.x.
        archiveVersion.set("$modVersion+$mcRange")
    }
}
