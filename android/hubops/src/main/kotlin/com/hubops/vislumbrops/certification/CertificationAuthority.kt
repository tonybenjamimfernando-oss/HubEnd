package com.hubops.vislumbrops.certification

import com.hubops.vislumbrops.core.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Issues device-local attestations. The HMAC key is protected by Android Keystore;
 * this is not a public-CA signature and cannot be independently verified on another device.
 */
class CertificationAuthority(private val signingKeyProvider: () -> SecretKey) {

    fun issue(contract: VerificationContract): Certificate {
        val score = contract.score ?: error("Score obrigatório")
        val id = "VIS-${UUID.randomUUID()}"
        val issued = Instant.now().toString()
        val expiry = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        val issuer = "Vislumbrops Local Attestation (Android Keystore)"
        val signature = CryptoProvider.hmacSha256(
            canonicalPayload(contract, id, issued, expiry, score.grade, issuer).toByteArray(Charsets.UTF_8),
            signingKeyProvider()
        )
        return Certificate(
            certificateId = id,
            issuedAt = issued,
            validUntil = expiry,
            signature = signature,
            grade = score.grade,
            issuer = issuer
        )
    }

    fun verify(contract: VerificationContract): Boolean {
        val certificate = contract.certificate ?: return false
        val score = contract.score ?: return false
        if (certificate.grade != score.grade || certificate.signature.isBlank()) return false
        return try {
            val issuedAt = Instant.parse(certificate.issuedAt)
            val validUntil = Instant.parse(certificate.validUntil)
            if (issuedAt.isAfter(Instant.now().plusSeconds(300)) || !validUntil.isAfter(Instant.now())) return false
            val expected = CryptoProvider.hmacSha256(
                canonicalPayload(
                    contract, certificate.certificateId, certificate.issuedAt,
                    certificate.validUntil, certificate.grade, certificate.issuer
                ).toByteArray(Charsets.UTF_8),
                signingKeyProvider()
            )
            CryptoProvider.constantTimeEquals(certificate.signature, expected)
        } catch (_: Exception) { false }
    }

    private fun canonicalPayload(
        c: VerificationContract,
        certificateId: String,
        issuedAt: String,
        validUntil: String,
        grade: VerificationScore.Grade,
        issuer: String
    ): String = buildString {
        append(c.contractVersion).append('|')
        append(c.verificationId).append('|').append(c.projectId).append('|')
        append(c.artifactHash).append('|').append(c.completedAt).append('|')
        append(c.status.name).append('|')
        append(c.score?.overall).append('|').append(grade).append('|')
        append(certificateId).append('|').append(issuedAt).append('|').append(validUntil).append('|')
        append(issuer).append('|')
        c.findings.sortedBy { it.id }.forEach {
            append(it.id).append(':').append(it.severity).append(':').append(it.category).append('|')
        }
        c.evidenceChain.forEach { append(it.type).append(':').append(it.hash).append('|') }
        c.metadata.toSortedMap().forEach { (key, value) ->
            append(key).append('=').append(value).append('|')
        }
    }
}
