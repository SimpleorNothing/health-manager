plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 signingConfigs {
  create("release") {
   val ks = System.getenv("ANDROID_KEYSTORE_PATH")
   if (!ks.isNullOrBlank()) {
    storeFile = file(ks)
    storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    keyAlias = System.getenv("ANDROID_KEY_ALIAS")
    keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
   }
  }
 }
 namespace = "io.github.simpleornothing.healthmanager"; compileSdk = 36
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig { applicationId = "io.github.simpleornothing.healthmanager"; minSdk = 28; targetSdk = 35; versionCode = 4; versionName = "0.3.0"; buildConfigField("String","HEALTH_API_TOKEN","\""+(System.getenv("HEALTH_API_TOKEN") ?: "")+"\"") }
 buildFeatures { buildConfig = true }
 buildTypes { getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") } }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-ktx:1.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.health.connect:connect-client:1.1.0")
}