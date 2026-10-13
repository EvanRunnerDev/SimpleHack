# SimpleHack Combined Mod — Requirements

## Introduction

SimpleHack is a Minecraft Fabric hacked-client mod forked from Meteor Client and shipped with
Baritone bundled inside a single JAR. Its primary audience is players who have never used a hacked
client before, so the theme and UX are simplified relative to upstream Meteor Client.

This document covers five areas:

1. **Mod Identity** — the new mod ID, name, and how it relates to the upstream sources.
2. **Theme Overhaul** — replacing Meteor's purple palette with a high-contrast white/black scheme.
3. **Configurable Build System** — a Gradle `addons` property that controls what gets bundled.
4. **Baritone Integration** — how Baritone's Fabric module is embedded and conditionally activated.
5. **Licensing** — obligations arising from GPL-3.0 (Meteor) and LGPL-3.0 (Baritone).

---

## 1. Mod Identity

### 1.1 Mod metadata

1.1.1 The mod MUST use mod ID `simplehack` in `fabric.mod.json`.

1.1.2 The mod MUST use display name `SimpleHack` in `fabric.mod.json`.

1.1.3 The `fabric.mod.json` `custom."meteor-client:color"` field MUST be changed from
      `"145,61,226"` to `"255,255,255"` (white).

1.1.4 The entry-point class MUST remain in the `meteordevelopment.meteorclient` package hierarchy
      unless the full codebase is re-packaged (which is out of scope for this phase).

### 1.2 Source relationship

1.2.1 SimpleHack MUST be a fork/copy of the Meteor Client source tree, not a runtime addon or
      wrapper jar.

1.2.2 Baritone MUST be integrated as a bundled dependency compiled into the SimpleHack JAR; players
      MUST NOT need to install a separate Baritone mod.

---

## 2. Theme Overhaul

Meteor Client's `MeteorGuiTheme` hard-codes a dark-purple palette as default `SettingColor`
values. SimpleHack replaces those defaults so the GUI launches with a white-background,
black-text appearance out of the box. Users may still change the colors at runtime through the
existing settings system.

### 2.1 Accent / checkbox colors (currently purple 145, 61, 226)

2.1.1 The default `accentColor` (`SettingColor`) MUST be changed to white: `(255, 255, 255)`.

2.1.2 The default `checkboxColor` (`SettingColor`) MUST be changed to white: `(255, 255, 255)`.

### 2.2 Slider colors (currently purple shades)

2.2.1 The default `sliderHandle` three-state colors MUST be changed to a white/light-grey
      progression: idle `(220, 220, 220)`, hover `(240, 240, 240)`, pressed `(255, 255, 255)`.

2.2.2 The default `sliderLeft` color MUST be changed to a neutral dark grey `(60, 60, 60)` so the
      filled portion of the track remains legible against the white scheme.

### 2.3 Background colors (currently near-black)

2.3.1 The default `backgroundColor` three-state colors MUST be changed to white with high opacity:
      idle `(255, 255, 255, 230)`, hover `(245, 245, 245, 230)`, focused `(235, 235, 235, 230)`.

2.3.2 The default `moduleBackground` color MUST be changed to `(230, 230, 230, 255)` (light grey)
      so active modules are distinguishable from the white panel background.

### 2.4 Text colors (currently white — must become bold black)

2.4.1 The default `textColor` MUST be changed to black: `(0, 0, 0, 255)`.

2.4.2 The default `titleTextColor` MUST be changed to black: `(0, 0, 0, 255)`.

2.4.3 The default `textSecondaryColor` MUST be changed to a dark grey: `(60, 60, 60, 255)`.

2.4.4 The default `separatorText` and `separatorCenter` colors MUST be changed to black:
      `(0, 0, 0, 255)`.

2.4.5 The default `separatorEdges` color MUST be changed to `(0, 0, 0, 150)` (semi-transparent
      black).

### 2.5 Outline colors (currently near-black — must contrast with white backgrounds)

2.5.1 The default `outlineColor` three-state colors MUST be changed to a grey progression:
      idle `(180, 180, 180)`, hover `(140, 140, 140)`, focused `(100, 100, 100)`.

### 2.6 Scrollbar colors

2.6.1 The default `scrollbarColor` three-state colors MUST be changed to grey:
      idle `(180, 180, 180, 200)`, hover `(150, 150, 150, 200)`, active `(120, 120, 120, 200)`.

### 2.7 HUD elements

2.7.1 Any HUD element whose default text or background color is derived from the Meteor accent
      color (`145, 61, 226` or related purple shades) MUST have those defaults updated to the
      white/black scheme described above.

2.7.2 The module-list HUD overlay MUST render module names in black text on a white/light
      background by default.

### 2.8 Scope of theme changes

2.8.1 Theme color changes MUST be implemented by modifying the default `SettingColor` constructor
      arguments in `MeteorGuiTheme` (and any related HUD/renderer color constants) — not by
      removing the settings themselves, so users retain the ability to customize colors.

