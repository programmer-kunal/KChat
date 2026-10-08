package com.example.kchat.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

class CryptoFoundationTest {

    // =========================================================================
    // 1. CANONICAL AAD TESTS
    // =========================================================================

    @Test
    fun canonicalAad_buildsExpectedBytes() {
        val channelId = "channel_123"
        val messageId = "-Msg_456"
        val mediaType = "image"

        val aad = CanonicalAad.build(channelId, messageId, mediaType)
        assertNotNull(aad)

        // Check canonical header prefix
        val expectedPrefix = "KCHAT-AAD-V1\u0000channel_123\u0000-Msg_456\u0000image".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expectedPrefix, aad)
    }

    @Test
    fun canonicalAad_normalizesMediaTypeCaseAndWhitespace() {
        val aad1 = CanonicalAad.build("ch1", "msg1", "IMAGE")
        val aad2 = CanonicalAad.build(" ch1 ", " msg1 ", "  image  ")
        assertArrayEquals(aad1, aad2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun canonicalAad_rejectsBlankChannelId() {
        CanonicalAad.build("", "msg1", "image")
    }

    @Test(expected = IllegalArgumentException::class)
    fun canonicalAad_rejectsBlankMessageId() {
        CanonicalAad.build("ch1", "   ", "image")
    }

    @Test(expected = IllegalArgumentException::class)
    fun canonicalAad_rejectsInvalidMediaType() {
        CanonicalAad.build("ch1", "msg1", "unsupported_media_type")
    }

    @Test
    fun canonicalAad_acceptsAllApprovedMediaTypes() {
        for (type in CanonicalAad.VALID_MEDIA_TYPES) {
            val aad = CanonicalAad.build("ch1", "msg1", type)
            assertTrue(aad.isNotEmpty())
        }
    }

    // =========================================================================
    // 2. SMALL MEDIA (AEAD AES-256-GCM) TESTS
    // =========================================================================

    @Test
    fun mediaEncryptor_smallMedia_encryptDecrypt_succeeds() {
        val plaintext = "Hello Secure KChat World - Phase 2D Verified".toByteArray(Charsets.UTF_8)
        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("ch_alpha", "msg_1", "image")

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, aad)
        assertTrue(ciphertext.size > plaintext.size)

        val decrypted = MediaEncryptor.decryptSmall(ciphertext, mediaKey, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun mediaEncryptor_smallMedia_tamperedCiphertext_failsDecryption() {
        val plaintext = "Confidential Voice Note Bytes".toByteArray(Charsets.UTF_8)
        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("ch_alpha", "msg_2", "audio")

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, aad)

        // Flip a byte in the ciphertext payload
        val tampered = ciphertext.clone()
        tampered[tampered.size - 5] = (tampered[tampered.size - 5].toInt() xor 0xFF).toByte()

        try {
            MediaEncryptor.decryptSmall(tampered, mediaKey, aad)
            fail("Expected GeneralSecurityException on tampered ciphertext")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun mediaEncryptor_smallMedia_mismatchedAad_failsDecryption() {
        val plaintext = "Secret Document File".toByteArray(Charsets.UTF_8)
        val mediaKey = MediaEncryptor.generateMediaKey()
        val correctAad = CanonicalAad.build("ch_secret", "msg_3", "file")
        val wrongAad = CanonicalAad.build("ch_public", "msg_3", "file")

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, correctAad)

        try {
            MediaEncryptor.decryptSmall(ciphertext, mediaKey, wrongAad)
            fail("Expected GeneralSecurityException on mismatched AAD")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaEncryptor_smallMedia_exceeding50Mb_isRejected() {
        MediaEncryptor.validateMediaSize(52_428_801L) // 50 MB + 1 byte
    }

    // =========================================================================
    // 3. STREAMING MEDIA (STREAMING AEAD AES-256-GCM-HKDF 4KB) TESTS
    // =========================================================================

    @Test
    fun mediaEncryptor_streaming_encryptDecrypt_succeeds() {
        // Generate a 128 KB sample payload (multiple 4 KB chunks)
        val originalData = ByteArray(128 * 1024)
        for (i in originalData.indices) {
            originalData[i] = (i % 256).toByte()
        }

        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("ch_video_room", "msg_video_1", "video")

        val encOut = ByteArrayOutputStream()
        MediaEncryptor.encryptStream(
            input = ByteArrayInputStream(originalData),
            output = encOut,
            mediaKey = mediaKey,
            aad = aad,
            totalSizeBytes = originalData.size.toLong()
        )

        val ciphertext = encOut.toByteArray()
        assertTrue(ciphertext.size > originalData.size)

        val decOut = ByteArrayOutputStream()
        MediaEncryptor.decryptStream(
            input = ByteArrayInputStream(ciphertext),
            output = decOut,
            mediaKey = mediaKey,
            aad = aad
        )

        assertArrayEquals(originalData, decOut.toByteArray())
    }

    @Test
    fun mediaEncryptor_streaming_tamperedCiphertext_fails() {
        val originalData = ByteArray(64 * 1024) { 0x42 }
        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("ch_video_room", "msg_video_2", "video")

        val encOut = ByteArrayOutputStream()
        MediaEncryptor.encryptStream(
            input = ByteArrayInputStream(originalData),
            output = encOut,
            mediaKey = mediaKey,
            aad = aad,
            totalSizeBytes = originalData.size.toLong()
        )

        val ciphertext = encOut.toByteArray()
        // Tamper a byte in the middle of a chunk
        ciphertext[2048] = (ciphertext[2048].toInt() xor 0xFF).toByte()

        val decOut = ByteArrayOutputStream()
        try {
            MediaEncryptor.decryptStream(
                input = ByteArrayInputStream(ciphertext),
                output = decOut,
                mediaKey = mediaKey,
                aad = aad
            )
            fail("Expected exception during streaming decryption of corrupted data")
        } catch (expected: Exception) {
            // Expected
        }
    }

    // =========================================================================
    // 4. HPKE KEY WRAPPING TESTS
    // =========================================================================

    @Test
    fun hpke_wrapAndUnwrap_recoversOriginalMediaKey() {
        val recipientKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val recipientPublicHandle = recipientKeysetHandle.publicKeysetHandle

        val mediaKey = MediaEncryptor.generateMediaKey()
        val contextInfo = "KChat-DirectMedia-Wrap".toByteArray(Charsets.UTF_8)

        val wrappedKeyEnvelope = MediaEncryptor.wrapMediaKey(mediaKey, recipientPublicHandle, contextInfo)
        assertTrue(wrappedKeyEnvelope.isNotEmpty())

        val recoveredKey = MediaEncryptor.unwrapMediaKey(wrappedKeyEnvelope, recipientKeysetHandle, contextInfo)
        assertArrayEquals(mediaKey, recoveredKey)
    }

    @Test
    fun hpke_unwrapWithWrongKey_fails() {
        val recipientKeyset1 = MediaEncryptor.generateHpkeKeysetHandle()
        val recipientKeyset2 = MediaEncryptor.generateHpkeKeysetHandle()

        val mediaKey = MediaEncryptor.generateMediaKey()
        val wrapped = MediaEncryptor.wrapMediaKey(mediaKey, recipientKeyset1.publicKeysetHandle)

        try {
            MediaEncryptor.unwrapMediaKey(wrapped, recipientKeyset2)
            fail("Expected GeneralSecurityException when unwrapping with wrong private keyset")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    // =========================================================================
    // 5. DEVICE IDENTITY & BINDING SIGNATURE TESTS
    // =========================================================================

    @Test
    fun keyManager_deviceBinding_signAndVerify_succeeds() {
        val keyPair = KeyManager.generateSoftwareIdentityKeyPair()
        val uid = "user_alice_123"
        val deviceId = "device_poco_f6"
        val hpkePubKeyBase64 = "ZXhhbXBsZUhwa2VLZXk="

        val signature = KeyManager.signDeviceBinding(
            uid = uid,
            deviceId = deviceId,
            hpkePublicKeyBase64 = hpkePubKeyBase64,
            privateKey = keyPair.private
        )

        val isValid = KeyManager.verifyDeviceBinding(
            uid = uid,
            deviceId = deviceId,
            hpkePublicKeyBase64 = hpkePubKeyBase64,
            signature = signature,
            identityPublicKeyBytes = keyPair.public.encoded
        )
        assertTrue(isValid)
    }

    @Test
    fun keyManager_deviceBinding_tamperedUid_failsVerification() {
        val keyPair = KeyManager.generateSoftwareIdentityKeyPair()
        val uid = "user_alice_123"
        val deviceId = "device_poco_f6"
        val hpkePubKeyBase64 = "ZXhhbXBsZUhwa2VLZXk="

        val signature = KeyManager.signDeviceBinding(
            uid = uid,
            deviceId = deviceId,
            hpkePublicKeyBase64 = hpkePubKeyBase64,
            privateKey = keyPair.private
        )

        val isValid = KeyManager.verifyDeviceBinding(
            uid = "user_eve_999", // Tampered UID!
            deviceId = deviceId,
            hpkePublicKeyBase64 = hpkePubKeyBase64,
            signature = signature,
            identityPublicKeyBytes = keyPair.public.encoded
        )
        assertFalse(isValid)
    }

    // =========================================================================
    // 6. SAFETY NUMBER & FINGERPRINT TESTS
    // =========================================================================

    @Test
    fun keyManager_safetyNumber_isSymmetricAndDeterministic() {
        val keyPairA = KeyManager.generateSoftwareIdentityKeyPair()
        val keyPairB = KeyManager.generateSoftwareIdentityKeyPair()

        val uidA = "alice_uid"
        val pkA = keyPairA.public.encoded
        val uidB = "bob_uid"
        val pkB = keyPairB.public.encoded

        val safetyNumberAB = KeyManager.computeSafetyNumber(uidA, pkA, uidB, pkB)
        val safetyNumberBA = KeyManager.computeSafetyNumber(uidB, pkB, uidA, pkA)

        // Symmetry test
        assertEquals(safetyNumberAB, safetyNumberBA)

        // Format test: 12 blocks of 5 digits separated by single space (total length = 12 * 5 + 11 = 71 chars)
        assertEquals(71, safetyNumberAB.length)
        val blocks = safetyNumberAB.split(" ")
        assertEquals(12, blocks.size)
        for (block in blocks) {
            assertEquals(5, block.length)
            assertTrue(block.all { it.isDigit() })
        }
    }

    @Test
    fun keyManager_safetyNumber_changesWhenKeyChanges() {
        val keyPairA1 = KeyManager.generateSoftwareIdentityKeyPair()
        val keyPairA2 = KeyManager.generateSoftwareIdentityKeyPair()
        val keyPairB = KeyManager.generateSoftwareIdentityKeyPair()

        val num1 = KeyManager.computeSafetyNumber("alice", keyPairA1.public.encoded, "bob", keyPairB.public.encoded)
        val num2 = KeyManager.computeSafetyNumber("alice", keyPairA2.public.encoded, "bob", keyPairB.public.encoded)

        assertFalse(num1 == num2)
    }
}
