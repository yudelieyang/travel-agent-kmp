plugins { id("com.android.application"); kotlin("plugin.compose") }
android {
    namespace = "travel.android"
    compileSdk = 37
    defaultConfig { applicationId = "travel.android"; minSdk = 26; targetSdk = 37; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("../core/src/test/resources")
}
dependencies {
    implementation(project(":composeApp"))
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.13.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
