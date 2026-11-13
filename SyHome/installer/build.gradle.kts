plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.syinstaller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.syinstaller"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation("dev.mobile:dadb:1.2.10")
}

// Builds the TV launcher first and embeds its APK inside this phone app
val copyTvApk by tasks.registering(Copy::class) {
    dependsOn(":app:assembleDebug")
    from(project(":app").layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "syhome.apk" }
}
tasks.named("preBuild") { dependsOn(copyTvApk) }
