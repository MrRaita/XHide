import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

configurations {
    create("api101Verification")
    create("api102CompileOnly")
    named("compileOnly") {
        extendsFrom(getByName("api102CompileOnly"))
    }
    named("debugCompileOnly") {
        extendsFrom(getByName("api102CompileOnly"))
    }
    named("releaseCompileOnly") {
        extendsFrom(getByName("api102CompileOnly"))
    }
}

android {
    sourceSets {
        named("debug") {
            java.directories.add("src/api102/java")
        }
        named("release") {
            java.directories.add("src/api102/java")
        }
    }


    namespace = "org.lsposed.hidexposed"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.lsposed.hidexposed"
        minSdk = 30
        targetSdk = 37
        versionCode = 104
        versionName = "1.0.4"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            // Opt-in strict mode for official releases: refuse to fall back to the debug key
            // or ship without a pinned companion certificate.
            val strictSigning = System.getenv("XHIDE_STRICT_SIGNING").equals("true", ignoreCase = true)
            if (strictSigning) {
                if (keystoreFile.isNullOrEmpty() || !file(keystoreFile).exists()) {
                    throw GradleException("XHIDE_STRICT_SIGNING is set but KEYSTORE_FILE is missing or does not exist")
                }
                if (System.getenv("RELEASE_CERT_SHA256").isNullOrBlank()) {
                    throw GradleException("XHIDE_STRICT_SIGNING is set but RELEASE_CERT_SHA256 is empty")
                }
            }
            if (!keystoreFile.isNullOrEmpty() && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                val debugStore = getByName("debug").storeFile
                if (debugStore != null && !debugStore.exists()) {
                    debugStore.parentFile?.mkdirs()
                    val javaHome = System.getProperty("java.home")
                    val keytoolBin = file("$javaHome/bin/keytool").takeIf { it.exists() }?.absolutePath ?: "keytool"
                    try {
                        ProcessBuilder(
                            keytoolBin,
                            "-genkeypair",
                            "-alias", "androiddebugkey",
                            "-keypass", "android",
                            "-keystore", debugStore.absolutePath,
                            "-storepass", "android",
                            "-dname", "CN=Android Debug,O=Android,C=US",
                            "-keyalg", "RSA",
                            "-keysize", "2048",
                            "-validity", "10000"
                        ).inheritIO().start().waitFor()
                    } catch (_: Throwable) {
                    }
                }
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"${System.getenv("RELEASE_CERT_SHA256") ?: ""}\"")
        }
        debug {
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
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
        )
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    if (name == "compileDebugKotlin" || name == "compileReleaseKotlin") {
        source("src/api102/java")
    }
}

val verifyApi101Core by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles the production modern core against libxposed API 101 only."

    val api101VerificationSources = files(
        fileTree("src/main/java/org/lsposed/hidexposed/core") {
            exclude("AppDiagnostics.kt")
        },
        fileTree("src/main/java/org/lsposed/hidexposed/data"),
        fileTree("src/api101/java"),
        fileTree(layout.buildDirectory.dir("generated/source/buildConfig/debug")) {
            include("**/BuildConfig.java")
        }
    )
    val api101VerificationClasspath = files(
        provider {
            tasks.named("compileDebugKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class)
                .get().libraries.filter {
                    it.name != "api-102.0.0-api.jar"
                }
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
    val sourceFiles = api101VerificationSources.elements.map { files ->
        files.map { it.asFile }.sortedBy { it.absolutePath }.map { it.absolutePath }
    }
    val classpathText = api101VerificationClasspath.elements.map { files ->
        files.joinToString(File.pathSeparator) { it.asFile.absolutePath }
    }
    val output = layout.buildDirectory.dir("api101-verification/classes")

    inputs.files(api101VerificationSources, api101VerificationClasspath)
    outputs.dir(output)
    dependsOn("generateDebugBuildConfig")

    classpath = files(configurations.getByName("kotlinCompilerClasspath"))
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    argumentProviders.add(
        org.gradle.process.CommandLineArgumentProvider {
            listOf(
                "-no-stdlib",
                "-no-reflect",
                "-jvm-target", "21",
                "-classpath", classpathText.get(),
                "-d", output.get().asFile.absolutePath
            ) + sourceFiles.get()
        }
    )
    doFirst { output.get().asFile.mkdirs() }
}

dependencies {
    add("api101Verification", "io.github.libxposed:api:101.0.1")
    add("api101Verification", "org.json:json:20240303")
    add("kotlinCompilerClasspath", "org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    add("api102CompileOnly", "io.github.libxposed:api:102.0.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("org.json:json:20240303")
}
