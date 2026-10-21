plugins {
    id("com.android.application")
}

// Stable release signing comes from CI secrets (see README). Without them the
// release APK is signed with the debug key, which is fine for a sideloaded module.
val ksFile = System.getenv("CANDY_KEYSTORE")?.let { file(it) }

android {
    namespace = "com.qmurzik.yacandy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qmurzik.yacandy"
        minSdk = 27
        targetSdk = 36
        versionCode = 4
        versionName = "2.2.0"
    }

    signingConfigs {
        if (ksFile != null && ksFile.exists()) {
            create("candy") {
                storeFile = ksFile
                storePassword = System.getenv("CANDY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CANDY_KEY_ALIAS")
                keyPassword = System.getenv("CANDY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Hook entry points are looked up by name from META-INF/xposed, keep code as is.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("candy") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}
