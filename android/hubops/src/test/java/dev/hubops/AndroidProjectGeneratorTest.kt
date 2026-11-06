package dev.hubops

import dev.hubops.despertar.project.AndroidProjectBlueprint
import dev.hubops.despertar.project.AndroidProjectGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class AndroidProjectGeneratorTest {
    @Test fun generatesCompleteComposeProjectArchive() {
        val dir = createTempDir(prefix = "hubops-forge-")
        try {
            val output = File(dir, "starter.zip")
            AndroidProjectGenerator.generate(
                output,
                AndroidProjectBlueprint("Minha App", "com.exemplo.minhaapp", includeInternetPermission = true),
            )
            assertTrue(output.isFile && output.length() > 0)
            ZipFile(output).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(names.contains("minha-app/settings.gradle.kts"))
                assertTrue(names.contains("minha-app/app/build.gradle.kts"))
                assertTrue(names.contains("minha-app/app/src/main/AndroidManifest.xml"))
                assertTrue(names.contains("minha-app/app/src/main/java/com/exemplo/minhaapp/MainActivity.kt"))
                val manifest = zip.getInputStream(zip.getEntry("minha-app/app/src/main/AndroidManifest.xml"))
                    .bufferedReader().readText()
                assertTrue(manifest.contains("android.permission.INTERNET"))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsInvalidPackageBeforeCreatingArchive() {
        val dir = createTempDir(prefix = "hubops-forge-invalid-")
        try {
            val output = File(dir, "invalid.zip")
            try {
                AndroidProjectGenerator.generate(output, AndroidProjectBlueprint("Projeto", "com..invalido"))
                fail("Esperava validação de package")
            } catch (_: IllegalArgumentException) { }
            assertFalse(output.exists())
        } finally { dir.deleteRecursively() }
    }
}
