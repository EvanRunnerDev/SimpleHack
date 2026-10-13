# SimpleHack Combined Mod — Technical Design

## Overview

SimpleHack is a Minecraft Fabric hacked-client forked directly from the Meteor Client source tree,
with Baritone's Fabric module compiled into the same JAR. This document translates the five
requirement areas (mod identity, theme, build system, Baritone integration, licensing) into
concrete file-level changes, Gradle script structure, and integration architecture.

All paths below are relative to the workspace root
`c:\Users\Jacob\Documents\GitHub\SimpleHack` unless stated otherwise.

---

## Glossary

| Term | Definition |
|------|-----------|
| **Meteor source** | `C:\Users\Jacob\Documents\meteor-client-master\meteor-client-master` — the upstream fork base |
| **Baritone source** | `C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2` — local Baritone tree |
| **Fabric Loom `include`** | Gradle configuration that embeds a dependency as a nested JAR (jar-in-jar) |
| **`addons` property** | Gradle project property `-Paddons=…` controlling which optional components are bundled |
| **`addonRegistry`** | A `Map<String, AddonConfig>` defined in `build.gradle.kts` that lists every known addon |
| **`MixinPlugin`** | `meteordevelopment.meteorclient.MixinPlugin` — Meteor's `IMixinConfigPlugin` that gates mixin application at runtime based on which mods are loaded |
| **`NopPathManager`** | The no-op `IPathManager` implementation already present in Meteor; used when Baritone is absent |
| **classtweaker** | Fabric Loom's access-widener mechanism; Meteor uses `meteor-client.classtweaker` |

---

## 1. Repository Scaffold

### 1.1 Recommended directory layout

```
SimpleHack/                          ← workspace root
├── build.gradle.kts                 ← NEW — replaces Meteor's build script
├── settings.gradle.kts              ← NEW — sets rootProject.name = "simplehack"
├── gradle.properties                ← NEW — mod-specific properties
├── gradle/
│   └── libs.versions.toml           ← COPIED from Meteor; baritone ref updated (see §2)
├── src/
│   └── main/
│       ├── java/
│       │   └── meteordevelopment/meteorclient/   ← COPIED from Meteor source tree
│       │       ├── MeteorClient.java
│       │       ├── MixinPlugin.java
│       │       ├── gui/themes/meteor/MeteorGuiTheme.java   ← MODIFIED (§3)
│       │       ├── pathing/
│       │       │   ├── PathManagers.java          ← unchanged
│       │       │   ├── BaritonePathManager.java   ← unchanged
│       │       │   ├── BaritoneSettings.java      ← unchanged
│       │       │   └── BaritoneUtils.java         ← unchanged
│       │       └── mixin/baritone/ComeCommandMixin.java
│       └── resources/
│           ├── fabric.mod.json                   ← MODIFIED (§3, §5)
│           ├── meteor-client.mixins.json          ← unchanged
│           ├── meteor-client-baritone.mixins.json ← unchanged (conditionally included)
│           ├── meteor-client-indigo.mixins.json
│           ├── meteor-client-sodium.mixins.json
│           ├── meteor-client-lithium.mixins.json
│           ├── meteor-client-viafabricplus.mixins.json
│           ├── meteor-client.classtweaker
│           └── assets/
│               └── meteor-client/               ← keep asset folder name (see §5)
├── .kiro/
│   └── specs/simplehack-combined-mod/
│       ├── requirements.md
│       └── design.md
└── LICENSE                          ← GPL-3.0, copied from Meteor
```

> **Baritone source location**: Baritone is **not** copied into the workspace. The Gradle build
> references it via a local file path (`C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2`).
> This keeps the workspace clean and avoids duplicating ~50k lines of Baritone source. The path
> is declared as a Gradle property so it can be overridden per-machine.

---

## 2. Gradle Build Script Design

### 2.1 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false

maven_group=meteordevelopment
archives_base_name=simplehack

# Absolute path to the local Baritone checkout.
# Override on CI or different machines via -PbaritoneDir=…
baritoneDir=C:/Users/Jacob/Documents/baritone-26.2/baritone-26.2
```

### 2.2 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        maven { name = "Fabric"; url = uri("https://maven.fabricmc.net/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "simplehack"
```

### 2.3 `gradle/libs.versions.toml`

