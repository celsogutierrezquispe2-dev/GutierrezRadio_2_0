plugins {
    id("com.android.application")
}
android {
    namespace = "com.gutierrez.radio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gutierrez.radio"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
