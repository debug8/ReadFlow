plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.readflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.readflow.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    // Тести Compose ганяються на JVM через Robolectric — без емулятора.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

/**
 * Спільні дані обох платформ (норми WPM і тексти-зразки) лежать у `shared/`
 * і не дублюються в коді Android — це непорушне правило №2 проєкту.
 * У рантаймі Android папки `shared/` не існує, тому вона синхронізується в assets
 * на етапі збірки.
 *
 * `Sync`, а не `Copy`: `Copy` не прибирає з призначення файли, яких уже немає
 * в джерелі, і видалений зразок лишався б у зібраному APK до наступного `clean`.
 */
val syncSharedAssets by tasks.registering(Sync::class) {
    from(rootProject.file("../shared")) {
        include("norms.json", "samples/**")
        exclude("**/README.md")
    }
    into(layout.buildDirectory.dir("generated/sharedAssets"))
}

android {
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/sharedAssets"))
}

tasks.named("preBuild") { dependsOn(syncSharedAssets) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)

    // Історія замірів — Room (`SPEC_ANDROID.md`, розділ 5; Задача 8).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Імена тестів у логу збірки.
 *
 * Не косметика: вічний цикл таймера у `viewModelScope` вішає `runTest`
 * намертво, і без цього рядка в логу видно лише «> Task :app:testDebugUnitTest»
 * без жодної підказки, який саме тест не завершився.
 */
tasks.withType<Test>().configureEach {
    testLogging { events("started", "failed") }
}
