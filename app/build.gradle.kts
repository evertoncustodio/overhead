plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.evertoncustodio.overhead"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.evertoncustodio.overhead"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    testImplementation(libs.junit)
}
