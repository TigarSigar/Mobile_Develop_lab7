import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localValue(name: String): String =
    localProperties.getProperty(name, "")

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.buhlograf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.buhlograf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val yandexClientId = localValue("YANDEX_CLIENT_ID")
        manifestPlaceholders["YANDEX_CLIENT_ID"] = yandexClientId

        buildConfigField("String", "YANDEX_CLIENT_ID", buildConfigString(yandexClientId))
        buildConfigField("String", "APPMETRICA_API_KEY", buildConfigString(localValue("APPMETRICA_API_KEY")))
        buildConfigField("String", "YANDEX_MAPKIT_API_KEY", buildConfigString(localValue("YANDEX_MAPKIT_API_KEY")))

        val vkAppId = localValue("VK_APP_ID")
        val vkClientSecret = localValue("VK_CLIENT_SECRET")
        manifestPlaceholders["VKIDClientID"] = vkAppId
        manifestPlaceholders["VKIDClientSecret"] = vkClientSecret
        manifestPlaceholders["VKIDRedirectHost"] = "vk.ru"
        manifestPlaceholders["VKIDRedirectScheme"] = "vk$vkAppId"
        buildConfigField("String", "VK_APP_ID", buildConfigString(vkAppId))
        buildConfigField("String", "VK_CLIENT_SECRET", buildConfigString(vkClientSecret))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    implementation("io.appmetrica.analytics:analytics:8.1.0")
    implementation("com.yandex.android:authsdk:3.1.3")
    implementation("com.yandex.android:maps.mobile:4.33.1-lite")
    implementation("com.vk.id:vkid:2.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
