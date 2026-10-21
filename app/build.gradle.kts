import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version comes from the release tag (CI passes -PappVersionName=1.2.3 or 1.2.3-beta.4).
// versionCode = (major*10000 + minor*100 + patch) * 100 + (pre-release number, or 99 for a final release),
// so 1.2.3-beta.4 < 1.2.3 < 1.2.4. Keep minor/patch below 100 and pre-release numbers below 99.
val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.0.0-dev")
val appVersionCode: Int = run {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.]*?(\d+))?""").find(appVersionName)
        ?: return@run 1
    val (major, minor, patch, pre) = match.destructured
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    val suffix = if (appVersionName.contains('-')) (pre.toIntOrNull() ?: 0).coerceIn(0, 98) else 99
    (base * 100 + suffix).coerceAtLeast(1)
}

// Release signing: environment variables (CI) take precedence over keystore.properties (local, git-ignored).
// Unless all four values are present, release builds fall back to the debug key so they can still be built locally.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun signingValue(env: String, key: String): String? =
    (providers.environmentVariable(env).orNull ?: keystoreProperties.getProperty(key))?.takeIf { it.isNotBlank() }
val releaseStoreFile = signingValue("APPLINGO_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("APPLINGO_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("APPLINGO_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("APPLINGO_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }
if (!hasReleaseSigning && releaseStoreFile != null) {
    logger.warn("Release signing is incomplete (check keystore.properties); release builds will use the debug key.")
}

android {
    namespace = "io.github.ernisn.applingo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ernisn.applingo"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    androidResources {
        generateLocaleConfig = true
        // Drop library translations for languages Applingo itself doesn't ship, so framework widgets
        // never appear in a different language than the rest of the UI.
        localeFilters += listOf("en", "zh-rCN")
    }

    dependenciesInfo {
        // Not published to Google Play; keep the signing block free of the encrypted dependency blob.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
