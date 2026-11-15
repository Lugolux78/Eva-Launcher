plugins { id("com.android.application") }
android {
  namespace = "com.evaone.premium.watchface"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.evaone.premium.watchface"
    minSdk = 34
    targetSdk = 35
    versionCode = 2
    versionName = "0.2"
  }
  buildTypes { getByName("release") { isMinifyEnabled = false } }
}
