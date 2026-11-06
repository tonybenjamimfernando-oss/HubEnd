package dev.hubops.despertar

import dev.hubops.core.config.SecureConfig
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

/** Comando já validado, pronto para ser enviado ao Termux. */
data class TermuxJob(val executable: String, val args: List<String>, val workDir: String, val label: String)

data class TermuxRunResult(
    val executionId: Int,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val internalError: String,
    val internalErrorCode: Int,
    val completedAt: Long,
) {
    val isPending: Boolean get() = completedAt == 0L
    val succeeded: Boolean get() = !isPending && exitCode == 0 && internalErrorCode == -1 && internalError.isBlank()
}

object TermuxJobs {
    private const val PREFIX = "/data/data/com.termux/files/usr/bin/"
    private const val HOME = "/data/data/com.termux/files/home"
    private val APK = Regex("^/storage/emulated/0/[A-Za-z0-9._ /-]{1,220}\\.apk$")
    private val NAME = Regex("^[A-Za-z0-9._-]{1,60}$")

    /** Ferramentas permitidas (allowlist). Só para APKs próprios/autorizados. */
    val TOOLS = setOf("jadx", "apktool")

    /** Diagnóstico sem alterações: versões, permissões, armazenamento e ferramentas disponíveis. */
    fun environmentCheck(): TermuxJob {
        val script = """set +e
printf '%s\n' 'HUBOPS_TERMUX_DIAGNOSTICS_BEGIN'
printf 'Termux: '; printf '%s\n' "${'$'}PREFIX"
printf 'Android: '; getprop ro.build.version.release 2>/dev/null || true
for tool in java gradle unzip zipinfo sha256sum jadx apktool aapt; do
  if command -v "${'$'}tool" >/dev/null 2>&1; then printf '%s: %s\n' "${'$'}tool" "$(command -v "${'$'}tool")"; else printf '%s: AUSENTE\n' "${'$'}tool"; fi
done
printf 'Java: '; java -version 2>&1 | head -n 1
printf 'Gradle: '; if command -v gradle >/dev/null 2>&1; then gradle --version 2>/dev/null | awk '/^Gradle / {print $2; exit}'; else echo AUSENTE; fi
SDK_DIR="${'$'}{ANDROID_HOME:-${'$'}{ANDROID_SDK_ROOT:-}}"
printf 'Android SDK: %s\n' "${'$'}{SDK_DIR:-NAO_CONFIGURADO}"
if [ -n "${'$'}SDK_DIR" ] && test -d "${'$'}SDK_DIR/platforms/android-34" && test -d "${'$'}SDK_DIR/build-tools/34.0.0"; then echo 'Android SDK 34: DISPONIVEL'; else echo 'Android SDK 34: INCOMPLETO_OU_AUSENTE'; fi
printf 'Armazenamento: '; if test -d /storage/emulated/0/Download/HubOps && test -r /storage/emulated/0/Download/HubOps; then echo ACESSIVEL; else echo INDISPONIVEL; fi
printf 'RUN_COMMAND externo: '; if grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true' "${'$'}HOME/.termux/termux.properties" 2>/dev/null; then echo ATIVADO; else echo NAO_CONFIRMADO; fi
printf 'Diretório de trabalho: '; if test -d "${'$'}HOME/despertar" && test -w "${'$'}HOME/despertar"; then echo GRAVAVEL; else echo AUSENTE_OU_SEM_PERMISSAO; fi
printf 'Espaço livre: '; df -h "${'$'}HOME" 2>/dev/null | tail -n 1
printf '%s\n' 'HUBOPS_TERMUX_DIAGNOSTICS_END'
""".trimIndent()
        return TermuxJob(PREFIX + "bash", listOf("-lc", script), HOME, "Diagnóstico do ambiente Termux")
    }

