package dev.hubops.despertar

/** Aceita um artefacto apenas quando o callback e o relatório do Builder provam validação concluída. */
object BuildArtifactParser {
    private val apkLine = Regex("(?m)^APK=(/storage/emulated/0/Download/HubOps/Builds/([A-Za-z0-9._-]{3,60})-debug\\.apk)$")
    private val reportLine = Regex("(?m)^REPORT=/storage/emulated/0/Download/HubOps/Builds/([A-Za-z0-9._-]{3,60})-build-report\\.txt$")
    private val shaLine = Regex("(?m)^SHA256=([a-f0-9]{64})$")
    private val packageLine = Regex("(?m)^PACKAGE=[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun parse(result: TermuxRunResult?): String? {
        if (result?.succeeded != true) return null
        val output = result.stdout
        if (!Regex("(?m)^BUILD_OK$").containsMatchIn(output)) return null
        val apk = apkLine.find(output) ?: return null
        val projectKey = apk.groupValues[2]
        if (reportLine.find(output)?.groupValues?.get(1) != projectKey) return null
        if (!shaLine.containsMatchIn(output) || !packageLine.containsMatchIn(output)) return null
        return apk.groupValues[1]
    }
}
