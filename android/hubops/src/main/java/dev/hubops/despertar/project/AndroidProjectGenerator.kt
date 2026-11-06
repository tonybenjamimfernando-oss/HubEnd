package dev.hubops.despertar.project

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Blueprint validado para gerar um projeto Android independente e compilável no Android Studio. */
data class AndroidProjectBlueprint(
    val name: String,
    val packageName: String,
    val applicationId: String = packageName,
    val includeInternetPermission: Boolean = false,
    val template: ProjectTemplate = ProjectTemplate.BASIC,
    val startUrl: String = "",
) {
    /** O modelo Web precisa sempre de Internet. */
    val needsInternet: Boolean get() = includeInternetPermission || template == ProjectTemplate.WEB

    fun validate() {
        if (template == ProjectTemplate.WEB) {
            require(startUrl.matches(Regex("https://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]*)?"))) {
                "Endereço inválido. Use https://exemplo.com"
            }
        }
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9 _-]{1,39}"))) {
            "O nome deve começar por uma letra e ter 2–40 caracteres."
        }
        val packageRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){1,5}")
        require(packageRegex.matches(packageName)) { "Package inválido. Exemplo: com.exemplo.minhaapp" }
        require(packageRegex.matches(applicationId)) { "Application ID inválido." }
    }
}

/** Gera um ZIP determinístico, sem rede, sem executar código e sem copiar fontes de terceiros. */
object AndroidProjectGenerator {
    fun generate(destination: File, blueprint: AndroidProjectBlueprint): File {
        blueprint.validate()
        require(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true) {
            "Não foi possível preparar a pasta de exportação."
        }
        val root = safeSlug(blueprint.name)
        val files = templateFiles(blueprint)
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            files.toSortedMap().forEach { (path, content) ->
                val entry = ZipEntry("$root/$path")
                entry.time = 0L
                zip.putNextEntry(entry)
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        require(destination.isFile && destination.length() > 0L) { "O ZIP do projeto não foi criado corretamente." }
        return destination
    }

    private fun templateFiles(b: AndroidProjectBlueprint): Map<String, String> {
        val namespace = b.packageName
        val appId = b.applicationId
        val root = safeSlug(b.name)
        val permissions = if (b.needsInternet) "    <uses-permission android:name=\"android.permission.INTERNET\" />\n" else ""
        return linkedMapOf(
            "settings.gradle.kts" to """pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "${escapeGradle(b.name)}"
include(":app")
""",
            "build.gradle.kts" to """plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
""",
            "gradle.properties" to """org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.daemon=false
android.useAndroidX=true
kotlin.code.style=official
""",
            "app/build.gradle.kts" to """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "$namespace"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    defaultConfig {
        applicationId = "$appId"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}
""",
            "app/src/main/AndroidManifest.xml" to """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
$permissions    <application android:allowBackup="false" android:label="${xmlEscape(b.name)}" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""",
            "app/src/main/java/${namespace.replace('.', '/')}/MainActivity.kt" to ProjectTemplates.mainActivity(b),
            "README.md" to """# ${b.name}

Projeto Android nativo gerado pelo HubOps Despertar.

## Requisitos
- Android Studio (versão compatível com Android Gradle Plugin 8.10.1)
- JDK 17
- Android SDK 34

## Abrir e compilar
1. Extraia o ZIP.
2. Abra a pasta `$root` no Android Studio.
3. Aguarde a sincronização Gradle e instale os componentes do SDK solicitados.
4. Execute `:app:assembleDebug`.

### Compilar no Android com Termux
O HubOps pode exportar este ZIP para `Download/HubOps/Projects` e solicitar a compilação pelo Termux. Requer JDK 17, Gradle 8.11.1–8.14.3, unzip e acesso ao armazenamento partilhado. O APK debug será copiado para `Download/HubOps/Builds`.

Modelo: **${b.template.label}** — ${b.template.description}

### Compilar no GitHub (sem computador)
Envie esta pasta para um repositório GitHub. O ficheiro `.github/workflows/android.yml` corre os testes e gera o APK debug em cada envio (separador Actions → artefacto `apk-debug`).

O projeto usa Kotlin, Jetpack Compose e Material 3. Não contém credenciais nem código de terceiros.

Package: `$namespace`  
Application ID: `$appId`
""",
            "app/src/main/java/${namespace.replace('.', '/')}/ItemCodec.kt" to ProjectTemplates.itemCodec(b),
            "app/src/test/java/${namespace.replace('.', '/')}/ItemCodecTest.kt" to ProjectTemplates.itemCodecTest(b),
            ".github/workflows/android.yml" to ProjectTemplates.githubWorkflow(),
            ".gitignore" to ".gradle/\nbuild/\nlocal.properties\n*.iml\n.idea/\n.DS_Store\n",
        )
    }

    private fun safeSlug(value: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "android-project" }
    private fun escapeGradle(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun xmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
}