    /** Resume artefactos da análise sem abrir/alterar os ficheiros produzidos. */
    fun inspectOutput(outName: String): TermuxJob {
        require(NAME.matches(outName) && outName !in setOf(".", "..")) { "Nome de saída inválido." }
        val out = "$HOME/despertar/$outName"
        val script = """set -eu
OUT='$out'
if [ ! -d "${'$'}OUT" ]; then echo 'Pasta de análise não encontrada.' >&2; exit 20; fi
printf 'Diretório: %s\n' "${'$'}OUT"
printf 'Ficheiros: '; find "${'$'}OUT" -type f | wc -l
printf 'Pastas: '; find "${'$'}OUT" -type d | wc -l
printf 'Tamanho: '; du -sh "${'$'}OUT" | cut -f1
printf '%s\n' '--- Primeiros 80 ficheiros (caminho e bytes) ---'
find "${'$'}OUT" -type f -printf '%s\t%p\n' 2>/dev/null | sort -nr | head -n 80
""".trimIndent()
        return TermuxJob(PREFIX + "bash", listOf("-lc", script), HOME, "Inspecionar resultados · $outName")
    }

    /** Compila um projeto gerado pelo HubOps dentro do Termux e publica o APK debug em Downloads. */
    fun buildAndroidProject(zipPath: String, projectKey: String, expectedSha256: String): TermuxJob {
        val zipPattern = Regex("^/storage/emulated/0/Download/HubOps/Projects/[A-Za-z0-9._-]{3,60}\\.zip$")
        require(zipPattern.matches(zipPath) && !zipPath.contains("..")) {
            "O projeto deve estar em Downloads/HubOps/Projects e ter um nome válido."
        }
        require(NAME.matches(projectKey) && projectKey !in setOf(".", "..")) {
            "Identificador de compilação inválido."
        }
        require(expectedSha256.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 esperado inválido." }
        val script = """set -eu
ZIP='$zipPath'
KEY='$projectKey'
EXPECTED_SHA256='$expectedSha256'
HOME_DIR="${'$'}HOME"
WORK_ROOT="${'$'}HOME_DIR/despertar/builds"
WORK_DIR="${'$'}WORK_ROOT/${'$'}KEY"
DEST_DIR='/storage/emulated/0/Download/HubOps/Builds'
DEST="${'$'}DEST_DIR/${'$'}KEY-debug.apk"
REPORT="${'$'}DEST_DIR/${'$'}KEY-build-report.txt"
command -v unzip >/dev/null 2>&1 && command -v zipinfo >/dev/null 2>&1 && command -v sha256sum >/dev/null 2>&1 || { echo 'FALTA: instale unzip no Termux (pkg install unzip).' >&2; exit 30; }
command -v gradle >/dev/null 2>&1 || { echo 'FALTA: instale Gradle 8.11.1–8.14.3 no Termux.' >&2; exit 31; }
command -v java >/dev/null 2>&1 || { echo 'FALTA: instale JDK 17 no Termux.' >&2; exit 32; }
[ -r "${'$'}ZIP" ] || { echo 'ZIP não acessível. Execute termux-setup-storage e conceda acesso.' >&2; exit 33; }
ACTUAL_SHA256=${'$'}(sha256sum "${'$'}ZIP" | awk '{print ${'$'}1}')
[ "${'$'}ACTUAL_SHA256" = "${'$'}EXPECTED_SHA256" ] || { echo 'SHA-256 do ZIP não corresponde ao ficheiro exportado; análise cancelada.' >&2; exit 45; }
SDK_DIR="${'$'}{ANDROID_HOME:-${'$'}{ANDROID_SDK_ROOT:-}}"
[ -n "${'$'}SDK_DIR" ] && [ -d "${'$'}SDK_DIR/platforms/android-34" ] && [ -x "${'$'}SDK_DIR/build-tools/34.0.0/aapt" ] && [ -x "${'$'}SDK_DIR/build-tools/34.0.0/apksigner" ] || { echo 'Android SDK incompleto: configure ANDROID_HOME com Platform 34, aapt e apksigner em Build Tools 34.0.0.' >&2; exit 42; }
[ ! -e "${'$'}WORK_DIR" ] || { echo 'A pasta de trabalho já existe; escolha outro projeto ou remova-a manualmente.' >&2; exit 34; }
[ ! -e "${'$'}DEST" ] || { echo 'O APK de destino já existe; não será sobrescrito.' >&2; exit 35; }
[ ! -e "${'$'}REPORT" ] || { echo 'O relatório de destino já existe; não será sobrescrito.' >&2; exit 35; }
GRADLE_VERSION=${'$'}(gradle --version | awk '/^Gradle / {print ${'$'}2; exit}')
case "${'$'}GRADLE_VERSION" in 8.11.1|8.12|8.13|8.14|8.14.[0-3]) ;; *) echo "Gradle incompatível: ${'$'}GRADLE_VERSION. Este template requer Gradle 8.11.1–8.14.3 e JDK 17." >&2; exit 36 ;; esac
java -version 2>&1 | grep -q '"17' || { echo 'JDK 17 é obrigatório para este template.' >&2; exit 37; }
# Rejeita arquivos ZIP excessivos antes da extração.
unzip -l "${'$'}ZIP" | awk '${'$'}1 ~ /^[0-9]+${'$'}/ {total += ${'$'}1; count++} END{if(count>500 || total>157286400) exit 1}' || { echo 'ZIP excede 500 entradas ou 150 MiB descomprimidos.' >&2; exit 43; }
# Rejeita caminhos absolutos, separadores Windows e segmentos de traversal.
zipinfo -l "${'$'}ZIP" | awk 'NR>2 && substr(${ '$' }1,1,1)=="l" {bad=1} END{exit bad}' || { echo 'ZIP com ligações simbólicas não é aceite.' >&2; exit 44; }
unzip -Z1 "${'$'}ZIP" | awk 'BEGIN{bad=0} /^\// || index(${ '$' }0,"\\")>0 {bad=1} {n=split(${ '$' }0,a,"/"); for(i=1;i<=n;i++) if(a[i]=="..") bad=1} END{exit bad}' || { echo 'ZIP contém caminhos inseguros.' >&2; exit 38; }
ROOTS=${'$'}(unzip -Z1 "${'$'}ZIP" | awk -F/ 'NF>1 {print ${'$'}1}' | sort -u)
ROOT_COUNT=${'$'}(printf '%s\n' "${'$'}ROOTS" | wc -l | tr -d ' ')
[ -n "${'$'}ROOTS" ] && [ "${'$'}ROOT_COUNT" -eq 1 ] || { echo 'ZIP deve conter uma única pasta raiz.' >&2; exit 39; }
mkdir -p "${'$'}WORK_DIR" "${'$'}DEST_DIR"
unzip -q "${'$'}ZIP" -d "${'$'}WORK_DIR"
PROJECT_DIR="${'$'}WORK_DIR/${'$'}ROOTS"
[ -f "${'$'}PROJECT_DIR/settings.gradle.kts" ] && [ -f "${'$'}PROJECT_DIR/app/build.gradle.kts" ] || { echo 'Estrutura Android/Gradle não reconhecida.' >&2; exit 40; }
echo "Projeto: ${'$'}PROJECT_DIR"
echo "Gradle: ${'$'}GRADLE_VERSION · Java 17"
export GRADLE_OPTS='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
if gradle -p "${'$'}PROJECT_DIR" --no-daemon --max-workers=2 --stacktrace :app:assembleDebug; then
  :
else
  GRADLE_EXIT=${'$'}?
  printf 'BUILD_FAILED\nGRADLE_EXIT=%s\n' "${'$'}GRADLE_EXIT"
  exit "${'$'}GRADLE_EXIT"
fi
APK="${'$'}PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[ -s "${'$'}APK" ] || { echo 'Gradle terminou sem produzir o APK esperado.' >&2; exit 41; }
# Verifica a integridade ZIP e a estrutura mínima antes de publicar o artefacto.
unzip -tq "${'$'}APK" >/dev/null || { echo 'APK inválido: teste de integridade ZIP falhou.' >&2; exit 47; }
unzip -Z1 "${'$'}APK" | grep -qx 'AndroidManifest.xml' || { echo 'APK inválido: manifesto Android ausente.' >&2; exit 47; }
unzip -Z1 "${'$'}APK" | grep -Eq '^classes([0-9]+)?\.dex${'$'}' || { echo 'APK inválido: nenhum ficheiro DEX encontrado.' >&2; exit 47; }
EXPECTED_PACKAGE=${'$'}(sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\([^" ]*\)".*/\1/p' "${'$'}PROJECT_DIR/app/build.gradle.kts" | head -n 1)
ACTUAL_PACKAGE=${'$'}("${'$'}SDK_DIR/build-tools/34.0.0/aapt" dump badging "${'$'}APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)
[ -n "${'$'}EXPECTED_PACKAGE" ] && [ "${'$'}ACTUAL_PACKAGE" = "${'$'}EXPECTED_PACKAGE" ] || { echo "Package do APK não corresponde ao projeto (esperado: ${'$'}EXPECTED_PACKAGE; obtido: ${'$'}ACTUAL_PACKAGE)." >&2; exit 46; }
SIGNATURE_REPORT=${'$'}("${'$'}SDK_DIR/build-tools/34.0.0/apksigner" verify --verbose --print-certs "${'$'}APK" 2>&1) || { printf '%s\n' "${'$'}SIGNATURE_REPORT" >&2; echo 'A verificação criptográfica da assinatura APK falhou.' >&2; exit 48; }
cp "${'$'}APK" "${'$'}DEST"
APK_BYTES=${'$'}(wc -c < "${'$'}DEST" | tr -d ' ')
APK_SHA256=${'$'}(sha256sum "${'$'}DEST" | awk '{print ${'$'}1}')
{
  printf 'HubOps Android Build Report\n'
  printf 'Project: %s\nPackage: %s\nGradle: %s\nJava: 17\n' "${'$'}KEY" "${'$'}ACTUAL_PACKAGE" "${'$'}GRADLE_VERSION"
  printf 'Built UTC: %s\nAPK: %s\nBytes: %s\nSHA-256: %s\n' "${'$'}(date -u +%Y-%m-%dT%H:%M:%SZ)" "${'$'}DEST" "${'$'}APK_BYTES" "${'$'}APK_SHA256"
  printf '\nSignature verification:\n%s\n' "${'$'}SIGNATURE_REPORT"
} > "${'$'}REPORT"
printf 'BUILD_OK\nAPK=%s\nBYTES=%s\nSHA256=%s\nPACKAGE=%s\nREPORT=%s\n' "${'$'}DEST" "${'$'}APK_BYTES" "${'$'}APK_SHA256" "${'$'}ACTUAL_PACKAGE" "${'$'}REPORT"
""".trimIndent()
        return TermuxJob(PREFIX + "bash", listOf("-lc", script), HOME, "Compilar projeto Android · $projectKey")
    }

    fun build(tool: String, apkPath: String, outName: String): TermuxJob {
        require(tool in TOOLS) { "Ferramenta não permitida." }
        require(APK.matches(apkPath) && !apkPath.contains("..")) {
            "Indique um APK importado para /storage/emulated/0/ (sem '..')."
        }
        require(NAME.matches(outName) && outName !in setOf(".", "..")) { "Nome de saída inválido." }
        val out = "$HOME/despertar/$outName"
        val toolArgs = when (tool) {
            "jadx" -> "-d '$out' '$apkPath'"
            else -> "d -o '$out' '$apkPath'"
        }
        // Os valores interpolados são validados por allowlist; aspas simples protegem espaços.
        val script = "mkdir -p '$HOME/despertar' && " +
            "if [ -e '$out' ]; then echo 'A pasta de saída já existe; escolha outro nome.' >&2; exit 17; fi && " +
            "exec '$PREFIX$tool' $toolArgs"
        val args = listOf("-lc", script)
        return TermuxJob(PREFIX + "bash", args, HOME, "$tool · $outName")
    }
}

/** Integração oficial RUN_COMMAND; recolhe exit code, stdout e stderr do Termux. */
class TermuxBridge(private val context: Context) {

