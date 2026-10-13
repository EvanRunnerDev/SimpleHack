# Implementation Plan

## Group 1 — Repository Scaffold

- [x] 1.1 Copy Meteor Client Java source tree into workspace
  - Source: `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master\src\main\java\`
  - Destination: `src\main\java\`
  - Copy the entire `meteordevelopment\meteorclient\` package tree verbatim
  - Do not modify any files in this step — copy only
  - _Requirements: 1.2.1_

- [x] 1.2 Copy Meteor Client resources into workspace
  - Source: `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master\src\main\resources\`
  - Destination: `src\main\resources\`
  - Files to copy:
    - `fabric.mod.json`
    - `meteor-client.mixins.json`
    - `meteor-client-baritone.mixins.json`
    - `meteor-client-indigo.mixins.json`
    - `meteor-client-sodium.mixins.json`
    - `meteor-client-lithium.mixins.json`
    - `meteor-client-viafabricplus.mixins.json`
    - `meteor-client.classtweaker`
    - `assets\meteor-client\` (entire directory tree)
  - _Requirements: 1.2.1, 3.2.2_

- [x] 1.3 Copy the launcher source set
  - Source: `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master\src\launcher\`
  - Destination: `src\launcher\`
  - Copy verbatim — contains `Main.java` used for the JAR manifest entry point
  - _Requirements: 1.2.1_

- [x] 1.4 Copy the Gradle wrapper and version catalog
  - Source files to copy from `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master\`:
    - `gradle\wrapper\gradle-wrapper.jar`
    - `gradle\wrapper\gradle-wrapper.properties`
    - `gradlew`
    - `gradlew.bat`
  - Copy `gradle\libs.versions.toml` to `gradle\libs.versions.toml`
  - _Requirements: 1.2.1_

- [x] 1.5 Remove Baritone entries from `gradle/libs.versions.toml`
  - File: `gradle\libs.versions.toml`
  - In `[versions]` section: remove the line `baritone = "26.1-SNAPSHOT"`
  - In `[libraries]` section: remove the line `baritone = { module = "meteordevelopment:baritone", version.ref = "baritone" }`
  - All other entries remain unchanged
  - _Requirements: 3.1.3, 3.2.5_

- [x] 1.6 Copy the Meteor Client LICENSE file
  - Source: `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master\LICENSE`
  - Destination: `LICENSE`
  - This is the GPL-3.0 license file
  - _Requirements: 5.1.1_

---

## Group 2 — Gradle Build Files

- [x] 2.1 Create `settings.gradle.kts`
  - Create file at workspace root: `settings.gradle.kts`
  - Content:
    ```kotlin
    pluginManagement {
        repositories {
            maven {
                name = "Fabric"
                url = uri("https://maven.fabricmc.net/")
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }

    rootProject.name = "simplehack"
    ```
  - _Requirements: 1.1.2_

- [x] 2.2 Create `gradle.properties`
  - Create file at workspace root: `gradle.properties`
  - Content:
    ```properties
    org.gradle.jvmargs=-Xmx2G
    org.gradle.parallel=true
    org.gradle.caching=true
    org.gradle.configuration-cache=false

    # Mod Properties
    maven_group=meteordevelopment
    archives_base_name=simplehack

    # Absolute path to the local Baritone checkout.
    # Override on CI or different machines via -PbaritoneDir=…
    baritoneDir=C:/Users/Jacob/Documents/baritone-26.2/baritone-26.2
    ```
  - _Requirements: 3.1.1, 4.1.1_

- [x] 2.3 Create `build.gradle.kts` — plugin block and base config
  - Create file at workspace root: `build.gradle.kts`
  - Add plugin declarations:
    ```kotlin
    import net.ltgt.gradle.errorprone.errorprone

    plugins {
        alias(libs.plugins.fabric.loom)
        id("maven-publish")
        alias(libs.plugins.errorprone)
    }
    ```
  - Add `base { archivesName = ...; group = ...; version = ... }` block (same logic as upstream)
  - _Requirements: 1.2.1_

- [x] 2.4 Add `AddonConfig` data class and `addonRegistry` map to `build.gradle.kts`
  - In `build.gradle.kts`, after the base block, add:
    ```kotlin
    data class AddonConfig(
        val sourcePath: String,
        val resourcePath: String,
        val mixinConfigs: List<String>
    )

    val baritoneRoot = providers.gradleProperty("baritoneDir").get()

    val addonRegistry: Map<String, AddonConfig> = mapOf(
        "baritone" to AddonConfig(
            sourcePath   = "$baritoneRoot/fabric/src/main/java",
            resourcePath = "$baritoneRoot/fabric/src/main/resources",
            mixinConfigs = listOf("meteor-client-baritone.mixins.json")
        )
    )
    ```
  - _Requirements: 3.3.1, 3.3.2_

- [x] 2.5 Add `addons` property parsing logic to `build.gradle.kts`
  - After the `addonRegistry` definition, add:
    ```kotlin
    val requestedAddons: Set<String> = run {
        val raw = providers.gradleProperty("addons").orNull
        when {
            raw == null || raw == "all" -> addonRegistry.keys
            raw == "none"              -> emptySet()
            else                       -> raw.split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                            .toSet()
        }
    }

    val activeAddons: Map<String, AddonConfig> =
        addonRegistry.filterKeys { it in requestedAddons }

    val baritoneActive = "baritone" in activeAddons
    ```
  - _Requirements: 3.1.1, 3.1.2, 3.1.3, 3.1.4, 3.1.5_

- [x] 2.6 Add repositories block to `build.gradle.kts`
  - Reproduce the full `repositories { ... }` block from Meteor's original `build.gradle.kts`
  - Include: `meteor-maven`, `meteor-maven-snapshots`, `ViaVersion`, `mavenCentral()`, and the `modrinth` exclusive content block
  - _Requirements: 1.2.1_

- [x] 2.7 Add `modInclude`, `jij`, and `launcher` configurations/source sets to `build.gradle.kts`
  - Reproduce Meteor's existing `modInclude`, `jij` configuration declarations and `configurations { }` block verbatim
  - Add the `launcher` source set:
    ```kotlin
    val launcher = sourceSets.create("launcher") {
        java.srcDir("src/launcher/java")
    }
    ```
  - _Requirements: 1.2.1_

- [x] 2.8 Add conditional Baritone source set to `build.gradle.kts`
  - After the `launcher` source set, add:
    ```kotlin
    val baritoneSourceSet = if (baritoneActive) {
        val cfg = activeAddons["baritone"]!!
        sourceSets.create("baritone") {
            java.srcDir(cfg.sourcePath)
            resources.srcDir(cfg.resourcePath)
        }
    } else null

    if (baritoneSourceSet != null) {
        sourceSets.named("main") {
            compileClasspath += baritoneSourceSet.output
            runtimeClasspath += baritoneSourceSet.output
        }
    }
    ```
  - _Requirements: 3.2.1_

- [x] 2.9 Add `dependencies` block to `build.gradle.kts`
  - Reproduce all existing Meteor `dependencies { }` entries (fabric, fabric-api modules, compileOnly compat mods, jij libraries, errorprone)
  - Replace `compileOnly(libs.baritone)` with a `compileOnly(files(...))` reference to the local Baritone API JAR:
    ```kotlin
    // Always on compileOnly so BaritonePathManager/Settings/Utils compile cleanly.
    // The JAR is only embedded when baritoneActive == true.
    val baritoneVersion = "26.2"
    compileOnly(files("$baritoneRoot/dist/baritone-api-fabric-${baritoneVersion}.jar"))
    ```
  - Add conditional jar-in-jar embed after the compileOnly line:
    ```kotlin
    if (baritoneActive) {
        val baritoneJar = fileTree("$baritoneRoot/fabric/build/libs") {
            include("*-fabric-*.jar")
            exclude("*-dev*.jar", "*-sources*.jar")
        }
        modInclude(files(baritoneJar))
    }
    ```
  - _Requirements: 3.2.1, 3.2.3, 3.2.4, 3.2.5, 4.1.1, 4.2.2_

- [x] 2.10 Add `java` toolchain block and jij transitive-dependency handling to `build.gradle.kts`
  - Reproduce the `java { toolchain { ... } }` block from Meteor verbatim
  - Reproduce the `jijExcluded` set and the `listOf("api", "implementation", "include").forEach { ... }` transitive-jij loop verbatim
  - _Requirements: 1.2.1_

- [x] 2.11 Add `loom` configuration block to `build.gradle.kts`
  - Add:
    ```kotlin
    loom {
        accessWidenerPath = file("src/main/resources/meteor-client.classtweaker")
    }
    ```
  - _Requirements: 1.2.1_

- [x] 2.12 Add `toMinecraftCompat` helper function to `build.gradle.kts`
  - Reproduce the `fun toMinecraftCompat(version: String): String { ... }` function from Meteor verbatim
  - _Requirements: 1.2.1_

- [x] 2.13 Add `tasks` block to `build.gradle.kts`
  - Add `processResources` task with dynamic mixin array injection:
    ```kotlin
    processResources {
        val alwaysMixins = listOf(
            "meteor-client.mixins.json",
            "meteor-client-indigo.mixins.json",
            "meteor-client-sodium.mixins.json",
            "meteor-client-lithium.mixins.json",
            "meteor-client-viafabricplus.mixins.json"
        )
        val addonMixins = activeAddons.values.flatMap { it.mixinConfigs }
        val allMixins = (alwaysMixins + addonMixins)
            .joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

        val propertyMap = mapOf(
            "version"           to project.version,
            "build_number"      to providers.gradleProperty("build_number").getOrElse(""),
            "commit"            to providers.gradleProperty("commit").getOrElse(""),
            "jdk_version"       to libs.versions.jdk.get(),
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "loader_version"    to libs.versions.fabric.loader.get(),
            "mixins_array"      to allMixins
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") { expand(propertyMap) }
    }
    ```
  - Add `compileLauncherJava` task with Java 8 source/target compatibility (same as Meteor)
  - Add `jar` task:
    ```kotlin
    jar {
        inputs.property("archivesName", archivesBaseName)

        from("LICENSE") {
            rename { "${it}_simplehack" }
        }

        // Baritone license bundled when Baritone is active (see task 5.1)
        if (baritoneActive) {
            from("src/main/resources/baritone-LICENSE") {
                rename { "LICENSE_baritone" }
            }
        }

        from(launcher.output)

        manifest {
            attributes["Main-Class"] = "meteordevelopment.meteorclient.Main"
        }
    }
    ```
  - Add `withType<JavaCompile>` error-prone block (same as Meteor)
  - Add `javadoc` options block (same as Meteor)
  - _Requirements: 3.2.2, 3.2.3, 5.1.1, 5.1.2_

- [x] 2.14 Add `publishing` block to `build.gradle.kts`
  - Reproduce Meteor's `publishing { publications { ... } repositories { ... } }` block
  - Change `artifactId = "meteor-client"` to `artifactId = "simplehack"`
  - _Requirements: 1.1.2_

---

## Group 3 — Mod Identity Updates

- [x] 3.1 Update `fabric.mod.json` — id, name, and color
  - File: `src\main\resources\fabric.mod.json`
  - Change `"id": "meteor-client"` to `"id": "simplehack"`
  - Change `"name": "Meteor Client"` to `"name": "SimpleHack"`
  - Change `"custom"."meteor-client:color"` value from `"145,61,226"` to `"255,255,255"`
  - _Requirements: 1.1.1, 1.1.2, 1.1.3_

- [x] 3.2 Update `fabric.mod.json` — replace `mixins` array with template token
  - File: `src\main\resources\fabric.mod.json`
  - Replace the hard-coded `"mixins": [ ... ]` array with the single-token form:
    ```json
    "mixins": ${mixins_array},
    ```
  - This token is expanded at build time by the `processResources` task added in task 2.13
  - _Requirements: 3.2.2, 3.2.3_

- [x] 3.3 Update `fabric.mod.json` — contact URLs
  - File: `src\main\resources\fabric.mod.json`
  - Update the `"contact"` object:
    ```json
    "contact": {
      "homepage": "https://github.com/Jacob-Hegy/SimpleHack",
      "issues": "https://github.com/Jacob-Hegy/SimpleHack/issues",
      "sources": "https://github.com/Jacob-Hegy/SimpleHack"
    }
    ```
  - Remove the `"discord"` key (or update to a SimpleHack Discord URL if one exists)
  - _Requirements: 1.1.2_

- [x] 3.4 Update `MeteorClient.java` — change `MOD_ID` constant to `"simplehack"`
  - File: `src\main\java\meteordevelopment\meteorclient\MeteorClient.java`
  - Change:
    ```java
    public static final String MOD_ID = "meteor-client";
    ```
    to:
    ```java
    public static final String MOD_ID = "simplehack";
    ```
  - This single change propagates automatically to:
    - `FOLDER` (game data directory: `.minecraft/simplehack/`)
    - `BUILD_NUMBER` lookup via `MOD_ID + ":build_number"` custom metadata key
    - `identifier()` helper method (resource namespace)
    - `FabricLoader.getInstance().getModContainer(MOD_ID)` call
  - Verify no other `"meteor-client"` string literals remain in `MeteorClient.java` after this change
  - _Requirements: 1.1.1_

---

## Group 4 — Theme Overhaul

- [x] 4.1 Apply all 23 color changes to `MeteorGuiTheme.java`
  - File: `src\main\java\meteordevelopment\meteorclient\gui\themes\meteor\MeteorGuiTheme.java`
  - Apply every change from the design doc §3.1 table. Changes listed below by field:

  **Accent / checkbox (req 2.1.1, 2.1.2)**
  - `accentColor`: `(145, 61, 226)` → `(255, 255, 255)`
  - `checkboxColor`: `(145, 61, 226)` → `(255, 255, 255)`

  **Slider colors (req 2.2.1, 2.2.2)**
  - `sliderHandle` idle: `(130, 0, 255)` → `(220, 220, 220)`
  - `sliderHandle` hover: `(140, 30, 255)` → `(240, 240, 240)`
  - `sliderHandle` pressed: `(150, 60, 255)` → `(255, 255, 255)`
  - `sliderLeft`: `(100, 35, 170)` → `(60, 60, 60)`

  **Background colors (req 2.3.1, 2.3.2)**
  - `backgroundColor` idle: `(20, 20, 20, 200)` → `(255, 255, 255, 230)`
  - `backgroundColor` hover: `(30, 30, 30, 200)` → `(245, 245, 245, 230)`
  - `backgroundColor` pressed/focused: `(40, 40, 40, 200)` → `(235, 235, 235, 230)`
  - `moduleBackground`: `(50, 50, 50)` → `(230, 230, 230, 255)`

  **Text colors (req 2.4.1–2.4.5)**
  - `textColor`: `(255, 255, 255)` → `(0, 0, 0, 255)`
  - `titleTextColor`: `(255, 255, 255)` → `(0, 0, 0, 255)`
  - `textSecondaryColor`: `(150, 150, 150)` → `(60, 60, 60, 255)`
  - `separatorText`: `(255, 255, 255)` → `(0, 0, 0, 255)`
  - `separatorCenter`: `(255, 255, 255)` → `(0, 0, 0, 255)`
  - `separatorEdges`: `(225, 225, 225, 150)` → `(0, 0, 0, 150)`

  **Outline colors (req 2.5.1)**
  - `outlineColor` idle: `(0, 0, 0)` → `(180, 180, 180)`
  - `outlineColor` hover: `(10, 10, 10)` → `(140, 140, 140)`
  - `outlineColor` pressed/focused: `(20, 20, 20)` → `(100, 100, 100)`

  **Scrollbar colors (req 2.6.1)**
  - `scrollbarColor` idle: `(30, 30, 30, 200)` → `(180, 180, 180, 200)`
  - `scrollbarColor` hover: `(40, 40, 40, 200)` → `(150, 150, 150, 200)`
  - `scrollbarColor` active/pressed: `(50, 50, 50, 200)` → `(120, 120, 120, 200)`

  - Do NOT change `plusColor`, `minusColor`, `favoriteColor`, `sliderRight`,
    `textHighlightColor`, `loggedInColor`, `placeholderColor`, or `starscript*` fields
  - _Requirements: 2.1.1, 2.1.2, 2.2.1, 2.2.2, 2.3.1, 2.3.2, 2.4.1, 2.4.2, 2.4.3, 2.4.4, 2.4.5, 2.5.1, 2.6.1, 2.8.1_

---

## Group 5 — Licensing

- [x] 5.1 Copy Baritone LICENSE to `src/main/resources/baritone-LICENSE`
  - Source: `C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2\LICENSE`
  - Destination: `src\main\resources\baritone-LICENSE`
  - This is the LGPL-3.0 license file
  - The `build.gradle.kts` `jar` task added in task 2.13 already conditionally includes this file as `LICENSE_baritone` when `baritoneActive == true`
  - _Requirements: 5.1.2_

---

## Group 6 — Verification

- [x] 6.1 Pre-build Baritone (one-time step)
  - Open a terminal and run the following commands from the Baritone directory:
    ```
    cd C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2
    gradlew :fabric:remapJar
    ```
  - This produces the Baritone Fabric JAR at `fabric\build\libs\` and the API JAR at `dist\`
  - Only needs to be re-run when the Baritone source changes
  - _Requirements: 4.1.1_

- [x] 6.2 Build SimpleHack with `-Paddons=all` (default)
  - From the workspace root (`c:\Users\Jacob\Documents\GitHub\SimpleHack`), run:
    ```
    gradlew build
    ```
    (equivalent to `gradlew build -Paddons=all`)
  - Verify: `build\libs\simplehack-26.2-local.jar` is produced
  - Verify: the JAR contains `baritone\` classes (open the JAR and check for nested Baritone JAR)
  - Verify: `meteor-client-baritone.mixins.json` appears in the `fabric.mod.json` inside the JAR
  - _Requirements: 3.1.1, 3.1.2, 4.1.1_

- [~] 6.3 Build SimpleHack with `-Paddons=none`
  - From the workspace root, run:
    ```
    gradlew build -Paddons=none
    ```
  - Verify: `build\libs\simplehack-26.2-local.jar` is produced
  - Verify: the JAR does NOT contain a nested Baritone JAR
  - Verify: `fabric.mod.json` inside the JAR does NOT list `meteor-client-baritone.mixins.json`
  - Verify: no `baritone.api.*` classes appear in the output JAR
  - _Requirements: 3.1.3, 3.2.3, 3.2.5_

- [~] 6.4 Build SimpleHack with `-Paddons=baritone`
  - From the workspace root, run:
    ```
    gradlew build -Paddons=baritone
    ```
  - Verify: the JAR is produced and contains the Baritone nested JAR
  - Verify: `meteor-client-baritone.mixins.json` appears in `fabric.mod.json` inside the JAR
  - Verify: behavior is identical to the `all` build (since Baritone is the only registered addon)
  - _Requirements: 3.1.4_
