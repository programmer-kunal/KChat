package com.example.kchat.crypto

import java.util.Base64
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Phase 2D Cryptographic Key Manager.
 *
 * Responsibilities:
 * - Device identity key generation and hardware-backed storage (EC P-256).
 * - Device binding statement creation and signing.
 * - Public-key authentication and signature verification.
 * - Safety number computation (60-digit deterministic cryptographic fingerprint).
 * - HPKE keyset handle serialization and deserialization.
 */
object KeyManager {

    private const val BINDING_PREFIX = "KChat-Device-Binding:v1:"
    private const val EC_CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Generates a fresh EC P-256 KeyPair (software fallback or pure JVM test support).
     */
    fun generateSoftwareIdentityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        return kpg.generateKeyPair()
    }

    /**
     * Constructs the canonical device binding statement to bind the HPKE encryption key to the identity key.
     */
    fun constructDeviceBindingStatement(uid: String, deviceId: String, hpkePublicKeyBase64: String): ByteArray {
        val statement = "$BINDING_PREFIX$uid:$deviceId:$hpkePublicKeyBase64"
        return statement.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Signs the device binding statement using the device identity private key.
     */
    fun signDeviceBinding(
        uid: String,
        deviceId: String,
        hpkePublicKeyBase64: String,
        privateKey: PrivateKey
    ): ByteArray {
        val statement = constructDeviceBindingStatement(uid, deviceId, hpkePublicKeyBase64)
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(statement)
        return signer.sign()
    }

    /**
     * Verifies the device binding signature against the device identity public key.
     */
    fun verifyDeviceBinding(
        uid: String,
        deviceId: String,
        hpkePublicKeyBase64: String,
        signature: ByteArray,
        identityPublicKeyBytes: ByteArray
    ): Boolean {
        return try {
            val kf = KeyFactory.getInstance("EC")
            val publicKeySpec = X509EncodedKeySpec(identityPublicKeyBytes)
            val publicKey = kf.generatePublic(publicKeySpec)

            val statement = constructDeviceBindingStatement(uid, deviceId, hpkePublicKeyBase64)
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(statement)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Computes the canonical Safety Number between two users (60 digits in 12 groups of 5).
     *
     * Fingerprint = SHA-256(Sort(uidA || identityPubKeyA) || Sort(uidB || identityPubKeyB))
     * Perfectly symmetric and deterministic regardless of parameter order.
     */
    fun computeSafetyNumber(
        uidA: String,
        identityPubKeyA: ByteArray,
        uidB: String,
        identityPubKeyB: ByteArray
    ): String {
        val userAData = uidA.toByteArray(StandardCharsets.UTF_8) + identityPubKeyA
        val userBData = uidB.toByteArray(StandardCharsets.UTF_8) + identityPubKeyB

        // Canonical sorting to ensure symmetry
        val compare = uidA.compareTo(uidB)
        val combinedData = if (compare <= 0) {
            userAData + userBData
        } else {
            userBData + userAData
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combinedData)

        // Derive 60 numeric digits from the hash bytes
        // Each 4-byte chunk modulo 100,000 yields a 5-digit block (12 blocks = 60 digits)
        val blocks = mutableListOf<String>()
        var offset = 0
        val extendedHash = hash + digest.digest(hash) // 64 bytes total, sufficient for 12 * 4 = 48 bytes

        for (i in 0 until 12) {
            val chunk = ((extendedHash[offset].toInt() and 0xFF) shl 24) or
                    ((extendedHash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((extendedHash[offset + 2].toInt() and 0xFF) shl 8) or
                    (extendedHash[offset + 3].toInt() and 0xFF)
            offset += 4

            val positiveChunk = chunk.toLong() and 0xFFFFFFFFL
            val value = positiveChunk % 100000L
            blocks.add(String.format("%05d", value))
        }

        return blocks.joinToString(" ")
    }

    /**
     * Serializes a Tink KeysetHandle (public or private) to binary bytes.
     */
    fun serializeKeysetHandle(handle: KeysetHandle): ByteArray {
        val baos = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(baos))
        return baos.toByteArray()
    }

    /**
     * Deserializes binary bytes to a Tink KeysetHandle.
     */
    fun deserializeKeysetHandle(bytes: ByteArray): KeysetHandle {
        return CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(bytes))
    }
}
