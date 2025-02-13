import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    id("fabric-loom")
    kotlin("jvm")
    id("com.google.devtools.ksp")
}
base.archivesName = project.extra["archives_base_name"] as String + "-testmod"
version = project.extra["mod_version"] as String + ".0"
group = project.properties["maven_group"] as String + ".testmod"
loom { runConfigs.configureEach { ideConfigGenerated(true) } }
repositories {
    maven("https://maven.wispforest.io")
    maven("https://maven.terraformersmc.com/releases/")
    mavenLocal()
}
dependencies {
    minecraft("com.mojang", "minecraft", project.extra["minecraft_version"] as String)
    mappings("net.fabricmc", "yarn", project.extra["yarn_mappings"] as String, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", project.extra["loader_version"] as String)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", project.extra["fabric_version"] as String)
    modImplementation("net.fabricmc", "fabric-language-kotlin", project.extra["fabric_language_kotlin_version"] as String)
    modImplementation("io.wispforest", "owo-lib", project.extra["owo_version"] as String)
    modImplementation("com.terraformersmc", "modmenu", project.extra["mod_menu_version"] as String)
    ksp(rootProject)
}

tasks {
    val javaVersion = JavaVersion.toVersion((project.extra["java_version"] as String).toInt())
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        options.release = javaVersion.toString().toInt()
    }
    withType<JavaExec>().configureEach { defaultCharacterEncoding = "UTF-8" }
    withType<Javadoc>().configureEach { options.encoding = "UTF-8" }
    withType<Test>().configureEach { defaultCharacterEncoding = "UTF-8" }
    withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget = JvmTarget.valueOf("JVM_$javaVersion") }
    jar { from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } } }
    processResources { filesMatching("fabric.mod.json") { expand(mutableMapOf("version" to project.extra["mod_version"] as String, "fabricloader" to project.extra["loader_version"] as String, "fabric_api" to project.extra["fabric_version"] as String, "fabric_language_kotlin" to project.extra["fabric_language_kotlin_version"] as String, "minecraft" to project.extra["minecraft_version"] as String, "java" to project.extra["java_version"] as String)) } }
    java {
        toolchain.languageVersion = JavaLanguageVersion.of(javaVersion.toString())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}