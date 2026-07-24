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
        buildConfigField("String", "PUTIO_CLIENT_ID", "\"$putIoClientId\"")
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
