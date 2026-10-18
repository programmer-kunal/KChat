package com.example.kchat.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.subtle.AesGcmJce
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * Phase 2D Core Media Encryptor and Stream Manager.
 *
 * Responsibilities:
 * - Enforces strict 50 MB (52,428,800 bytes) media limit.
 * - Generates 256-bit cryptographically secure per-media keys (Km).
 * - Encrypts/decrypts small media (<= 5 MB) using Google Tink AesGcmJce.
 * - Streams large media (> 5 MB up to 50 MB) using Google Tink AesGcmHkdfStreaming (4 KB chunks).
 * - Wraps/unwraps media keys using Google Tink HPKE (DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM).
 * - Never constructs or modifies raw Tink wire-format internals manually.
 */
object MediaEncryptor {

    const val MAX_MEDIA_SIZE_BYTES = 52_428_800L // Exact 50 MB limit (50 * 1024 * 1024)
    const val SMALL_MEDIA_THRESHOLD_BYTES = 5 * 1024 * 1024L // 5 MB

    private val secureRandom = SecureRandom()

    init {
        AeadConfig.register()
        HybridConfig.register()
    }

    /**
     * Checks if media size is within the strict 50 MB production constraint.
     */
    fun validateMediaSize(sizeBytes: Long) {
        require(sizeBytes in 1..MAX_MEDIA_SIZE_BYTES) {
            if (sizeBytes <= 0) "Media payload is empty ($sizeBytes bytes)"
            else "Media payload exceeds 50 MB limit ($sizeBytes bytes > $MAX_MEDIA_SIZE_BYTES bytes)"
        }
    }

    /**
     * Generates a fresh, uniformly random 256-bit (32 bytes) media encryption key.
     */
    fun generateMediaKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Encrypts small media payload (<= 5 MB) with AES-256-GCM.
     */
    fun encryptSmall(plaintext: ByteArray, mediaKey: ByteArray, aad: ByteArray): ByteArray {
        validateMediaSize(plaintext.size.toLong())
        require(mediaKey.size == 32) { "Media key must be exactly 32 bytes (256-bit)" }
        val cipher = AesGcmJce(mediaKey)
        return cipher.encrypt(plaintext, aad)
    }

    /**
     * Decrypts small media payload (<= 5 MB) with AES-256-GCM.
     */
    fun decryptSmall(ciphertext: ByteArray, mediaKey: ByteArray, aad: ByteArray): ByteArray {
        require(mediaKey.size == 32) { "Media key must be exactly 32 bytes (256-bit)" }
        val cipher = AesGcmJce(mediaKey)
        return cipher.decrypt(ciphertext, aad)
    }

    /**
     * Encrypts UTF-8 text string with AES-256-GCM and returns Base64 ciphertext.
     */
    fun encryptText(plaintext: String, key: ByteArray, aad: ByteArray): String {
        require(key.size == 32) { "Key must be exactly 32 bytes (256-bit)" }
        val cipher = AesGcmJce(key)
        val ciphertext = cipher.encrypt(plaintext.toByteArray(Charsets.UTF_8), aad)
        return java.util.Base64.getEncoder().encodeToString(ciphertext)
    }

    /**
     * Decrypts Base64 ciphertext with AES-256-GCM and returns UTF-8 text string.
     */
    fun decryptText(ciphertextB64: String, key: ByteArray, aad: ByteArray): String {
        require(key.size == 32) { "Key must be exactly 32 bytes (256-bit)" }
        val cipher = AesGcmJce(key)
        val ciphertextBytes = java.util.Base64.getDecoder().decode(ciphertextB64)
        val decrypted = cipher.decrypt(ciphertextBytes, aad)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Streams encryption of large media (> 5 MB, <= 50 MB) using Tink Streaming AEAD (4 KB chunks).
     * Memory usage is constant (< 256 KB) regardless of file size.
     */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        mediaKey: ByteArray,
        aad: ByteArray,
        totalSizeBytes: Long
    ) {
        validateMediaSize(totalSizeBytes)
        require(mediaKey.size == 32) { "Media key must be exactly 32 bytes (256-bit)" }

        // AES256_GCM_HKDF_4KB streaming configuration:
        // key = mediaKey, hkdfHash = HmacSha256, derivedKeySize = 32, chunkSize = 4096, firstSegmentOffset = 0
        val streamingAead = AesGcmHkdfStreaming(mediaKey, "HmacSha256", 32, 4096, 0)
        val encryptingStream = streamingAead.newEncryptingStream(output, aad)

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalRead = 0L

        encryptingStream.use { encOut ->
            input.use { inStream ->
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    totalRead += bytesRead
                    if (totalRead > MAX_MEDIA_SIZE_BYTES) {
                        throw IllegalArgumentException("Stream exceeded 50 MB limit during encryption")
                    }
                    encOut.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    /**
     * Streams decryption of large media (> 5 MB, <= 50 MB) using Tink Streaming AEAD.
     */
    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        mediaKey: ByteArray,
        aad: ByteArray
    ) {
        require(mediaKey.size == 32) { "Media key must be exactly 32 bytes (256-bit)" }

        val streamingAead = AesGcmHkdfStreaming(mediaKey, "HmacSha256", 32, 4096, 0)
        val decryptingStream = streamingAead.newDecryptingStream(input, aad)

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int

        output.use { outStream ->
            decryptingStream.use { decIn ->
                while (decIn.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    /**
     * Generates a new Tink HPKE keyset handle for device encryption.
     * Algorithm: DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM
     */
    fun generateHpkeKeysetHandle(): KeysetHandle {
        return KeysetHandle.generateNew(com.google.crypto.tink.KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
    }

    /**
     * Wraps (encrypts) the media key Km using the recipient's HPKE public keyset handle.
     */
    fun wrapMediaKey(mediaKey: ByteArray, recipientPublicKeysetHandle: KeysetHandle, contextInfo: ByteArray = ByteArray(0)): ByteArray {
        require(mediaKey.size == 32) { "Media key must be exactly 32 bytes" }
        val hybridEncrypt = recipientPublicKeysetHandle.getPrimitive(HybridEncrypt::class.java)
        return hybridEncrypt.encrypt(mediaKey, contextInfo)
    }

    /**
     * Unwraps (decrypts) the media key Km using the recipient's HPKE private keyset handle.
     */
    fun unwrapMediaKey(wrappedKeyEnvelope: ByteArray, recipientPrivateKeysetHandle: KeysetHandle, contextInfo: ByteArray = ByteArray(0)): ByteArray {
        val hybridDecrypt = recipientPrivateKeysetHandle.getPrimitive(HybridDecrypt::class.java)
        return hybridDecrypt.decrypt(wrappedKeyEnvelope, contextInfo)
    }
}