Copy verbatim from Meteor Client. The only required change is removing the remote Baritone
reference, because SimpleHack compiles Baritone from local source rather than pulling a
pre-built JAR from Maven:

```toml
# REMOVE this line:
# baritone = "26.1-SNAPSHOT"

# REMOVE this library entry:
# baritone = { module = "meteordevelopment:baritone", version.ref = "baritone" }
```

All other version catalog entries are unchanged.

### 2.4 `build.gradle.kts` — structure and key logic

#### 2.4.1 Addon registry data class and registry map

```kotlin
data class AddonConfig(
    val sourcePath: String,           // absolute path to the addon's fabric/src/main/java
    val resourcePath: String,         // absolute path to the addon's fabric/src/main/resources
    val mixinConfigs: List<String>    // mixin config filenames this addon contributes
)

val baritoneRoot = providers.gradleProperty("baritoneDir").get()

val addonRegistry: Map<String, AddonConfig> = mapOf(
    "baritone" to AddonConfig(
        sourcePath   = "$baritoneRoot/fabric/src/main/java",
        resourcePath = "$baritoneRoot/fabric/src/main/resources",
        mixinConfigs = listOf("meteor-client-baritone.mixins.json")
    )
    // Future addons: add a single entry here, nothing else changes.
)
```

#### 2.4.2 Parsing the `addons` property

```kotlin
// Resolve the active addon set from the -Paddons property.
// Absence of the property is equivalent to "all".
val requestedAddons: Set<String> = run {
    val raw = providers.gradleProperty("addons").orNull
    when {
        raw == null || raw == "all" -> addonRegistry.keys          // default: all
        raw == "none"               -> emptySet()
        else                        -> raw.split(",")
                                         .map { it.trim() }
                                         .filter { it.isNotEmpty() }
                                         .toSet()
    }
}

// Final active addons = intersection of requested names with the known registry.
// Unknown names are silently ignored (could be made a build error if preferred).
val activeAddons: Map<String, AddonConfig> =
    addonRegistry.filterKeys { it in requestedAddons }

val baritoneActive = "baritone" in activeAddons
```

#### 2.4.3 Conditionally adding the Baritone source set

```kotlin
val baritoneJava: SourceSet? = if (baritoneActive) {
    val cfg = activeAddons["baritone"]!!
    sourceSets.create("baritone") {
        java.srcDir(cfg.sourcePath)
        resources.srcDir(cfg.resourcePath)
    }
} else null

// Make the main source set depend on the Baritone source set at compile time.
if (baritoneJava != null) {
    sourceSets.named("main") {
        compileClasspath += baritoneJava.output
        runtimeClasspath += baritoneJava.output
    }
}
```

#### 2.4.4 Dependencies block (conditional Baritone jar-in-jar)

```kotlin
dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    // … all existing Meteor dependencies unchanged …

    if (baritoneActive) {
        // Embed the Baritone Fabric JAR built from local source.
        // The Baritone project must be built first; use its remapped output.
        val baritoneRoot = providers.gradleProperty("baritoneDir").get()
        val baritoneJar = fileTree("$baritoneRoot/fabric/build/libs") {
            include("*-fabric-*.jar")
            exclude("*-dev*.jar", "*-sources*.jar")
        }
        modInclude(files(baritoneJar))   // include = embed via Fabric Loom jar-in-jar
    }
}
```

> **Build-order note**: Because Baritone uses a Groovy DSL build (not Kotlin), it cannot be
> added as a Gradle subproject of SimpleHack without a settings-level composite build. The
> recommended workflow is: `cd baritone-26.2 && gradlew :fabric:remapJar` once, then build
> SimpleHack. A composite build (`includeBuild(baritoneRoot)`) can automate this if desired
> but is an optional enhancement.

#### 2.4.5 Dynamic `fabric.mod.json` `mixins` array via `processResources`

`fabric.mod.json` is stored as a **template** in `src/main/resources/`. The `mixins` array
uses a placeholder that Gradle expands at build time:

**`src/main/resources/fabric.mod.json`** (template form):
```json
{
  "mixins": ${mixins_array}
}
```

