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
        // Cada corrida de GitHub sube el numero: Android solo actualiza
        // encima si el versionCode no baja.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
    }

    // Llave fija para poder actualizar sin desinstalar. Cada maquina de GitHub
    // generaba su propia llave de debug, y Android rechaza una actualizacion
    // firmada con otra llave ("paquete no valido"). La llave llega por
    // secretos del repositorio, nunca dentro de el: el repo es publico.
    val keystore = System.getenv("PESO_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("fija") {
                storeFile = keystore
                storeType = "pkcs12"
                storePassword = System.getenv("PESO_KEYSTORE_PASS")
                keyAlias = "pesototal"
                keyPassword = System.getenv("PESO_KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("fija")
        }
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
    // Escaner por camara de Play Services: sin permiso de camara ni UI propia.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
}
