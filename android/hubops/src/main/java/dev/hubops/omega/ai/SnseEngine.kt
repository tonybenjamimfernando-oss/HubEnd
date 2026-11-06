package dev.hubops.omega.ai

import com.hubops.vislumbrops.core.Finding
import com.hubops.vislumbrops.core.Severity
import java.io.File
import java.util.Locale

/**
 * SNSE: deterministic routing + optional, replaceable local inference runtime.
 * No model weights or native llama runtime are bundled by this module. When no
 * compatible runtime/model is installed, the engine reports SYMBOLIC_ONLY.
 */
enum class ShardId { SQLI, CRYPTO, AUTH, NETWORK, STORAGE, LOGGING, DESERIALIZATION }

data class ShardRoute(val shardId: ShardId, val confidence: Double, val matchedRules: Int, val reason: String)
data class CweRoutingEntry(val id: String, val title: String, val shardId: ShardId)

/** Small offline routing index, not a complete MITRE CWE catalogue. */
object CweRoutingCatalog {
    private val entries = listOf(
        CweRoutingEntry("CWE-89", "SQL Injection", ShardId.SQLI),
        CweRoutingEntry("CWE-327", "Use of a Broken or Risky Cryptographic Algorithm", ShardId.CRYPTO),
        CweRoutingEntry("CWE-328", "Use of Weak Hash", ShardId.CRYPTO),
        CweRoutingEntry("CWE-330", "Insufficiently Random Values", ShardId.CRYPTO),
        CweRoutingEntry("CWE-287", "Improper Authentication", ShardId.AUTH),
        CweRoutingEntry("CWE-306", "Missing Authentication", ShardId.AUTH),
        CweRoutingEntry("CWE-862", "Missing Authorization", ShardId.AUTH),
        CweRoutingEntry("CWE-295", "Improper Certificate Validation", ShardId.NETWORK),
        CweRoutingEntry("CWE-297", "Improper Validation of Certificate Hostname", ShardId.NETWORK),
        CweRoutingEntry("CWE-312", "Cleartext Storage of Sensitive Information", ShardId.STORAGE),
        CweRoutingEntry("CWE-732", "Incorrect Permission Assignment", ShardId.STORAGE),
        CweRoutingEntry("CWE-532", "Insertion of Sensitive Information into Log File", ShardId.LOGGING),
        CweRoutingEntry("CWE-502", "Deserialization of Untrusted Data", ShardId.DESERIALIZATION),
        CweRoutingEntry("CWE-611", "Improper Restriction of XML External Entity Reference", ShardId.DESERIALIZATION),
    ).associateBy { it.id }
    fun find(id: String?): CweRoutingEntry? = id?.uppercase(Locale.ROOT)?.let(entries::get)
    fun all(): List<CweRoutingEntry> = entries.values.sortedBy { it.id }
}
data class ShardModel(val shardId: ShardId, val file: File, val parameterBillions: Double?, val quantization: String, val estimatedRamMb: Long)
data class ShardOutput(val patch: String, val confidence: Double, val rationale: String, val sourceShard: ShardId? = null)
data class ShardExecution(val shardId: ShardId, val output: ShardOutput?, val error: String? = null)
data class Arbitration(val accepted: Boolean, val winner: ShardOutput?, val reason: String, val requiresExternalValidation: Boolean = true)
data class SnseResult(
    val mode: String,
    val routes: List<ShardRoute>,
    val executions: List<ShardExecution>,
    val finalPatch: String?,
    val acceptedForReview: Boolean,
    val arbitrationReason: String,
    val limitations: List<String>,
)

interface ShardInferenceRuntime {
    /** Implementations must release/unmap a model before returning. */
    suspend fun generate(model: ShardModel, prompt: String, maxTokens: Int, temperature: Float): Result<String>
}

object NoShardInferenceRuntime : ShardInferenceRuntime {
    override suspend fun generate(model: ShardModel, prompt: String, maxTokens: Int, temperature: Float) =
        Result.failure<String>(IllegalStateException("Runtime de inferência GGUF não instalado."))
}

