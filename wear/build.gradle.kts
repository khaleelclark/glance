plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.khaleel.glance.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.khaleel.glance"
        minSdk = 30
        targetSdk = 37
        versionCode = 35
        versionName = "0.30.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.named("main") {
        kotlin.directories.add("../wear-common/src/main/kotlin")
    }
}

dependencies {
    constraints {
        implementation(libs.fragment) { because("Wearable Play services must not downgrade permission handling to Fragment 1.0") }
    }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.complications)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
