import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
    alias(libs.plugins.errorprone)
}

// ---------------------------------------------------------------------------
// Addon registry
// ---------------------------------------------------------------------------

data class AddonConfig(
    val sourcePaths: List<String>,
    val resourcePaths: List<String>,
    val mixinConfigs: List<String>
)

val addonRegistry: Map<String, AddonConfig> = mapOf(
    "baritone" to AddonConfig(
        sourcePaths = listOf(
            "src/baritone/api/java",
            "src/baritone/main/java",
            "src/baritone/launch/java",
            "src/baritone/fabric/java"
        ),
        resourcePaths = listOf(),
        mixinConfigs = listOf(
            "meteor-client-baritone.mixins.json",
            "mixins.baritone-meteor.json"
        )
    )
)

val requestedAddons: Set<String> = run {
    val raw = providers.gradleProperty("addons").orNull
    when {
        raw == null || raw == "all" -> addonRegistry.keys
        raw == "none"               -> emptySet()
        else                        -> raw.split(",")
                                         .map { it.trim() }
                                         .filter { it.isNotEmpty() }
                                         .toSet()
    }
}

val activeAddons: Map<String, AddonConfig> =
    addonRegistry.filterKeys { it in requestedAddons }

val baritoneActive = "baritone" in activeAddons

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()
val runErrorProne = providers.gradleProperty("errorprone").isPresent

base {
    archivesName = archivesBaseName
    group = mavenGroup
    val suffix = providers.gradleProperty("build_number").getOrElse("local")
    version = "${libs.versions.minecraft.get()}-$suffix"
}

repositories {
    maven { name = "meteor-maven"; url = uri("https://maven.meteordev.org/releases") }
    maven { name = "meteor-maven-snapshots"; url = uri("https://maven.meteordev.org/snapshots") }
    maven { name = "ViaVersion"; url = uri("https://repo.viaversion.com") }
    maven { name = "babbaj-repo"; url = uri("https://babbaj.github.io/maven/") }
    mavenCentral()
    exclusiveContent {
        forRepository { maven { name = "modrinth"; url = uri("https://api.modrinth.com/maven") } }
        filter { includeGroup("maven.modrinth") }
    }
}

val modInclude = configurations.create("modInclude")
val jij = configurations.create("jij")

configurations {
    implementation.configure { extendsFrom(modInclude) }
    include.configure { extendsFrom(modInclude) }
    implementation.configure { extendsFrom(jij) }
    include.configure { extendsFrom(jij) }
}

val launcher = sourceSets.create("launcher") {
    java.srcDir("src/launcher/java")
}

val baritoneApiSourceSet = sourceSets.create("baritoneApi") {
    java.srcDir("src/baritone/api/java")
    compileClasspath += sourceSets.getByName("main").compileClasspath
    runtimeClasspath += sourceSets.getByName("main").runtimeClasspath
}
sourceSets.named("main") {
    compileClasspath += baritoneApiSourceSet.output
    runtimeClasspath += baritoneApiSourceSet.output
}

val baritoneSourceSet = if (baritoneActive) {
    sourceSets.create("baritone") {
        java.srcDirs("src/baritone/main/java", "src/baritone/launch/java", "src/baritone/fabric/java")
        // Exclude files that depend on optional third-party mods (Litematica, Schematica)
        // These are guarded at runtime via Class.forName but have static imports
        java.exclude("baritone/utils/schematic/litematica/**")
        java.exclude("baritone/utils/schematic/schematica/SchematicAdapter.java")
        java.exclude("baritone/utils/schematic/schematica/SchematicaHelper.java")
        resources.setSrcDirs(emptyList<Any>())
        compileClasspath += sourceSets.getByName("main").compileClasspath + baritoneApiSourceSet.output
        runtimeClasspath += sourceSets.getByName("main").runtimeClasspath + baritoneApiSourceSet.output
    }
} else null

