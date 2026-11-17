plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "uk.co.flipwhatsapphangup"
    compileSdk = 36
    defaultConfig {
        applicationId = "uk.co.flipwhatsapphangup"
        minSdk = 30
        targetSdk = 36
        versionCode = 17
        versionName = "0.17"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
