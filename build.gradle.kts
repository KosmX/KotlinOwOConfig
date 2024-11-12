import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    kotlin("jvm")
    `maven-publish`
}
base.archivesName = project.extra["archives_base_name"] as String
version = project.extra["mod_version"] as String
group = project.extra["maven_group"] as String
repositories {
    mavenCentral()
    maven ("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.wispforest.io")
}
dependencies {
    implementation("io.wispforest", "owo-lib", project.extra["owo_version"] as String)
    implementation("com.google.devtools.ksp", "symbol-processing-api", project.extra["ksp_version"] as String)
    implementation("com.squareup", "kotlinpoet-ksp", project.extra["kotlinpoet_version"] as String)
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
    java {
        toolchain.languageVersion = JavaLanguageVersion.of(javaVersion.toString())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}
val env: Map<String, String> = System.getenv()
publishing {
    publications {
        create<MavenPublication>("ksp_stuff") {
            artifactId = "ksp-owo-config"
            from(components["java"])
        }
    }
    repositories {
        if ("MAVEN_USER" in env) {
            maven("https://maven.kosmx.dev/") {
                credentials {
                    username = env["MAVEN_USER"]
                    password = env["MAVEN_PASS"]
                }
            }
        } else {
            mavenLocal()
        }
    }
}
