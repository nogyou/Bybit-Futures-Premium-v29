plugins { id("com.android.application") }
android {
    namespace = "com.btc.analyzer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.btc.analyzer.premiumv29"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.9.0-dev1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.squareup.okhttp3:okhttp:4.12.0") }
