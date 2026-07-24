plugins {
    id("com.android.application")
}

android {
    namespace = "com.thepiratebrowser.android"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.thepiratebrowser.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        val putIoClientId = providers.gradleProperty("PUTIO_CLIENT_ID")
            .orElse(providers.environmentVariable("PUTIO_CLIENT_ID"))
            .getOrElse("")
        val putIoOauthToken = providers.gradleProperty("PUTIO_OAUTH_TOKEN")
            .orElse(providers.environmentVariable("PUTIO_OAUTH_TOKEN"))
            .getOrElse("")
        buildConfigField("String", "PUTIO_CLIENT_ID", "\"$putIoClientId\"")
        buildConfigField("String", "PUTIO_OAUTH_TOKEN", "\"$putIoOauthToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.3")
}
