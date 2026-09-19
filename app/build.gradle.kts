import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    id("com.google.gms.google-services")
    kotlin("kapt")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getSecretProperty(key: String): String {
    val fromLocalProps = localProperties.getProperty(key)
    if (!fromLocalProps.isNullOrBlank()) return fromLocalProps

    if (project.hasProperty(key)) {
        val fromProject = project.property(key)?.toString()
        if (!fromProject.isNullOrBlank()) return fromProject
    }

    val fromEnv = System.getenv(key)
    if (!fromEnv.isNullOrBlank()) return fromEnv

    return ""
}

val zegoAppIdStr = getSecretProperty("ZEGO_APP_ID")
val zegoAppSign = getSecretProperty("ZEGO_APP_SIGN")
val supabaseUrl = getSecretProperty("SUPABASE_URL")
val supabaseAnonKey = getSecretProperty("SUPABASE_ANON_KEY")
val recaptchaSiteKey = getSecretProperty("RECAPTCHA_ENTERPRISE_SITE_KEY")

if (zegoAppIdStr.isBlank() || zegoAppSign.isBlank() || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
    val missing = mutableListOf<String>()
    if (zegoAppIdStr.isBlank()) missing.add("ZEGO_APP_ID")
    if (zegoAppSign.isBlank()) missing.add("ZEGO_APP_SIGN")
    if (supabaseUrl.isBlank()) missing.add("SUPABASE_URL")
    if (supabaseAnonKey.isBlank()) missing.add("SUPABASE_ANON_KEY")
    throw GradleException(
        "Missing required configuration properties: ${missing.joinToString(", ")}. " +
        "Please define them in 'local.properties' (see local.properties.example), " +
        "as Gradle properties (-Pkey=value), or as environment variables."
    )
}

val zegoAppId = zegoAppIdStr.toLongOrNull()
    ?: throw GradleException("ZEGO_APP_ID must be a valid Long number, but was: '$zegoAppIdStr'")

android {
    namespace = "com.example.kchat"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.kunal.kchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "ZEGO_APP_ID", "${zegoAppId}L")
        buildConfigField("String", "ZEGO_APP_SIGN", "\"$zegoAppSign\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "RECAPTCHA_ENTERPRISE_SITE_KEY", "\"$recaptchaSiteKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes +="META-INF/LICENSE"
            excludes +="META-INF/LICENSE.txt"
            excludes +="META-INF/NOTICE"
            excludes +="META-INF/NOTICE.txt"
            excludes +="mozilla/public-suffix-list.txt"
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")   // modern phones only
            isUniversalApk = false
        }
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.volley)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.dagger.hilt.android)
    implementation(libs.firebase.crashlytics.buildtools)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.dagger.hilt.compose)
    implementation(libs.coil)
    implementation(libs.material)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-ai:16.2.0")
    implementation("com.google.firebase:firebase-appcheck-recaptcha:19.2.1")
    implementation("com.google.firebase:firebase-appcheck-debug:19.4.1")

    implementation("com.github.ZEGOCLOUD:zego_uikit_prebuilt_call_android:3.9.11")
    implementation("com.guolindev.permissionx:permissionx:1.8.0")
    implementation ("io.github.jan-tennert.supabase:storage-kt:1.4.7")
    implementation ("io.github.jan-tennert.supabase:compose-auth:1.4.7")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    val ktor_version = "2.3.13"
    implementation ("io.ktor:ktor-client-android:$ktor_version")
    implementation ("io.ktor:ktor-client-core:$ktor_version")
    implementation ("io.ktor:ktor-utils:$ktor_version")
    implementation ("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation ("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

}

configurations.all {
    resolutionStrategy {
        force("im.zego:express-video:3.17.3")
    }
}