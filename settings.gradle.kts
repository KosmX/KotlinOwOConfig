rootProject.name = settings.extra["archives_base_name"] as String
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("fabric-loom").version(settings.extra["loom_version"] as String)
        kotlin("jvm").version(settings.extra["kotlin_version"] as String)
        id("com.google.devtools.ksp").version(settings.extra["ksp_version"] as String)
    }
}
if (File("test").isFile) include(":testmod")