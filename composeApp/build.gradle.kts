plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}
kotlin {
    jvm("desktop")
    android { namespace = "travel.app"; compileSdk = 37; minSdk = 24; androidResources { enable = true } }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies { implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3) }
        val desktopMain by getting { dependencies { implementation(compose.desktop.currentOs) } }
        val androidMain by getting { dependencies { implementation("androidx.activity:activity-compose:1.13.0") } }
    }
}
compose.desktop { application { mainClass = "travel.MainKt" } }
