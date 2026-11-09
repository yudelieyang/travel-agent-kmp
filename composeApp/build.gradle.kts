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
        commonMain.dependencies { implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.components.resources) }
        commonTest.dependencies { implementation(kotlin("test")) }
        val jvmMain by creating { dependsOn(commonMain.get()); dependencies { implementation(project(":core")); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0") } }
        val desktopMain by getting { dependsOn(jvmMain); dependencies { implementation(compose.desktop.currentOs) } }
        val desktopTest by getting { resources.srcDir("../core/src/test/resources"); dependencies { implementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.2"); implementation("junit:junit:4.13.2") } }
        val androidMain by getting { dependsOn(jvmMain); dependencies { implementation("androidx.activity:activity-compose:1.13.0") } }
    }
}
compose.desktop { application { mainClass = "travel.MainKt" } }