**`processResources` task** in `build.gradle.kts`:
```kotlin
tasks.processResources {
    // Build the mixins JSON array dynamically.
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

    val props = mapOf(
        "version"          to project.version,
        "build_number"     to providers.gradleProperty("build_number").getOrElse(""),
        "commit"           to providers.gradleProperty("commit").getOrElse(""),
        "jdk_version"      to libs.versions.jdk.get(),
        "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
        "loader_version"   to libs.versions.fabric.loader.get(),
        "mixins_array"     to allMixins    // ← injected array
    )

    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
```

When `addons=none`, `addonMixins` is empty, so `mixins_array` expands to the five core configs
only — `meteor-client-baritone.mixins.json` is simply absent. When `addons=baritone` or `all`,
it is appended automatically.

#### 2.4.6 Loom configuration

```kotlin
loom {
    // Access widener filename is unchanged.
    accessWidenerPath = file("src/main/resources/meteor-client.classtweaker")
}
```

---

## 3. Theme Changes

### 3.1 File: `src/main/java/meteordevelopment/meteorclient/gui/themes/meteor/MeteorGuiTheme.java`

All changes are to `new SettingColor(…)` default-value arguments in the field declarations.
No structural code changes are needed.

| Field | Old value | New value | Req |
|-------|-----------|-----------|-----|
| `accentColor` | `(145, 61, 226)` | `(255, 255, 255)` | 2.1.1 |
| `checkboxColor` | `(145, 61, 226)` | `(255, 255, 255)` | 2.1.2 |
| `textColor` | `(255, 255, 255)` | `(0, 0, 0, 255)` | 2.4.1 |
| `titleTextColor` | `(255, 255, 255)` | `(0, 0, 0, 255)` | 2.4.2 |
| `textSecondaryColor` | `(150, 150, 150)` | `(60, 60, 60, 255)` | 2.4.3 |
| `separatorText` | `(255, 255, 255)` | `(0, 0, 0, 255)` | 2.4.4 |
| `separatorCenter` | `(255, 255, 255)` | `(0, 0, 0, 255)` | 2.4.4 |
| `separatorEdges` | `(225, 225, 225, 150)` | `(0, 0, 0, 150)` | 2.4.5 |
| `backgroundColor` (idle) | `(20, 20, 20, 200)` | `(255, 255, 255, 230)` | 2.3.1 |
| `backgroundColor` (hover) | `(30, 30, 30, 200)` | `(245, 245, 245, 230)` | 2.3.1 |
| `backgroundColor` (pressed) | `(40, 40, 40, 200)` | `(235, 235, 235, 230)` | 2.3.1 |
| `moduleBackground` | `(50, 50, 50)` | `(230, 230, 230, 255)` | 2.3.2 |
| `outlineColor` (idle) | `(0, 0, 0)` | `(180, 180, 180)` | 2.5.1 |
| `outlineColor` (hover) | `(10, 10, 10)` | `(140, 140, 140)` | 2.5.1 |
| `outlineColor` (pressed) | `(20, 20, 20)` | `(100, 100, 100)` | 2.5.1 |
| `scrollbarColor` (idle) | `(30, 30, 30, 200)` | `(180, 180, 180, 200)` | 2.6.1 |
| `scrollbarColor` (hover) | `(40, 40, 40, 200)` | `(150, 150, 150, 200)` | 2.6.1 |
| `scrollbarColor` (pressed) | `(50, 50, 50, 200)` | `(120, 120, 120, 200)` | 2.6.1 |
| `sliderHandle` (idle) | `(130, 0, 255)` | `(220, 220, 220)` | 2.2.1 |
| `sliderHandle` (hover) | `(140, 30, 255)` | `(240, 240, 240)` | 2.2.1 |
| `sliderHandle` (pressed) | `(150, 60, 255)` | `(255, 255, 255)` | 2.2.1 |
| `sliderLeft` | `(100, 35, 170)` | `(60, 60, 60)` | 2.2.2 |

> `plusColor`, `minusColor`, `favoriteColor`, `sliderRight`, `textHighlightColor`,
> `loggedInColor`, `placeholderColor`, and all `starscript*` colors are **not changed** — they
> are either functional indicators (green/red/yellow) or code-editor syntax colors that are
> out of scope for the white theme.

### 3.2 File: `src/main/resources/fabric.mod.json`

```diff
-  "custom": {
-    "meteor-client:color": "145,61,226",
+  "custom": {
+    "meteor-client:color": "255,255,255",
```

> The `custom` key name `meteor-client:color` is intentionally preserved — changing it would
> break any downstream tooling that reads the Meteor metadata namespace.

