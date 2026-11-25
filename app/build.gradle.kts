import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// libxposed API: the module core is written against 101 (the default). The 102 sources (hot reload) are compiled
// against 102 and guarded at runtime; to switch, set targetApiVersion=102 and autoHotReload=true in
// src/main/resources/META-INF/xposed/module.prop.
configurations {
    create("api101Verification")
    create("api102CompileOnly")
    named("compileOnly") { extendsFrom(getByName("api102CompileOnly")) }
    named("debugCompileOnly") { extendsFrom(getByName("api102CompileOnly")) }
    named("releaseCompileOnly") { extendsFrom(getByName("api102CompileOnly")) }
}

android {
    namespace = "dev.fadalias"
    compileSdk = 37

    sourceSets {
        named("debug") { java.directories.add("src/api102/java") }
        named("release") { java.directories.add("src/api102/java") }
    }

    defaultConfig {
        applicationId = "dev.hfw"
        minSdk = 26 // lower bound required by libxposed API 101
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    val customKeystore = System.getenv("FADALIAS_KEYSTORE")?.takeIf { it.isNotEmpty() && file(it).exists() }
    if (customKeystore != null) {
        signingConfigs {
            create("fadaliasRelease") {
                storeFile = file(customKeystore)
                storePassword = System.getenv("FADALIAS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FADALIAS_KEY_ALIAS")
                keyPassword = System.getenv("FADALIAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideload builds are signed with the debug key unless FADALIAS_KEYSTORE* is set.
            signingConfig = signingConfigs.getByName(if (customKeystore != null) "fadaliasRelease" else "debug")
        }
        all {
            // Optional pin: only a manager signed with this certificate may talk to the hook (empty = package name + any signature).
            buildConfigField("String", "PINNED_CERT_SHA256", "\"${System.getenv("FADALIAS_CERT_SHA256") ?: ""}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    if (name == "compileDebugKotlin" || name == "compileReleaseKotlin") {
        source("src/api102/java")
    }
}

// Compiles the production core against libxposed API 101 only, so nothing from 102 can leak into the default path.
// Run: gradle :app:verifyApi101Core
val verifyApi101Core by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles the modern core against libxposed API 101 only."

    val sources = files(
        fileTree("src/main/java/dev/fadalias/core"),
        fileTree("src/main/java/dev/fadalias/state"),
        file("src/main/java/dev/fadalias/Identity.kt"),
        file("src/main/java/dev/fadalias/data/DurableFiles.kt"),
        fileTree("src/api101/java"),
        fileTree(layout.buildDirectory.dir("generated/source/buildConfig/debug")) { include("**/BuildConfig.java") },
    )
    val classpathFiles = files(
        provider {
            tasks.named("compileDebugKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class)
                .get().libraries.filter { !it.name.startsWith("api-102") }
        },
        configurations.getByName("api101Verification").incoming.artifactView {
            attributes {
                attribute(
                    com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE,
                    com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                )
            }
        }.files
    )
    val sourceList = sources.elements.map { f -> f.map { it.asFile }.sortedBy { it.absolutePath }.map { it.absolutePath } }
    val classpathText = classpathFiles.elements.map { f -> f.joinToString(File.pathSeparator) { it.asFile.absolutePath } }
    val output = layout.buildDirectory.dir("api101-verification/classes")

    inputs.files(sources, classpathFiles)
    outputs.dir(output)
    dependsOn("generateDebugBuildConfig")

    classpath = files(configurations.getByName("kotlinCompilerClasspath"))
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    argumentProviders.add(
        org.gradle.process.CommandLineArgumentProvider {
            listOf("-no-stdlib", "-no-reflect", "-jvm-target", "21", "-classpath", classpathText.get(), "-d", output.get().asFile.absolutePath) + sourceList.get()
        }
    )
    doFirst { output.get().asFile.mkdirs() }
}

dependencies {
    add("api101Verification", "io.github.libxposed:api:101.0.1")
    add("kotlinCompilerClasspath", "org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    add("api102CompileOnly", "io.github.libxposed:api:102.0.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
