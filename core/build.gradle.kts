plugins {
    id("com.android.library")
}

android {
    namespace = "com.revcore.android.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Pin to the annotation artifact available with the AndroidX stack used by
    // the reference build; usb-serial-for-android requests 1.8.0 transitively.
    implementation("androidx.annotation:annotation:1.8.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
