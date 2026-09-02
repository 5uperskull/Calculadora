plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.icestar.pesototal"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.icestar.pesototal"
        minSdk = 26
        // targetSdk 33 a proposito: evita las restricciones de foreground
        // service de Android 14, que no aportan nada a un APK que se instala
        // a mano y nunca pasa por Play.
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