2.8.2 Non-color visual properties (font, scale, layout, widget shapes) are out of scope for this
      phase.

---

## 3. Configurable Build System

The build must support a Gradle project property `addons` that controls which optional components
are compiled and bundled.

### 3.1 Property values

3.1.1 When the property is absent (no `-Paddons` flag), the build MUST behave identically to
      `-Paddons=all`.

3.1.2 `-Paddons=all` MUST bundle Meteor core, Baritone, and every registered future addon.

3.1.3 `-Paddons=none` MUST produce a Meteor-core-only JAR: no Baritone sources, no Baritone
      dependency, and the Baritone mixin config MUST NOT be listed in `fabric.mod.json`.

3.1.4 `-Paddons=baritone` MUST bundle Meteor core plus Baritone only.

3.1.5 `-Paddons=<comma-separated list>` MUST bundle Meteor core plus only the named addons, e.g.
      `-Paddons=baritone,xaeroplus`.

### 3.2 Conditional Baritone inclusion

3.2.1 When Baritone is included, the Baritone Fabric source set (from
      `baritone-26.2/baritone-26.2/fabric/src`) MUST be compiled into the build.

3.2.2 When Baritone is included, `meteor-client-baritone.mixins.json` MUST appear in the `mixins`
      array of `fabric.mod.json`.

3.2.3 When Baritone is excluded, `meteor-client-baritone.mixins.json` MUST NOT appear in the
      `mixins` array of `fabric.mod.json`.

3.2.4 When Baritone is excluded, the `BaritonePathManager`, `BaritoneSettings`, and
      `BaritoneUtils` classes in `meteordevelopment.meteorclient.pathing` MUST be excluded from
      compilation (or guarded so the build does not fail), and `PathManagers` MUST fall back to
      the existing `NopPathManager`.

3.2.5 When Baritone is excluded, no Baritone API jars or Baritone runtime classes MUST be present
      in the output JAR.

### 3.3 Future addon extensibility

3.3.1 The build script MUST define an addon registry mechanism (e.g. a `Map` or `List` in
      `build.gradle.kts`) so that a new addon can be registered by adding a single entry — without
      modifying the core resolution logic that reads the `addons` property.

3.3.2 Each registered addon entry MUST declare at minimum: a name key, its source path or
      Maven coordinate, and (optionally) a list of mixin config file names it contributes.

---

## 4. Baritone Integration

### 4.1 Bundling

4.1.1 When Baritone is included, the Baritone Fabric module JAR (built from
      `baritone-26.2/baritone-26.2`) MUST be embedded in the SimpleHack output JAR using
      Fabric Loom's jar-in-jar (`include`) mechanism **or** shadow/fat-jar packaging — the choice
      is a design decision, but the player MUST load only one JAR.

4.1.2 The Baritone mixin config (`meteor-client-baritone.mixins.json`) already present in Meteor's
      resources MUST be preserved verbatim when Baritone is included.

### 4.2 Pathing integration

4.2.1 When Baritone is included, `BaritonePathManager` MUST be activated as the
      `IPathManager` implementation (as `PathManagers` already does via `baritone.api.*` checks).

4.2.2 When Baritone is included, `BaritoneSettings` and `BaritoneUtils` MUST compile and function
      against the bundled Baritone API.

4.2.3 When Baritone is excluded, `PathManagers` MUST activate `NopPathManager` so all pathing
      calls are no-ops rather than runtime errors.

### 4.3 Runtime behavior

4.3.1 When running with Baritone included, the `ComeCommandMixin` in
      `meteordevelopment.meteorclient.mixin.baritone` MUST be applied by Mixin at launch.

4.3.2 When running without Baritone, no Baritone-specific mixin configs MUST be loaded by the
      Mixin system (enforced by requirement 3.2.3).

---

## 5. Licensing

5.1.1 The Meteor Client GPL-3.0 license file MUST be retained in the SimpleHack repository and
      included in the output JAR (as Meteor's build already does via `from("LICENSE")`).

5.1.2 The Baritone LGPL-3.0 license MUST be bundled in the output JAR whenever Baritone is
      included in the build.

5.1.3 All source files forked from Meteor Client MUST retain their existing GPL-3.0 copyright
      header: `// This file is part of the Meteor Client distribution …`.

5.1.4 New source files added specifically for SimpleHack MUST include a license header that
      identifies them as derived from Meteor Client under GPL-3.0 and notes the SimpleHack
      project name.

5.1.5 The `license` field in `fabric.mod.json` MUST remain `"GPL-3.0"` (the combined work is
      a GPL-3.0 derivative; the LGPL-3.0 Baritone component is compatible with this).

---

## Out of Scope (This Phase)

- Changes to gameplay modules, keybinds, or command behavior.
- Beginner-mode toggle or simplified tooltips (planned for a future phase).
- XaeroPlus or any addon other than Baritone.
- Re-packaging of the `meteordevelopment.meteorclient` namespace.
- CI/CD pipeline changes.