class ShardModelManager(private val modelDirectory: File, @Volatile private var runtime: ShardInferenceRuntime) {
    fun attachRuntime(runtime: ShardInferenceRuntime) { this.runtime = runtime }
    fun available(shardId: ShardId, ramBudgetMb: Long): ShardModel? {
        if (ramBudgetMb < 256 || !modelDirectory.isDirectory || java.nio.file.Files.isSymbolicLink(modelDirectory.toPath())) return null
        val prefix = shardId.name.lowercase(Locale.ROOT)
        val root = modelDirectory.canonicalFile
        val candidates = modelDirectory.listFiles { file -> file.isFile && file.canRead() &&
            !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.canonicalPath.startsWith(root.path + File.separator) &&
            file.extension.equals("gguf", true) &&
            (file.nameWithoutExtension == prefix || file.nameWithoutExtension.startsWith("$prefix-")) }.orEmpty()
        return candidates.mapNotNull { file ->
            if (file.length() < 1) return@mapNotNull null
            // Weight bytes are only a rough lower bound; reserve 256 MB for runtime/context.
            val estimate = ((file.length() * 1.45) / (1024 * 1024)).toLong() + 256
            if (estimate > ramBudgetMb) return@mapNotNull null
            val declaredOneB = Regex("(?i)(^|[-_])1b([-_]|$)").containsMatchIn(file.nameWithoutExtension)
            val declaredQ4 = Regex("(?i)(^|[-_])q4([-_]|$)").containsMatchIn(file.nameWithoutExtension)
            ShardModel(shardId, file, if (declaredOneB) 1.0 else null,
                if (declaredQ4) "Q4 (nome do ficheiro; não verificado)" else "Quantização não verificada", estimate)
        }.minByOrNull { it.estimatedRamMb }
    }

    suspend fun infer(model: ShardModel, prompt: String): Result<String> =
        runtime.generate(model, prompt, maxTokens = 512, temperature = 0.1f)
}

class SymbolicShardRouter {
    private data class Rule(val id: String, val regex: Regex, val weight: Double)
    private val rules: Map<ShardId, List<Rule>> = mapOf(
        ShardId.SQLI to listOf(
            Rule("raw-query", Regex("\\b(rawQuery|execSQL|createStatement|prepareStatement)\\b", RegexOption.IGNORE_CASE), 0.65),
            Rule("sql-concat", Regex("(?is)(SELECT|UPDATE|DELETE|INSERT).{0,180}(\\+|\\$\\{)"), 0.35)),
        ShardId.CRYPTO to listOf(
            Rule("crypto-api", Regex("\\b(Cipher\\.getInstance|MessageDigest|SecretKeySpec|Mac\\.getInstance)\\b"), 0.35),
            Rule("weak-primitive", Regex("\\b(DES|MD5|SHA-?1|RC4)\\b", RegexOption.IGNORE_CASE), 0.65)),
        ShardId.AUTH to listOf(
            Rule("auth-api", Regex("\\b(checkPermission|requestPermissions|grantUriPermission|authenticate)\\b"), 0.45),
            Rule("webview-bridge", Regex("\\b(addJavascriptInterface|setAllowFileAccess)\\b"), 0.55)),
        ShardId.NETWORK to listOf(
            Rule("tls-api", Regex("\\b(TrustManager|HostnameVerifier|X509TrustManager)\\b"), 0.5),
            Rule("hostname-verifier", Regex("setDefaultHostnameVerifier|checkServerTrusted"), 0.5)),
        ShardId.STORAGE to listOf(
            Rule("world-readable", Regex("MODE_WORLD_(READABLE|WRITEABLE)"), 0.7),
            Rule("external-storage", Regex("getExternalStorage|openFileOutput|MODE_PRIVATE"), 0.3)),
        ShardId.LOGGING to listOf(
            Rule("sensitive-log", Regex("(?is)Log\\.(d|v|i|w|e)\\s*\\([^)]{0,300}(password|token|secret|api[_-]?key)"), 1.0)),
        ShardId.DESERIALIZATION to listOf(
            Rule("native-serialization", Regex("\\b(ObjectInputStream|readObject)\\b"), 0.65),
            Rule("json-deserialization", Regex("\\b(Gson\\.fromJson|readValue)\\b"), 0.35)),
    )

