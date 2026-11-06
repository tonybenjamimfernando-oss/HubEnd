import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * HubOps 1.29.0 — núcleo e motores autónomos do HubEnd (biblioteca Android).
 * Preserva integralmente o código, os testes e as dependências do projeto HubOps.
 * Alterações de integração (documentadas em docs/AUDITORIA.md):
 *  - com.android.application -> com.android.library (o APK é montado pelo módulo :app);
 *  - compileSdk 34 -> 36 (exigido pelas gramáticas Tree-sitter e pelo Capacitor 7); targetSdk mantém-se 35;
 *  - kotlinOptions -> compilerOptions (DSL atual do Kotlin 2.2).
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val omegaReleasePublicKey = providers.gradleProperty("OMEGA_RELEASE_PUBLIC_KEY_B64")
    .orElse(providers.environmentVariable("OMEGA_RELEASE_PUBLIC_KEY_B64")).getOrElse("")

android {
    namespace = "dev.hubops"
    compileSdk = 36

    defaultConfig {
        // HubEnd: os testes HubOps usam nomes com espaços (DEX 040, API 30+); só a execução
        // em dispositivo (-PhubendDeviceTests) sobe o minSdk. O APK continua com minSdk 26.
        minSdk = if (providers.gradleProperty("hubendDeviceTests").isPresent) 30 else 26
        buildConfigField("String", "OMEGA_RELEASE_PUBLIC_KEY_B64", "\"$omegaReleasePublicKey\"")
        buildConfigField("String", "HUBOPS_VERSION", "\"1.29.0\"")
        consumerProguardFiles("proguard-rules.pro")
        // HubEnd: os mesmos testes HubOps também correm em ART (dispositivo/emulador),
        // onde o motor de regex é ICU e o comportamento difere da JVM.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        getByName("androidTest").java.srcDirs("src/test/java", "src/androidTest/java")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Tree-sitter ASTs reais para Kotlin/Kotlin DSL, Java e Python.
    implementation("io.github.tree-sitter:ktreesitter-android:0.25.1")
    implementation("cn.enaium.treesitter:treesitter-languages-kotlin-kmp-android:0.3.8.2")
    implementation("cn.enaium.treesitter:treesitter-languages-java-kmp-android:0.23.5.2")
    implementation("cn.enaium.treesitter:treesitter-languages-python-kmp-android:0.25.0.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
