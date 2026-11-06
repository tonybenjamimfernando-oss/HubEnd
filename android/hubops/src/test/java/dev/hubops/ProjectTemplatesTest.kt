package dev.hubops

import dev.hubops.despertar.project.AndroidProjectBlueprint
import dev.hubops.despertar.project.AndroidProjectGenerator
import dev.hubops.despertar.project.ProjectTemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** HubEnd: cada modelo do Project Forge gera uma app completa. A CI compila estes ZIPs (HUBEND_FORGE_OUT). */
class ProjectTemplatesTest {
    private fun read(zip: ZipFile, path: String) = zip.getInputStream(zip.getEntry(path)).bufferedReader().readText()

    private fun generate(template: ProjectTemplate, url: String = ""): File {
        val out = System.getenv("HUBEND_FORGE_OUT")?.let(::File) ?: createTempDir(prefix = "forge-")
        out.mkdirs()
        val file = File(out, "${template.name.lowercase()}.zip")
        AndroidProjectGenerator.generate(file, AndroidProjectBlueprint("Teste App", "com.exemplo.teste", template = template, startUrl = url))
        return file
    }

    @Test fun notesTemplateSavesNotesOnDeviceAndShipsTestsAndCi() {
        ZipFile(generate(ProjectTemplate.NOTES)).use { zip ->
            val main = read(zip, "teste-app/app/src/main/java/com/exemplo/teste/MainActivity.kt")
            assertTrue(main.contains("getSharedPreferences"))
            assertTrue(main.contains("Guardar nota"))
            assertTrue(zip.getEntry("teste-app/app/src/test/java/com/exemplo/teste/ItemCodecTest.kt") != null)
            assertTrue(read(zip, "teste-app/.github/workflows/android.yml").contains(":app:assembleDebug"))
        }
    }

    @Test fun tasksTemplateCanMarkAndClearDoneTasks() {
        ZipFile(generate(ProjectTemplate.TASKS)).use { zip ->
            val main = read(zip, "teste-app/app/src/main/java/com/exemplo/teste/MainActivity.kt")
            assertTrue(main.contains("ItemCodec.toggle"))
            assertTrue(main.contains("Limpar concluídas"))
        }
    }

    @Test fun webTemplateAlwaysRequestsInternetAndLoadsTheGivenSite() {
        ZipFile(generate(ProjectTemplate.WEB, "https://exemplo.com/inicio")).use { zip ->
            assertTrue(read(zip, "teste-app/app/src/main/AndroidManifest.xml").contains("android.permission.INTERNET"))
            assertTrue(read(zip, "teste-app/app/src/main/java/com/exemplo/teste/MainActivity.kt").contains("\"https://exemplo.com/inicio\""))
        }
    }

    @Test fun basicTemplateHasNoInternetPermission() {
        ZipFile(generate(ProjectTemplate.BASIC)).use { zip ->
            assertFalse(read(zip, "teste-app/app/src/main/AndroidManifest.xml").contains("INTERNET"))
        }
    }

    @Test fun webTemplateRejectsInsecureOrMissingAddress() {
        for (bad in listOf("", "http://exemplo.com", "https://exem plo.com", "javascript:alert(1)")) {
            try {
                AndroidProjectBlueprint("Teste", "com.exemplo.teste", template = ProjectTemplate.WEB, startUrl = bad).validate()
                fail("Devia rejeitar: $bad")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
