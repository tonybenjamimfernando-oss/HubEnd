package dev.hubops

import dev.hubops.despertar.BuildRepairAdvisor
import dev.hubops.despertar.BuildRepairFinding
import org.junit.Assert.*
import org.junit.Test

class BuildRepairAdvisorTest {
    @Test fun detectsMissingSdkAndGivesActionableGuidance() {
        val findings = BuildRepairAdvisor.analyze("", "SDK location not found. Define location with sdk.dir", 1)
        assertEquals("sdk_missing", findings.first().id)
        assertTrue(findings.first().recommendation.contains("ANDROID_HOME"))
    }

    @Test fun detectsUnresolvedKotlinReference() {
        val findings = BuildRepairAdvisor.analyze("e: MainActivity.kt: unresolved reference: rememberThing", "", 1)
        assertTrue(findings.any { it.id == "unresolved_symbol" })
    }

    @Test fun detectsDependencyNetworkFailure() {
        val findings = BuildRepairAdvisor.analyze("", "Could not resolve com.example:lib:1.0. UnknownHostException", 1)
        assertTrue(findings.any { it.id == "dependency_network" })
    }

    @Test fun limitsAndDeduplicatesFindings() {
        val log = (1..20).joinToString("\n") { "Could not resolve dependency $it" } + "\nNo space left on device"
        val findings = BuildRepairAdvisor.analyze(log, "", 1)
        assertEquals(findings.map { it.id }.distinct(), findings.map { it.id })
        assertTrue(findings.size <= 8)
        assertTrue(findings.any { it.id == "disk_space" })
    }

    @Test fun unknownFailureDoesNotInventRootCause() {
        val findings = BuildRepairAdvisor.analyze("unexpected tool failure", "", 17)
        assertEquals("unknown_failure", findings.single().id)
        assertEquals(45, findings.single().confidence)
    }

    @Test fun successfulEmptyLogIsNotClaimedAsVerifiedBuild() {
        val findings = BuildRepairAdvisor.analyze("", "", 0)
        assertEquals(BuildRepairFinding.Severity.INFO, findings.single().severity)
        assertTrue(findings.single().recommendation.contains("não substitui"))
    }
}
