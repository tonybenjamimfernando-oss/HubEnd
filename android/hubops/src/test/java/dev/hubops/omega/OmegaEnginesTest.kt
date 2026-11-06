package dev.hubops.omega

import com.hubops.vislumbrops.core.CheckCategory
import com.hubops.vislumbrops.core.Finding
import com.hubops.vislumbrops.core.Severity
import dev.hubops.omega.ai.ShardOutput
import dev.hubops.omega.ai.ShardModel
import dev.hubops.omega.ai.ShardInferenceRuntime
import dev.hubops.omega.ai.SnseEngine
import kotlinx.coroutines.runBlocking
import dev.hubops.omega.ai.ShardId
import dev.hubops.omega.ai.SymbolicPatchArbiter
import dev.hubops.omega.ai.SymbolicShardRouter
import dev.hubops.omega.containers.*
import dev.hubops.omega.merkle.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OmegaEnginesTest {
    private fun finding(cwe: String = "CWE-89") = Finding(
        category = CheckCategory.STATIC_ANALYSIS, severity = Severity.HIGH,
        title = "Consulta SQL dinâmica", description = "rawQuery concatena entrada não confiável", cweId = cwe
    )

    @Test fun snseRoutesToSqlInjectionShard() {
        val routes = SymbolicShardRouter().route(
            "db.rawQuery(\"SELECT * FROM users WHERE id=\" + userId, null)", finding())
        assertTrue(routes.any { it.shardId == ShardId.SQLI })
        assertTrue(routes.size <= 2)
    }


    @Test fun snseLowConfidenceRoutesToTwoCandidates() {
        val routes = SymbolicShardRouter().route("MessageDigest.getInstance(algorithm)", finding("CWE-327"))
        assertEquals(2, routes.size)
        assertTrue(routes.first().confidence < 0.70)
    }


    @Test fun snseUsesInstalledRuntimeSequentiallyAndParsesProposal() = runBlocking {
        val models = createTempDir(prefix = "snse-models-test")
        try {
            File(models, "sqli-1b-q4.gguf").writeBytes(ByteArray(1_024) { 1 })
            val runtime = object : ShardInferenceRuntime {
                override suspend fun generate(model: ShardModel, prompt: String, maxTokens: Int, temperature: Float) =
                    Result.success("""{"patch":"db.rawQuery(\"SELECT * FROM users WHERE id = ?\", arrayOf(id))","confidence":0.91,"rationale":"parameterized query"}""")
            }
            val engine = SnseEngine(models, runtime, ramBudgetMb = { 2_048 })
            val result = engine.generate(finding(), "db.rawQuery(\"SELECT * FROM users WHERE id=\" + id, null)")
            assertEquals("HYBRID_PROPOSAL", result.mode)
            assertTrue(result.acceptedForReview)
            assertTrue(result.finalPatch!!.contains("?"))
        } finally { models.deleteRecursively() }
    }

    @Test fun snseArbiterRejectsDangerousPatch() {
        val result = SymbolicPatchArbiter().arbitrate(
            listOf(ShardOutput("Runtime.getRuntime().exec(cmd)", 0.99, "unsafe")), "val x = 1")
        assertFalse(result.accepted)
    }



    @Test fun snseArbiterRaisesThresholdForHighSeverityFindings() {
        val result = SymbolicPatchArbiter().arbitrate(
            listOf(ShardOutput("db.rawQuery(\"SELECT * FROM users WHERE id = ?\", arrayOf(id))", 0.65, "candidate", ShardId.SQLI)),
            "db.rawQuery(\"SELECT * FROM users WHERE id=\" + id, null)", finding("CWE-89"))
        assertFalse(result.accepted)
    }

    @Test fun snseArbiterRejectsWeakCryptoIntroduced() {
        val result = SymbolicPatchArbiter().arbitrate(
            listOf(ShardOutput("val digest = MessageDigest.getInstance(\"MD5\")", 0.95, "weak digest")),
            "val digest = MessageDigest.getInstance(\"SHA-256\")")
        assertFalse(result.accepted)
    }

    @Test fun snseArbiterOnlyProducesReviewableProposal() {
        val result = SymbolicPatchArbiter().arbitrate(
            listOf(ShardOutput("val query = \"SELECT * FROM users WHERE id = ?\"", 0.91, "parameterized query")),
            "val query = \"SELECT * FROM users WHERE id=\" + id")
        assertTrue(result.accepted)
        assertTrue(result.requiresExternalValidation)
    }

    @Test fun nscNeverCallsJvmClassLoaderASandbox() {
        val root = createTempDir(prefix = "nsc-test")
        val nsc = NestedSandboxCascade(root) { SandboxCapabilities(1024, null, null, false) }
        val plan = nsc.plan(WorkloadProfile(requiresUnixBinary = false))
        assertEquals(IsolationLevel.JVM_STATIC_ANALYSIS, plan.level)
        assertTrue(plan.warnings.any { it.contains("ClassLoader") })
        root.deleteRecursively()
    }

    @Test fun nscRejectsUnixWorkloadWithoutRuntime() {
        val root = createTempDir(prefix = "nsc-test")
        val nsc = NestedSandboxCascade(root) { SandboxCapabilities(1024, null, null, false) }
        val plan = nsc.plan(WorkloadProfile(requiresUnixBinary = true, expectedRamMb = 512))
        assertEquals(IsolationLevel.UNAVAILABLE, plan.level)
        assertFalse(plan.ready)
        root.deleteRecursively()
    }


    @Test fun nscRejectsSymlinkedProotBinary() {
        val root = createTempDir(prefix = "nsc-link-test")
        val native = File(root, "native").apply { mkdirs() }
        val rootfs = File(root, "rootfs/default").apply { mkdirs() }
        val link = File(native, "proot")
        runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(), File("/bin/sh").toPath()) }
        val nsc = NestedSandboxCascade(root) { SandboxCapabilities(4096, link, rootfs, false) }
        val plan = nsc.plan(WorkloadProfile(requiresUnixBinary = true, expectedRamMb = 256))
        assertEquals(IsolationLevel.UNAVAILABLE, plan.level)
        root.deleteRecursively()
    }

    @Test fun gopaRequiresSevenOfTenDistinctRegisteredPeers() {
        val pairs = (1..10).map { GopaKeys.generateEd25519() }
        val peers = pairs.mapIndexed { i, pair -> PeerIdentity("peer-${i + 1}", GopaKeys.encodePublic(pair.public)) }
        val gopa = GopaConsensus(peers)
        val artifact = "a".repeat(64)
        val evidence = listOf("b".repeat(64), "c".repeat(64))
        pairs.forEachIndexed { i, pair ->
            gopa.createAttestation(artifact, if (i < 7) AuditVerdict.PASS else AuditVerdict.FAIL,
                evidence, "peer-${i + 1}", pair.private)
        }
        val result = gopa.consensus(artifact)
        assertEquals(7, result.requiredQuorum)
        assertEquals(10, result.totalValid)
        assertEquals(7, result.agreeingPeers)
        assertTrue(result.accepted)
        assertEquals("PASS", result.finalVerdict)
    }

    @Test fun gopaRejectsTamperedReceiptAndDuplicatePeer() {
        val pair = GopaKeys.generateEd25519()
        val peer = PeerIdentity("peer-a", GopaKeys.encodePublic(pair.public))
        val gopa = GopaConsensus(listOf(peer))
        val artifact = "d".repeat(64)
        val receipt = gopa.createAttestation(artifact, AuditVerdict.PASS, listOf("e".repeat(64)), "peer-a", pair.private)
        assertEquals(0, gopa.ingest(listOf(receipt.copy(verdict = AuditVerdict.FAIL))))
        assertEquals(1, gopa.consensus(artifact).totalValid)
        assertEquals(0, gopa.ingest(listOf(receipt)))
    }


    @Test fun gopaRejectsSixOfTenEvenWhenAllSixAgree() {
        val pairs = (1..10).map { GopaKeys.generateEd25519() }
        val peers = pairs.mapIndexed { i, pair -> PeerIdentity("p-${i + 1}", GopaKeys.encodePublic(pair.public)) }
        val gopa = GopaConsensus(peers)
        val artifact = "f".repeat(64)
        pairs.take(6).forEachIndexed { i, pair ->
            gopa.createAttestation(artifact, AuditVerdict.PASS, listOf("1".repeat(64)), "p-${i + 1}", pair.private)
        }
        val result = gopa.consensus(artifact)
        assertEquals(7, result.requiredQuorum)
        assertFalse(result.accepted)
        assertEquals("NO_CONSENSUS", result.finalVerdict)
    }

    @Test fun gopaMerkleRootChangesWhenEvidenceChanges() {
        val a = GopaConsensus.merkleRoot(listOf("a".repeat(64), "b".repeat(64)))
        val b = GopaConsensus.merkleRoot(listOf("a".repeat(64), "c".repeat(64)))
        assertNotEquals(a, b)
    }
}