### 3.3 HUD renderer colors

A search of the Meteor source tree shows that HUD module colors are driven through
`MeteorGuiTheme` settings (specifically `accentColor`, `textColor`, and `moduleBackground`)
rather than hard-coded constants in renderer classes. Because all three of those
`SettingColor` defaults are updated in §3.1, requirement 2.7 is satisfied without any
additional HUD file changes. No standalone purple color constants exist in the renderer layer.

---

## 4. Baritone Integration Architecture

### 4.1 Source compilation strategy

Baritone's Fabric module exposes its classes under the `baritone.launch` package
(`fabric/src/main/java/baritone/launch/`). The core Baritone API and implementation live in the
root project's `src/api` and `src/main` source sets. Compiling Baritone's Fabric module
standalone would require running its own Gradle build. The cleanest integration for SimpleHack
is therefore a **two-step approach**:

1. **Pre-build Baritone** once via its own Gradle wrapper:
   ```
   cd C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2
   gradlew :fabric:remapJar
   ```
   This produces a remapped, runnable JAR at
   `fabric/build/libs/baritone-<version>-fabric.jar`.

2. **Embed the JAR** into SimpleHack using Fabric Loom's `include` configuration (jar-in-jar),
   as shown in §2.4.4. Loom repackages it inside the SimpleHack output JAR so players load a
   single file.

This is the same pattern Meteor Client itself uses for its bundled libraries (`orbit`,
`starscript`, `discord-ipc`, etc.) via the `jij` configuration. The Baritone embed is added to
`modInclude` rather than `jij` because Baritone ships its own `fabric.mod.json` and must be
recognized as a Fabric mod by the loader, not just a plain library.

**Why not add Baritone as a Gradle subproject / composite build?**  
Baritone uses a Groovy DSL build with the `unimined` plugin and custom build logic
(`baritone.gradle.task.*`). Integrating it as a composite build is possible (`includeBuild`)
but introduces significant complexity. The pre-built JAR approach is lower-risk and still
fully satisfies requirements 4.1.1 and 3.2.1.

### 4.2 Mixin config conditional inclusion

As designed in §2.4.5, `processResources` injects the `mixins` array at build time. No runtime
mechanism is needed for build-time exclusion.

At **runtime**, `MixinPlugin.shouldApplyMixin` already guards the baritone mixin package:

```java
// MixinPlugin.java (existing, unchanged)
isBaritonePresent = FabricLoader.getInstance().isModLoaded("baritone")
                 || FabricLoader.getInstance().isModLoaded("baritone-meteor");
…
} else if (mixinClassName.startsWith(mixinPackage + ".baritone")) {
    return isBaritonePresent;
}
```

Because the Baritone JAR declares mod ID `baritone-meteor` in its `fabric.mod.json`,
`isBaritonePresent` will be `true` when the JAR is embedded and `false` when it is absent.
This provides a defense-in-depth layer on top of the build-time exclusion: even if a stale
or misconfigured `fabric.mod.json` template somehow included the baritone config, the mixin
plugin would safely skip the baritone mixins when the JAR is not present.

### 4.3 `PathManagers.java` guard analysis

The existing code in `PathManagers.java` uses runtime reflection to detect both
`meteordevelopment.voyager.PathManager` and `baritone.api.BaritoneAPI`:

```java
if (exists("baritone.api.BaritoneAPI")) {
    BaritoneUtils.IS_AVAILABLE = true;
    if (INSTANCE instanceof NopPathManager)
        INSTANCE = new BaritonePathManager();
}
```

**When Baritone is included**: `baritone.api.BaritoneAPI` is on the classpath →
`BaritonePathManager` is instantiated → requirement 4.2.1 satisfied.

**When Baritone is excluded**: `Class.forName("baritone.api.BaritoneAPI")` throws
`ClassNotFoundException` → caught → `exists()` returns `false` → `NopPathManager` remains →
requirement 4.2.3 satisfied.

The guard already fully satisfies requirements 3.2.4, 4.2.1, and 4.2.3 with no changes needed.