    fun run(job: TermuxJob): Int {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo("com.termux", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("com.termux", 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            error("Termux não está instalado. Instale o Termux e configure o acesso RUN_COMMAND.")
        }

        val executionId = nextExecutionId()
        val callback = Intent(context, TermuxResultService::class.java)
            .putExtra(TermuxResultService.EXTRA_EXECUTION_ID, executionId)
        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val resultPendingIntent = PendingIntent.getService(context, executionId, callback, pendingFlags)

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", job.executable)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", job.args.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", job.workDir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", job.label)
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "Análise local de APK autorizada pelo utilizador através de ${job.label}.")
        }
        context.getSharedPreferences(TermuxResultService.PREFS, Context.MODE_PRIVATE).edit()
            .putInt("execution_id", executionId)
            .putInt("exit_code", -2)
            .putInt("internal_error_code", -2)
            .putString("stdout", "")
            .putString("stderr", "")
            .putString("internal_error", "")
            .putLong("completed_at", 0L)
            .apply()
        try {
            context.startService(intent)
        } catch (e: Exception) {
            context.getSharedPreferences(TermuxResultService.PREFS, Context.MODE_PRIVATE).edit()
                .putInt("exit_code", -1)
                .putInt("internal_error_code", 1)
                .putString("internal_error", e.message ?: "Não foi possível iniciar o serviço do Termux.")
                .putLong("completed_at", System.currentTimeMillis())
                .apply()
            throw e
        }
        return executionId
    }

    private fun nextExecutionId(): Int = counter.incrementAndGet()

    companion object {
        private val counter = AtomicInteger((System.currentTimeMillis() % 1_000_000).toInt())
    }
}

