plugins {
    id("fabric-loom") version "1.6.12"
}

base {
    archivesName.set("blockblend")
}

group = "com.example.blockblend"
version = "0.3.1"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.test { useJUnitPlatform() }

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    jar {
        from("LICENSE")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.named<org.gradle.api.tasks.bundling.Jar>("sourcesJar") {
    from("LICENSE")
}

val smoke by sourceSets.creating
smoke.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
smoke.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
val showcase by sourceSets.creating
showcase.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
showcase.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath

val smokeGraphics = providers.gradleProperty("smokeGraphics").orElse("fabulous").get()
require(smokeGraphics in listOf("fancy", "fabulous")) { "smokeGraphics must be fancy or fabulous" }
val smokeRunDir = file(providers.gradleProperty("smokeRunDir").orElse("build/runtime/smoke/$smokeGraphics/client").get())
val smokeEvidenceDir = file(providers.gradleProperty("smokeEvidenceDir").orElse("build/runtime/smoke/$smokeGraphics/evidence").get())
val showcaseRunDir = file(providers.gradleProperty("showcaseRunDir").orElse("build/showcase/client").get())
val showcaseOutputDir = file(providers.gradleProperty("showcaseOutputDir").orElse("build/showcase/evidence").get())

loom {
    mixin { defaultRefmapName.set("blockblend.refmap.json") }
    mods {
        create("blockblend") { sourceSet(sourceSets.main.get()) }
        create("blockblend_smoke") { sourceSet(smoke) }
        create("blockblend_showcase") { sourceSet(showcase) }
    }
    runs {
        named("client") {
            runDir("build/runtime/client")
        }
        named("server") {
            runDir("build/runtime/server")
        }
        create("smokeClient") {
            client()
            source(smoke)
            runDir(project.relativePath(smokeRunDir))
            vmArg("-Dblockblend.smoke=true")
            vmArg("-Dblockblend.smoke.runDir=${smokeRunDir.absolutePath}")
            vmArg("-Dblockblend.smoke.evidence=${smokeEvidenceDir.absolutePath}")
            vmArg("-Dblockblend.smoke.graphics=$smokeGraphics")
        }
        create("showcaseClient") {
            client()
            source(showcase)
            runDir(project.relativePath(showcaseRunDir))
            vmArg("-Dblockblend.showcase.runDir=${showcaseRunDir.absolutePath}")
            vmArg("-Dblockblend.showcase=true")
            vmArg("-Dblockblend.showcase.output=${showcaseOutputDir.absolutePath}")
            programArg("--width")
            programArg(providers.gradleProperty("showcaseWidth").orElse("1600").get())
            programArg("--height")
            programArg(providers.gradleProperty("showcaseHeight").orElse("900").get())
        }
    }
}

// Loom does not create custom working directories before launching Java.
val clientRunDirectories = mapOf(
    "runClient" to file("build/runtime/client"),
    "runServer" to file("build/runtime/server"),
    "runSmokeClient" to smokeRunDir,
    "runShowcaseClient" to showcaseRunDir
)
tasks.configureEach {
    clientRunDirectories[name]?.let { directory ->
        doFirst {
            check(directory.isDirectory || directory.mkdirs()) {
                "Could not create isolated Minecraft run directory: $directory"
            }
        }
    }
}

var smokeStartedAt = 0L
tasks.matching { it.name == "runSmokeClient" }.configureEach {
    doFirst {
        check(smokeEvidenceDir.isDirectory || smokeEvidenceDir.mkdirs()) {
            "Could not create smoke evidence directory: $smokeEvidenceDir"
        }
        smokeStartedAt = System.currentTimeMillis()
    }
    doLast {
        val receipts = smokeEvidenceDir.listFiles().orEmpty().filter {
            it.name.startsWith("blockblend-opacity-") && it.extension == "jsonl"
                && it.lastModified() >= smokeStartedAt
        }
        check(receipts.size == 1) { "Expected one new smoke receipt in $smokeEvidenceDir" }
        // Screenshot IO callbacks may append after the terminal test event.
        val terminal = receipts.single().useLines { lines ->
            lines.lastOrNull { it.contains("\"kind\":\"PASS\"") || it.contains("\"kind\":\"FAIL\"") }
        }
        check(terminal?.contains("\"kind\":\"PASS\"") == true) {
            "Minecraft smoke did not finish with PASS; inspect ${receipts.single()}"
        }
    }
}
