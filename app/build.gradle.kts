import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Chave da YouTube Data API: em local.properties (fora do git) ou na variável YOUTUBE_API_KEY.
// Sem chave o app funciona; só a busca de vídeos fica desligada.
val youtubeApiKey: String = localProps.getProperty("youtube.apiKey") ?: System.getenv("YOUTUBE_API_KEY") ?: ""

// Painel do treinador (Firebase): também em local.properties, sem o google-services.json. Sem eles o app
// funciona igual e a opção "Painel do treinador" não aparece. Passo a passo em docs/painel-web.md.
fun firebaseProp(key: String): String = localProps.getProperty("firebase.$key") ?: ""

// Onde o app procura versão nova (publicada por web/scripts/publicar-app.mjs no Hosting do painel).
val updateUrl: String = localProps.getProperty("update.url")
    ?: firebaseProp("projectId").takeIf { it.isNotBlank() }?.let { "https://$it.web.app/app/version.json" }
    ?: ""

// Chave de upload da Play Store: keystore.properties na raiz (fora do git). Sem ele, o release sai sem assinatura.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.myvitory.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.myvitory.app"
        minSdk = 26
        targetSdk = 36
        // Cada envio à Play Store precisa de um versionCode maior que o anterior.
        versionCode = 14
        versionName = "0.13.1"
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseProp("apiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProp("appId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProp("projectId")}\"")
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Emulador do Firebase (ex.: 10.0.2.2 no emulador do Android, ou o IP do PC na rede). Só no debug.
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"${firebaseProp("emulatorHost")}\"")
        }
        release {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sem a chave de upload, assina com a de debug: dá para instalar o release (rápido) direto no celular.
            // Para a Play Store, o keystore.properties é obrigatório.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric 4.17 no JDK 25 precisa acessar internos do java.base.
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.glance.appwidget)
    // Instala os perfis de compilação (do Compose e do app) mesmo fora da Play Store: abre e rola mais liso.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.youtube.player)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