    fun route(code: String, finding: Finding, maxShards: Int = 2): List<ShardRoute> {
        require(maxShards in 1..2) { "O SNSE limita a execução a um ou dois shards por análise." }
        val text = buildString {
            append(finding.title).append('\n').append(finding.description).append('\n')
            append(finding.cweId.orEmpty()).append('\n').append(code)
        }
        val scored = rules.map { (id, shardRules) ->
            val matched = shardRules.filter { it.regex.containsMatchIn(text) }
            val confidence = matched.sumOf { it.weight }.coerceIn(0.0, 1.0)
            ShardRoute(id, confidence, matched.size,
                if (matched.isEmpty()) "Sem regra simbólica correspondente" else matched.joinToString(",") { it.id })
        }.sortedWith(compareByDescending<ShardRoute> { it.confidence }.thenBy { it.shardId.name })

        val cwePreferred = CweRoutingCatalog.find(finding.cweId)?.shardId
        val ordered = if (cwePreferred == null) scored else scored.sortedWith(
            compareByDescending<ShardRoute> { it.shardId == cwePreferred }.thenByDescending { it.confidence }
        )
        // No symbolic signal means heuristic/manual fallback. Below 0.70, route to
        // two specialists (including the second-ranked candidate); otherwise one.
        val best = ordered.firstOrNull() ?: return emptyList()
        if (best.confidence <= 0.0) return emptyList()
        return if (best.confidence < 0.70) ordered.take(maxShards) else listOf(best)
    }
}

