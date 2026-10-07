plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trailmap.gps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trailmap.gps"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
        fun secret(name: String): String {
            // Public / CI APKs must never bake in personal NPS/RIDB keys.
            // Never read secrets from process environment — only from gitignored local.properties.
            if (System.getenv("ARETE_PUBLIC_BUILD") == "1") return ""
            val local = rootProject.file("local.properties")
            if (!local.exists()) return ""
            val raw = local.readLines()
                .firstOrNull { it.startsWith("$name=") && !it.trimStart().startsWith("#") }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
            // Refuse obviously-placeholder values so nothing accidental ships.
            if (raw.isEmpty() || raw.contains("YOUR_") || raw.equals("changeme", ignoreCase = true)) return ""
            return raw.replace("\\", "\\\\").replace("\"", "\\\"")
        }
        buildConfigField("String", "NPS_API_KEY", "\"${secret("NPS_API_KEY")}\"")
        buildConfigField("String", "RIDB_API_KEY", "\"${secret("RIDB_API_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation("org.json:json:20240303")
}
