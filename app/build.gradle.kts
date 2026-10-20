import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Local, never-committed configuration (see local.properties.example).
// Values can also come from environment variables (CI).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(name: String, default: String = ""): String =
    localProps.getProperty(name) ?: System.getenv(name) ?: default

fun signingProp(name: String): String? =
    keystoreProps.getProperty(name) ?: System.getenv(name)

android {
    namespace = "app.pulse.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pulse.client"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables.useSupportLibrary = true
        // Russian is the default (values/); drop other locales pulled in by libraries.
        resourceConfigurations += listOf("ru", "en")

        // VK ID app id is not a secret, but every user registers their own app,
        // so it is configurable and can also be entered on the login screen.
        buildConfigField("String", "VK_CLIENT_ID", "\"${prop("VK_CLIENT_ID")}\"")
        // Browser login returns via vk<ID>://vk.com/blank.html, so the scheme is fixed at build time.
        manifestPlaceholders["vkRedirectScheme"] = prop("VK_CLIENT_ID").let { if (it.isBlank()) "vkpulse" else "vk$it" }
        buildConfigField("String", "VK_ID_HOST", "\"${prop("VK_ID_HOST", "id.vk.ru")}\"")
        buildConfigField("String", "VK_API_HOST", "\"${prop("VK_API_HOST", "api.vk.com")}\"")
        buildConfigField("String", "VK_API_VERSION", "\"5.199\"")
    }

    val hasReleaseSigning = signingProp("PULSE_KEYSTORE_PATH") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProp("PULSE_KEYSTORE_PATH")!!)
                storePassword = signingProp("PULSE_KEYSTORE_PASSWORD")
                keyAlias = signingProp("PULSE_KEY_ALIAS")
                keyPassword = signingProp("PULSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // One stable key (when configured) so the SHA-1 registered in VK ID does not change.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Without a real keystore the release APK is signed with the debug key:
            // fine for measuring size/startup and side-loading, not for publishing.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    lint {
        // Lint must never be the reason an APK is missing; reports are uploaded by CI.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("compose-stability.conf")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