/** Serviço interno que recebe o callback do Termux e persiste o último resultado. */
class TermuxResultService : android.app.IntentService("HubOps-Termux-Result") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("execution_id", -1) != executionId) return
        prefs.edit()
            .putInt("execution_id", executionId)
            .putInt("exit_code", bundle?.getInt("exitCode", -1) ?: -1)
            .putInt("internal_error_code", bundle?.getInt("err", -1) ?: -1)
            .putString("stdout", bundle?.getString("stdout", "").orEmpty().take(MAX_CAPTURE))
            .putString("stderr", bundle?.getString("stderr", "").orEmpty().take(MAX_CAPTURE))
            .putString("internal_error", bundle?.getString("errmsg", "") ?: if (bundle == null) "O Termux não devolveu o pacote de resultado." else "")
            .putLong("completed_at", System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "hubops.execution_id"
        const val EXTRA_RESULT_BUNDLE = "result"
        const val PREFS = "termux_job_result"
        const val MAX_CAPTURE = 12_000

        fun read(context: Context): TermuxRunResult? {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!p.contains("execution_id")) return null
            return TermuxRunResult(
                executionId = p.getInt("execution_id", -1),
                exitCode = p.getInt("exit_code", -1),
                stdout = p.getString("stdout", "").orEmpty(),
                stderr = p.getString("stderr", "").orEmpty(),
                internalError = p.getString("internal_error", "").orEmpty(),
                internalErrorCode = p.getInt("internal_error_code", -1),
                completedAt = p.getLong("completed_at", 0L),
            )
        }
    }
}

/** Recurso pago. Requer produto e validação de compra no Google Play Billing. */
interface Entitlement { fun isUnlocked(): Boolean }

/** Interruptor local exclusivamente para desenvolvimento/testes; não protege produção. */
class DevEntitlement(private val config: SecureConfig) : Entitlement {
    override fun isUnlocked(): Boolean = config.devUnlocked
}
