package com.hubops.vislumbrops.core

import android.content.Context
import com.hubops.vislumbrops.analysis.*
import dev.hubops.core.integration.HubOpsOrchestrator
import com.hubops.vislumbrops.hubops.HubOpsBridge
import com.hubops.vislumbrops.certification.CertificationAuthority
import com.hubops.vislumbrops.certification.EvidenceChain
import com.hubops.vislumbrops.certification.RemediationPlanner
import com.hubops.vislumbrops.reporting.ReportGenerator
import com.hubops.vislumbrops.runtime.SandboxRunner

object ServiceLocator {

    private var appContext: Context? = null

    val apkInspector: ApkInspector by lazy { ApkInspector(appContext ?: error("ServiceLocator não inicializado")) }
    val apkSignatureEngine: ApkSignatureVerificationEngine by lazy { ApkSignatureVerificationEngine(appContext ?: error("ServiceLocator não inicializado")) }
    val supplyChainEngine: ArtifactSupplyChainEngine by lazy { ArtifactSupplyChainEngine() }
    val secretScanner: SecretScanner by lazy { SecretScanner() }
    val permissionAnalyzer: PermissionAnalyzer by lazy { PermissionAnalyzer(appContext ?: error("ServiceLocator não inicializado")) }
    val staticAnalyzer: StaticAnalyzer by lazy { StaticAnalyzer() }
    val evidenceChain: EvidenceChain by lazy { EvidenceChain() }
    val remediationPlanner: RemediationPlanner by lazy { RemediationPlanner() }
    val reportGenerator: ReportGenerator by lazy { ReportGenerator() }

    val dependencyAuditor: DependencyAuditor by lazy {
        DependencyAuditor(appContext ?: error("ServiceLocator não inicializado"))
    }

    val sandboxRunner: SandboxRunner by lazy {
        SandboxRunner()
    }

    val certificationAuthority: CertificationAuthority by lazy {
        CertificationAuthority { CryptoProvider.getOrCreateSigningKey() }
    }

    val hubOpsBridge: HubOpsBridge by lazy { HubOpsBridge(evidenceChain, certificationAuthority) }

    /** Shared audit ledger: VislumbOps results become visible to HubOps without project mutation. */
    val hubLedger: HubOpsOrchestrator by lazy {
        HubOpsOrchestrator(java.io.File((appContext ?: error("ServiceLocator não inicializado")).filesDir, "hubops-integration"))
    }

    val orchestrator: VerificationOrchestrator by lazy {
        VerificationOrchestrator(
            inspector = apkInspector,
            signatureEngine = apkSignatureEngine,
            supplyChainEngine = supplyChainEngine,
            secretScanner = secretScanner,
            dependencyAuditor = dependencyAuditor,
            permissionAnalyzer = permissionAnalyzer,
            staticAnalyzer = staticAnalyzer,
            sandboxRunner = sandboxRunner,
            certAuthority = certificationAuthority,
            evidenceChain = evidenceChain,
            remediationPlanner = remediationPlanner,
            reportGenerator = reportGenerator
        )
    }

    fun bootstrap(context: Context) {
        appContext = context.applicationContext
        // A chave é gerada/recuperada sob demanda no Android Keystore.
        // Não existe chave efémera em memória que invalide certificados após reinício.
    }
}