> **Note**: `BaritonePathManager.java`, `BaritoneSettings.java`, and `BaritoneUtils.java` import
> `baritone.api.*` directly (not via reflection). When `addons=none`, these three files must
> either be **excluded from compilation** or the baritone API jar must still be on
> `compileOnly`. The simplest approach is keeping `compileOnly(baritone-api)` from Maven even
> in the `none` build (it will not appear in the runtime JAR), so the source always compiles.
> Add this to `build.gradle.kts`:
>
> ```kotlin
> // Always needed for compileOnly so BaritonePathManager etc. compile cleanly.
> // The JAR is only embedded (modInclude) when baritoneActive == true.
> compileOnly(files("$baritoneRoot/dist/baritone-api-fabric-${baritoneVersion}.jar"))
> ```
>
> The `dist/` output is also produced by `gradlew :fabric:remapJar`. Alternatively, publish
> the API JAR to a local Maven repository and reference it via `mavenLocal()`.

---

## 5. Mod Identity Changes

### 5.1 String changes required

| Location | Old value | New value | Risk |
|----------|-----------|-----------|------|
| `fabric.mod.json` → `id` | `meteor-client` | `simplehack` | **High** — mod ID is used by `FabricLoader.isModLoaded`, save directories, and config paths |
| `fabric.mod.json` → `name` | `Meteor Client` | `SimpleHack` | Low |
| `fabric.mod.json` → `description` | `Based utility mod.` | update as desired | Low |
| `fabric.mod.json` → `custom."meteor-client:color"` value | `145,61,226` | `255,255,255` | Low |
| `fabric.mod.json` → `contact` URLs | meteor URLs | SimpleHack repo | Low |
| `gradle.properties` → `archives_base_name` | `meteor-client` | `simplehack` | Low — output JAR filename only |
| `settings.gradle.kts` → `rootProject.name` | `meteor-client` | `simplehack` | Low |
| `publishing` block in `build.gradle.kts` → `artifactId` | `meteor-client` | `simplehack` | Low |

### 5.2 Strings that should **NOT** change

| Item | Reason |
|------|--------|
| `fabric.mod.json` → `accessWidener` value (`meteor-client.classtweaker`) | The filename of the classtweaker file in `src/main/resources/` must match. Renaming it requires also renaming the file. Rename both together or leave both unchanged. |
| `fabric.mod.json` → `mixins` filenames (`meteor-client.mixins.json`, `meteor-client-baritone.mixins.json`, …) | These filenames are referenced inside the JSON files themselves via `"package"` and by Mixin at runtime. Renaming requires updating all six `.mixins.json` files and `MixinPlugin.java`'s package prefix string — high blast radius, low value. Leave unchanged. |
| `MixinPlugin.java` → `mixinPackage` constant (`meteordevelopment.meteorclient.mixin`) | The Java package of all mixin classes. Changing it requires moving ~200 files. Out of scope. |
| All `src/main/java/meteordevelopment/…` package declarations | Re-packaging is explicitly listed as out of scope in requirements §1.2 and the OOS section. |
| `assets/meteor-client/` resource directory | Minecraft's resource loader resolves assets by mod ID. If the mod ID changes to `simplehack`, the asset folder should technically move to `assets/simplehack/`. However, Meteor's internal code references `meteor-client` asset paths directly in many places. Changing both the mod ID and the asset folder together is a large rename. **Recommendation**: defer asset folder rename to a follow-up; keep `assets/meteor-client/` for this phase. |
| `fabric.mod.json` → `entrypoints."client"` class (`meteordevelopment.meteorclient.MeteorClient`) | Package rename is out of scope. |
| `fabric.mod.json` → `license` (`GPL-3.0`) | Required by req 5.1.5. |

### 5.3 Risk notes

**High-risk changes** (require careful, consistent edits):
- `fabric.mod.json` `id` field: changing to `simplehack` affects the save folder
  (`~/.minecraft/mods/simplehack/`), config paths (`MeteorClient.java` uses
  `FabricLoader.getInstance().getConfigDir().resolve("meteor-client")` — that hardcoded string
  would need updating too), and any inter-mod compatibility checks by name. Audit all
  `"meteor-client"` string literals in the Java source before changing the mod ID.

- `meteor-client.classtweaker`: the `accessWidener` field in `fabric.mod.json` and the
  filename must match exactly. If renamed to `simplehack.classtweaker`, rename the file in
  `src/main/resources/` simultaneously and update the `loom.accessWidenerPath` in
  `build.gradle.kts`.