if (baritoneSourceSet != null) {
    sourceSets.named("main") {
        compileClasspath += baritoneSourceSet.output
        runtimeClasspath += baritoneSourceSet.output
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    val fapiVersion = libs.versions.fabric.api.get()
    modInclude(fabricApi.module("fabric-api-base", fapiVersion))
    modInclude(fabricApi.module("fabric-resource-loader-v1", fapiVersion))
    compileOnly(fabricApi.module("fabric-renderer-indigo", fapiVersion))
    compileOnly(libs.sodium) { isTransitive = false }
    compileOnly(libs.lithium) { isTransitive = false }
    compileOnly(libs.iris) { isTransitive = false }
    compileOnly(libs.viafabricplus) { isTransitive = false }
    compileOnly(libs.viafabricplus.api) { isTransitive = false }
    compileOnly(libs.modmenu)
    jij("dev.babbaj:nether-pathfinder:1.6")
    implementation("com.google.code.findbugs:jsr305:3.0.2") { isTransitive = false }
    jij(libs.orbit)
    jij(libs.starscript)
    jij(libs.discord.ipc)
    jij(libs.reflections)
    jij(libs.netty.handler.proxy) { isTransitive = false }
    jij(libs.netty.codec.socks) { isTransitive = false }
    jij(libs.waybackauthlib)
    jij(libs.minecraft.auth) {
        exclude("com.google.code.gson")
        exclude("com.google.errorprone")
    }
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt())) }
    if (System.getenv("CI")?.toBoolean() == true) { withSourcesJar(); withJavadocJar() }
}

val jijExcluded = setOf("org.slf4j")
listOf("api", "implementation", "include").forEach { configName ->
    configurations.named(configName).configure {
        defaultDependencies {
            configurations.getByName("jij").incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .forEach { id ->
                    val notation = "${id.group}:${id.module}:${id.version}"
                    if (jijExcluded.none { notation.contains(it) }) {
                        add(project.dependencies.create(notation) { isTransitive = false })
                    }
                }
        }
    }
}

loom {
    accessWidenerPath = file("src/main/resources/meteor-client.classtweaker")
}

fun toMinecraftCompat(version: String): String {
    val stable = Regex("""^(\d{2})\.([1-9]\d*)(?:\.(\d+))?$""")
    stable.matchEntire(version)?.let { val (year, drop, _) = it.destructured; return "~$year.$drop" }
    val pre = Regex("""^(\d{2})\.([1-9]\d*)-pre[-.](\d+)$""")
    pre.matchEntire(version)?.let { return version.replace("-pre-", "-pre.") }
    val rc = Regex("""^(\d{2})\.([1-9]\d*)-rc[-.](\d+)$""")
    rc.matchEntire(version)?.let { return version.replace("-rc-", "-rc.") }
    return version
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val propertyMap = mapOf(
            "version"           to project.version,
            "build_number"      to providers.gradleProperty("build_number").getOrElse(""),
            "commit"            to providers.gradleProperty("commit").getOrElse(""),
            "jdk_version"       to libs.versions.jdk.get(),
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "loader_version"    to libs.versions.fabric.loader.get()
        )
        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    named<JavaCompile>("compileLauncherJava").configure {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.compilerArgs.add("-Xlint:-options")
    }

    jar {
        inputs.property("archivesName", archivesBaseName)
        from("LICENSE") { rename { "${it}_simplehack" } }
        if (baritoneActive) { from("src/main/resources/baritone-LICENSE") { rename { "LICENSE_baritone" } } }
        from(launcher.output)
        from(baritoneApiSourceSet.output)
        if (baritoneSourceSet != null) { from(baritoneSourceSet.output) }
        manifest { attributes["Main-Class"] = "meteordevelopment.meteorclient.Main" }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
        options.errorprone.enabled.set(runErrorProne)
        if (runErrorProne) {
            options.errorprone {
                check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
                option("NullAway:AnnotatedPackages", "meteordevelopment.meteorclient")
                option("NullAway:JSpecifyMode", "true")
                option("UnusedMethod:ExcludedAnnotations", "meteordevelopment.orbit.EventHandler")
            }
        }
    }

    javadoc {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("Xdoclint:none", "-quiet")
            addStringOption("encoding", "UTF-8")
            addStringOption("charSet", "UTF-8")
        }
    }

    build { if (System.getenv("CI")?.toBoolean() == true) { dependsOn("javadocJar") } }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "simplehack"
            version = "${libs.versions.minecraft.get()}-SNAPSHOT"
        }
    }
    repositories {
        maven("https://maven.meteordev.org/snapshots") {
            name = "meteor-maven"
            credentials {
                username = System.getenv("MAVEN_METEOR_ALIAS")
                password = System.getenv("MAVEN_METEOR_TOKEN")
            }
            authentication { create<BasicAuthentication>("basic") }
        }
    }
}