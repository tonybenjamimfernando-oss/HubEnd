package com.hubops.vislumbrops

import com.hubops.vislumbrops.certification.CertificationAuthority
import com.hubops.vislumbrops.certification.EvidenceChain
import com.hubops.vislumbrops.core.*
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class VislumbropsTest {

    @Test fun `evidence chain is valid`() {
        val chain = EvidenceChain()
        val contract = VerificationContract(
            projectId = "test",
            artifactPath = "/tmp/apk",
            artifactHash = "abc123",
            score = VerificationScore(80, 85, 90, 70, 75,
                VerificationScore.Grade.SILVER),
            findings = listOf(
                Finding(category = CheckCategory.SECRETS,
                    severity = Severity.HIGH,
                    title = "test", description = "test")
            )
        )
        val evidence = chain.build(contract)
        assertTrue(chain.verifyChain(evidence))
    }

    @Test fun `tampered evidence chain is invalid`() {
        val chain = EvidenceChain()
        val contract = VerificationContract(
            projectId = "test", artifactPath = "/tmp/apk",
            artifactHash = "abc123"
        )
        val evidence = chain.build(contract)
        val tampered = evidence.toMutableList()
        tampered[0] = tampered[0].copy(payload = "hacked")
        assertFalse(chain.verifyChain(tampered))
    }

    @Test fun `certificate signs and verifies`() {
        val key = CryptoProvider.generateKey()
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        val ca = CertificationAuthority { secretKey }
        val contract = VerificationContract(
            projectId = "p", artifactPath = "/a", artifactHash = "h",
            score = VerificationScore(85, 85, 85, 85, 85,
                VerificationScore.Grade.SILVER)
        )
        val cert = ca.issue(contract)
        val withCert = contract.copy(certificate = cert)
        assertTrue(ca.verify(withCert))
    }

    @Test fun `constant time equals`() {
        assertTrue(CryptoProvider.constantTimeEquals("abc", "abc"))
        assertFalse(CryptoProvider.constantTimeEquals("abc", "abd"))
        assertFalse(CryptoProvider.constantTimeEquals("abc", "abcd"))
    }

    @Test fun `entropy of random string is high`() {
        val high = CryptoProvider.shannonEntropy("aK9xQ2mLpR7vN3zW8bY4")
        val low = CryptoProvider.shannonEntropy("aaaaaaaaaaaaaaaaaaaa")
        assertTrue(high > 3.5)
        assertTrue(low < 0.5)
    }
    @Test fun `certificate metadata tampering is rejected`() {
        val key = CryptoProvider.generateKey()
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        val ca = CertificationAuthority { secretKey }
        val contract = VerificationContract(
            projectId = "p", artifactPath = "/a", artifactHash = "h",
            score = VerificationScore(85, 85, 85, 85, 85, VerificationScore.Grade.SILVER)
        )
        val certificate = ca.issue(contract)
        val tampered = contract.copy(certificate = certificate.copy(issuer = "Fake issuer"))
        assertFalse(ca.verify(tampered))
    }
}
