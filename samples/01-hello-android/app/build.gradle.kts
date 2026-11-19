plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.hellorevcore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.hellorevcore"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