**Low-risk changes** (safe to do independently):
- Display name, description, contact URLs, `archives_base_name`, `rootProject.name`,
  `custom."meteor-client:color"` value, theme colors.

---

## 6. File-by-File Change Summary

| File (relative to workspace root) | Action | What changes |
|------------------------------------|--------|-------------|
| `build.gradle.kts` | **Create** | New Kotlin DSL build: `addonRegistry`, `addons` parsing, conditional Baritone source set, conditional jar-in-jar embed, dynamic `processResources` mixin array injection, access widener path |
| `settings.gradle.kts` | **Create** | `rootProject.name = "simplehack"`, same plugin repos as Meteor |
| `gradle.properties` | **Create** | `archives_base_name=simplehack`, `baritoneDir=<path>`, JVM args |
| `gradle/libs.versions.toml` | **Copy + modify** | Remove `baritone` version and library entries; all other entries unchanged |
| `src/main/resources/fabric.mod.json` | **Copy + modify** | `id` → `simplehack`; `name` → `SimpleHack`; `custom."meteor-client:color"` → `255,255,255`; `"mixins"` array becomes `${mixins_array}` template token; contact URLs updated |
| `src/main/java/…/gui/themes/meteor/MeteorGuiTheme.java` | **Copy + modify** | 23 `SettingColor` default value changes per §3.1 table |
| `src/main/java/…/pathing/PathManagers.java` | **Copy** | No changes — existing reflection guard satisfies all requirements |
| `src/main/java/…/pathing/BaritonePathManager.java` | **Copy** | No changes |
| `src/main/java/…/pathing/BaritoneSettings.java` | **Copy** | No changes |
| `src/main/java/…/pathing/BaritoneUtils.java` | **Copy** | No changes |
| `src/main/java/…/MixinPlugin.java` | **Copy** | No changes — already guards baritone mixin package correctly |
| `src/main/java/…/MeteorClient.java` | **Copy + audit** | Audit for `"meteor-client"` string literals in config/data path resolution; update if mod ID changes |
| `src/main/resources/meteor-client.mixins.json` | **Copy** | No changes |
| `src/main/resources/meteor-client-baritone.mixins.json` | **Copy** | No changes (conditionally injected by build) |
| `src/main/resources/meteor-client-indigo.mixins.json` | **Copy** | No changes |
| `src/main/resources/meteor-client-sodium.mixins.json` | **Copy** | No changes |
| `src/main/resources/meteor-client-lithium.mixins.json` | **Copy** | No changes |
| `src/main/resources/meteor-client-viafabricplus.mixins.json` | **Copy** | No changes |
| `src/main/resources/meteor-client.classtweaker` | **Copy** | No changes (or rename to `simplehack.classtweaker` — see §5.3) |
| `src/main/resources/assets/meteor-client/` | **Copy** | No changes this phase (see §5.2) |
| `src/main/java/…/mixin/baritone/ComeCommandMixin.java` | **Copy** | No changes |
| `LICENSE` | **Copy** | GPL-3.0 file from Meteor, per req 5.1.1 |
| `src/main/resources/baritone-LICENSE` | **Add** | LGPL-3.0 text bundled per req 5.1.2; add a `from("baritone-LICENSE")` clause in the `jar` task |
| `.kiro/specs/simplehack-combined-mod/requirements.md` | Exists | No changes |
| `.kiro/specs/simplehack-combined-mod/design.md` | **Create** | This document |
| `.kiro/specs/simplehack-combined-mod/.config.kiro` | **Create** | `{"specId":"ec58b368-bd7c-42c7-a44b-f7fbb5cf4707","workflowType":"requirements-first","specType":"bugfix"}` |

---

## 7. Build Workflow Summary

1. **One-time Baritone pre-build** (only needed when Baritone changes):
   ```
   cd C:\Users\Jacob\Documents\baritone-26.2\baritone-26.2
   gradlew :fabric:remapJar
   ```

2. **Normal SimpleHack build** (all addons, default):
   ```
   cd C:\Users\Jacob\Documents\GitHub\SimpleHack
   gradlew build
   # equivalent to: gradlew build -Paddons=all
   ```

3. **Core-only build** (no Baritone):
   ```
   gradlew build -Paddons=none
   ```

4. **Baritone only**:
   ```
   gradlew build -Paddons=baritone
   ```

Output JAR: `build/libs/simplehack-<minecraft_version>-<build_number>.jar`
