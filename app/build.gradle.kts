plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "uk.co.flipwhatsapphangup"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "uk.co.flipwhatsapphangup"
        minSdk = 30
        targetSdk = 36
        versionCode = 39
        versionName = "0.20"
        buildConfigField("boolean", "DIAGNOSTICS_BUILD", "false")
        buildConfigField("String", "DIAGNOSTICS_ENDPOINT", "\"\"")
    }
    val releaseKeystore = System.getenv("PABI_FLIP_KEYSTORE")
    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs {
            create("distribution") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PABI_FLIP_STORE_PASSWORD")
                keyAlias = System.getenv("PABI_FLIP_KEY_ALIAS")
                keyPassword = System.getenv("PABI_FLIP_KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("distribution")
            }
        }
    }
    buildTypes {
        create("diagnostics") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diagnostics"
            versionNameSuffix = "-diagnostics.1"
            buildConfigField("boolean", "DIAGNOSTICS_BUILD", "true")
            val endpoint = providers.gradleProperty("pabiDiagnosticsEndpoint").orElse("").get()
            buildConfigField("String", "DIAGNOSTICS_ENDPOINT", "\"$endpoint\"")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
