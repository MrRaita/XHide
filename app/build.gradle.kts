plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
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
        )
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
