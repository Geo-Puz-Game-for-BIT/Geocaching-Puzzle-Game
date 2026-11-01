import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val amapApiKey = localProperties.getProperty("AMAP_API_KEY")
    ?: System.getenv("AMAP_API_KEY")
    ?: ""
val apiBaseUrl = localProperties.getProperty("API_BASE_URL")
    ?: System.getenv("API_BASE_URL")
    ?: ""
val demoAuthToken = localProperties.getProperty("DEMO_AUTH_TOKEN")
    ?: System.getenv("DEMO_AUTH_TOKEN")
    ?: ""
val demoUserId = localProperties.getProperty("DEMO_USER_ID")
    ?: System.getenv("DEMO_USER_ID")
    ?: "usr_demo01"

android {
    namespace = "com.bitse.geotreasure"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bitse.geotreasure"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        buildConfigField("boolean", "HAS_AMAP_KEY", amapApiKey.isNotBlank().toString())
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEMO_AUTH_TOKEN", "\"${demoAuthToken.replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEMO_USER_ID", "\"${demoUserId.replace("\"", "\\\"")}\"")
        buildConfigField(
            "boolean",
            "HAS_CLOUD_API",
            (apiBaseUrl.isNotBlank() && demoAuthToken.isNotBlank()).toString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.amap.map.location.search)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
