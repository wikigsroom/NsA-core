import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
val hallCatalogueUrl = providers.gradleProperty("nsa.catalogUrl").orElse("https://localhost:8443").get().trimEnd('/')
val hallCatalogueUri = URI(hallCatalogueUrl)
require(hallCatalogueUri.scheme == "https" && hallCatalogueUri.host != null && hallCatalogueUri.userInfo == null && hallCatalogueUri.query == null && hallCatalogueUri.fragment == null && !hallCatalogueUrl.any { it == '"' || it == '\\' || it.code < 32 }) {
    "nsa.catalogUrl must be an absolute HTTPS service URL without credentials, query or fragment"
}
android {
    namespace = "org.nsa.hall"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.nsa.hall"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "DEFAULT_ENDPOINT", "\"$hallCatalogueUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    androidResources { noCompress += listOf("json", "pem") }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// Source files remain UTF-8; the Gradle launcher uses the native Windows charset
// for JDK argument files (Gradle #30304) when the workspace contains Chinese.
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.00"))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