class SymbolicPatchArbiter {
    private val forbidden = listOf(
        Regex("Runtime\\.getRuntime\\(\\)\\.exec\\s*\\("), Regex("\\bProcessBuilder\\s*\\("),
        Regex("\\bDexClassLoader\\b"), Regex("\\b(InsecureTrustManager|TrustAllCerts)\\b"),
        Regex("\\beval\\s*\\("),
        // HubEnd: "}" escapado — o motor de regex ICU do Android rejeita "}" literal sem escape
        // (a JVM aceita), o que fazia a aplicação fechar no arranque.
        Regex("checkServerTrusted\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
    )

    fun arbitrate(outputs: List<ShardOutput>, originalCode: String, finding: Finding? = null): Arbitration {
        if (outputs.isEmpty()) return Arbitration(false, null, "Nenhum shard gerou uma proposta; manter análise simbólica.")
        val safe = outputs.filter { out ->
            out.patch.isNotBlank() && out.patch.length <= 1_000_000 &&
                out.confidence.isFinite() && out.confidence in 0.0..1.0 &&
                out.patch.trim() != originalCode.trim() &&
                forbidden.none { it.containsMatchIn(out.patch) } &&
                !introducesWeakCrypto(out.patch, originalCode) &&
                !introducesDangerousSurface(out.patch, originalCode)
        }
        if (safe.isEmpty()) return Arbitration(false, null,
            "Todas as propostas foram rejeitadas por formato, confiança, ausência de alteração ou regra de segurança.")

        val severityWeight = when (finding?.severity) {
            Severity.CRITICAL -> 1.25
            Severity.HIGH -> 1.15
            Severity.MEDIUM -> 1.0
            Severity.LOW -> 0.85
            Severity.INFO -> 0.70
            null -> 1.0
        }
        val minimumScore = when (finding?.severity) {
            Severity.CRITICAL -> 0.90
            Severity.HIGH -> 0.82
            Severity.MEDIUM -> 0.70
            Severity.LOW -> 0.55
            Severity.INFO, null -> 0.40
        }
        val preferredShard = CweRoutingCatalog.find(finding?.cweId)?.shardId
        val ranked = safe.map { output ->
            val cweAlignment = if (preferredShard != null && output.sourceShard == preferredShard) 0.05 else 0.0
            val weightedScore = (output.confidence * severityWeight + cweAlignment).coerceAtMost(1.0)
            output to weightedScore
        }.filter { it.second >= minimumScore }
        if (ranked.isEmpty()) return Arbitration(false, null,
            "Nenhuma proposta atingiu o limiar de confiança ponderado pela severidade/CWE ($minimumScore).")
        val winner = ranked.maxWithOrNull(compareBy<Pair<ShardOutput, Double>> { it.second }
            .thenBy { -kotlin.math.abs(it.first.patch.length - originalCode.length) })!!.first
        return Arbitration(true, winner,
            "Proposta elegível para revisão humana; score ponderado por severidade/CWE. Exige diff, compilação, testes e nova auditoria.")
    }

    private fun introducesWeakCrypto(patch: String, original: String): Boolean {
        val weak = Regex("(?i)\\b(MD5|SHA-?1|DES|RC4)\\b")
        return weak.findAll(patch).count() > weak.findAll(original).count()
    }

    private fun introducesDangerousSurface(patch: String, original: String): Boolean {
        val added = listOf("setJavaScriptEnabled(true)", "setAllowFileAccess(true)", "MODE_WORLD_READABLE", "MODE_WORLD_WRITEABLE")
        return added.any { token -> token in patch && token !in original }
    }
}

class SnseEngine(
    modelDirectory: File,
    private val runtime: ShardInferenceRuntime = NoShardInferenceRuntime,
    private val router: SymbolicShardRouter = SymbolicShardRouter(),
    private val arbiter: SymbolicPatchArbiter = SymbolicPatchArbiter(),
    private val ramBudgetMb: () -> Long = { 0L },
) {
    private val models = ShardModelManager(modelDirectory, runtime)

    /** Runtime may be attached only after native binary and release signature validation. */
    fun attachRuntime(runtime: ShardInferenceRuntime) = models.attachRuntime(runtime)

    suspend fun generate(finding: Finding, code: String): SnseResult {
        require(code.isNotBlank() && code.length <= 1_000_000) { "Código vazio ou superior a 1 MB." }
        val routes = router.route(code, finding)
        if (routes.isEmpty()) return SnseResult("SYMBOLIC_ONLY", emptyList(), emptyList(), null, false,
            "Nenhuma regra de roteamento disparou; não inventar uma correção.",
            listOf("Nenhum modelo foi executado.", "A análise simbólica não substitui AST/compilador."))

        val executions = mutableListOf<ShardExecution>()
        val outputs = mutableListOf<ShardOutput>()
        // Sequential inference prevents simultaneous loading of two large shards.
        for (route in routes) {
            val model = models.available(route.shardId, ramBudgetMb())
            if (model == null) {
                executions += ShardExecution(route.shardId, null, "Modelo GGUF ausente.")
                continue
            }
            val prompt = buildString {
                append("### Task\nRepair the vulnerable source while preserving unrelated behavior.\n")
                append("Specialist: ").append(route.shardId.name.lowercase(Locale.ROOT)).append("\n")
                append("CWE: ").append(finding.cweId ?: "unclassified").append("\n")
                append("Return only a JSON object with patch (complete corrected source), confidence (0..1), and rationale. Do not execute code.\n")
                append("### Vulnerable source\n").append(code)
                append("\n### Corrected source JSON:\n")
            }
            val raw = models.infer(model, prompt).getOrNull()
            val output = raw?.let(::parseShardOutput)
            if (output != null) outputs += output.copy(sourceShard = route.shardId)
            executions += ShardExecution(route.shardId, output,
                if (output == null) "Inferência indisponível ou resposta inválida." else null)
        }
        val arbitration = arbiter.arbitrate(outputs, code, finding)
        val modelUsed = executions.any { it.output != null }
        return SnseResult(if (modelUsed) "HYBRID_PROPOSAL" else "SYMBOLIC_ONLY", routes, executions,
            arbitration.winner?.patch, arbitration.accepted, arbitration.reason,
            listOf("O runtime llama.cpp só fica ativo depois de instalar o binário assinado e modelos assinados.",
                "A estimativa de RAM por tamanho do ficheiro é indicativa, não medição de pico.",
                "Arbitragem por padrões não prova ausência de vulnerabilidades.",
                "Uma proposta aceite nunca é aplicada automaticamente; exige ControlledRepairEngine e nova auditoria."))
    }

    private fun parseShardOutput(raw: String): ShardOutput? = runCatching {
        require(raw.length <= 2_000_000) { "Resposta do shard excede 2 MB." }
        val patch = readJsonString(raw, "patch") ?: error("Campo patch ausente.")
        val confidence = Regex("\\\"confidence\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
            .find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: error("Confidence inválida.")
        val rationale = readJsonString(raw, "rationale").orEmpty().take(2_000)
        require(patch.isNotBlank() && patch.length <= 1_000_000)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        ShardOutput(patch, confidence, rationale)
    }.getOrNull()

    /** Bounded JSON string-field reader supporting escapes; rejects malformed strings. */
    private fun readJsonString(json: String, field: String): String? {
        val key = Regex("\\\"${Regex.escape(field)}\\\"\\s*:").find(json) ?: return null
        var i = key.range.last + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '\"') return null
        i++
        val out = StringBuilder()
        while (i < json.length) {
            val ch = json[i++]
            when {
                ch == '\"' -> return out.toString()
                ch == '\\' -> {
                    if (i >= json.length) return null
                    when (json[i++]) {
                        '\"' -> out.append('\"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (i + 4 > json.length) return null
                            val code = json.substring(i, i + 4).toIntOrNull(16) ?: return null
                            out.append(code.toChar())
                            i += 4
                        }
                        else -> return null
                    }
                }
                ch.code < 0x20 -> return null
                else -> out.append(ch)
            }
            if (out.length > 1_000_000) return null
        }
        return null
    }
}
