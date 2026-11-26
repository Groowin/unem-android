plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "kz.unem.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "kz.unem.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1"
    }
    // Тұрақты қолтаңба: әр жаңарту ескі қосымшаның үстіне орнатылсын (деректер жоғалмасын)
    val ksPath = System.getenv("UNEM_KS_PATH")
    if (ksPath != null && File(ksPath).exists()) {
        signingConfigs {
            create("unem") {
                storeFile = File(ksPath)
                storePassword = System.getenv("UNEM_KS_PASS")
                keyAlias = System.getenv("UNEM_KEY_ALIAS") ?: "unem"
                keyPassword = System.getenv("UNEM_KS_PASS")
            }
        }
        buildTypes {
            getByName("debug") { signingConfig = signingConfigs.getByName("unem") }
            getByName("release") { signingConfig = signingConfigs.getByName("unem") }
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
