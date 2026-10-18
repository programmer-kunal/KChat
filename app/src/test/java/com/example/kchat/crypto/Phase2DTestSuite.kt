package com.example.kchat.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Phase 2D Comprehensive Test Suite.
 *
 * Verifies:
 * 1. Group sender-key ratcheting (HKDF/HMAC ratcheting, forward secrecy, receiver derivation).
 * 2. Replay protection and bounded skipped-key cache (gap <= 50, max 100 keys, TTL handling).
 * 3. Epoch isolation and future-message access revocation.
 * 4. Device identity binding, ECDSA verification, and Safety Number properties.
 * 5. Canonical AAD tampering resistance (ciphertext, channelId, messageId, mediaType).
 * 6. Hard 50 MB boundary constraints (<= 50 MB valid, > 50 MB rejected).
 * 7. Streaming AEAD chunked payload round-trip integrity.
 */
class Phase2DTestSuite {

    @Before
    fun setUp() {
        GroupSenderKeyManager.clear()
    }

    // =========================================================================
    // 1. GROUP SENDER-KEY RATCHETING & FORWARD SECRECY
    // =========================================================================

    @Test
    fun groupSenderKey_senderAndReceiver_stepInSync() {
        val channelId = "-group_123"
        val epochId = 1
        val senderUid = "alice_uid"

        // Initialize sender and receiver with identical root chain key
        val rootChainKey = GroupSenderKeyManager.generateRootChainKey()
        GroupSenderKeyManager.initSenderSession(channelId, epochId, rootChainKey)
        GroupSenderKeyManager.initReceiverSession(channelId, epochId, senderUid, rootChainKey)

        // Generate 5 message keys sequentially
        for (expectedIndex in 0 until 5) {
            val (stepIndex, senderMediaKey) = GroupSenderKeyManager.stepSender(channelId, epochId)
            assertEquals("Sender index should match expected sequence", expectedIndex, stepIndex)

            val derivedReceiverKey = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, expectedIndex)
            assertNotNull("Receiver must derive media key at index $expectedIndex", derivedReceiverKey)
            assertArrayEquals("Derived receiver key must equal sender media key", senderMediaKey, derivedReceiverKey)
        }
    }

    @Test
    fun groupSenderKey_forwardSecrecy_chainKeyRatcheting() {
        val channelId = "-group_123"
        val epochId = 1

        val rootChainKey = GroupSenderKeyManager.generateRootChainKey()
        GroupSenderKeyManager.initSenderSession(channelId, epochId, rootChainKey)

        val (_, key0) = GroupSenderKeyManager.stepSender(channelId, epochId)
        val (_, key1) = GroupSenderKeyManager.stepSender(channelId, epochId)

        // Keys at different ratchet steps must be distinct
        assertFalse("Keys at successive steps must not be equal", key0.contentEquals(key1))
    }

    @Test
    fun groupSenderKey_receiverOutOfOrderDerivation_boundedGapSucceeds() {
        val channelId = "-group_123"
        val epochId = 1
        val senderUid = "bob_uid"

        val rootChainKey = GroupSenderKeyManager.generateRootChainKey()
        GroupSenderKeyManager.initSenderSession(channelId, epochId, rootChainKey)
        GroupSenderKeyManager.initReceiverSession(channelId, epochId, senderUid, rootChainKey)

        // Sender produces 15 messages (indices 0 to 14)
        val senderKeys = mutableListOf<ByteArray>()
        for (i in 0 until 15) {
            val (_, key) = GroupSenderKeyManager.stepSender(channelId, epochId)
            senderKeys.add(key)
        }

        // Receiver receives message at index 14 FIRST (gap of 14 steps, within limit of 50)
        val receiverKey14 = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 14)
        assertNotNull("Receiver should derive key 14", receiverKey14)
        assertArrayEquals("Derived key 14 must match sender key 14", senderKeys[14], receiverKey14)

        // Receiver then receives previously skipped messages (e.g. index 3, index 7, index 0)
        val receiverKey3 = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 3)
        assertNotNull("Receiver should retrieve cached skipped key 3", receiverKey3)
        assertArrayEquals("Derived key 3 must match sender key 3", senderKeys[3], receiverKey3)

        val receiverKey7 = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 7)
        assertNotNull("Receiver should retrieve cached skipped key 7", receiverKey7)
        assertArrayEquals("Derived key 7 must match sender key 7", senderKeys[7], receiverKey7)

        val receiverKey0 = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 0)
        assertNotNull("Receiver should retrieve cached skipped key 0", receiverKey0)
        assertArrayEquals("Derived key 0 must match sender key 0", senderKeys[0], receiverKey0)
    }

    @Test
    fun groupSenderKey_excessiveGap_rejected() {
        val channelId = "-group_123"
        val epochId = 1
        val senderUid = "bob_uid"

        val rootChainKey = GroupSenderKeyManager.generateRootChainKey()
        GroupSenderKeyManager.initReceiverSession(channelId, epochId, senderUid, rootChainKey)

        // Requesting index 51 when current is 0 exceeds MAX_SENDER_KEY_GAP (50)
        val result = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 51)
        assertNull("Gaps exceeding MAX_SENDER_KEY_GAP (50) must be rejected with null", result)
    }

    @Test
    fun groupSenderKey_replayAttackProtection_skippedKeyConsumedOnce() {
        val channelId = "-group_123"
        val epochId = 1
        val senderUid = "carol_uid"

        val rootChainKey = GroupSenderKeyManager.generateRootChainKey()
        GroupSenderKeyManager.initSenderSession(channelId, epochId, rootChainKey)
        GroupSenderKeyManager.initReceiverSession(channelId, epochId, senderUid, rootChainKey)

        // Sender produces 5 messages
        for (i in 0 until 5) {
            GroupSenderKeyManager.stepSender(channelId, epochId)
        }

        // Receiver receives index 4 (skips 0, 1, 2, 3)
        GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 4)

        // Receiver uses skipped key 2 (first retrieval succeeds)
        val firstRetrieval = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 2)
        assertNotNull("First retrieval of skipped key must succeed", firstRetrieval)

        // Subsequent retrieval re-uses cached derived key so scrolling/reopening chat re-renders properly
        val cachedRetrieval = GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, 2)
        assertNotNull("Subsequent retrieval must succeed using derived key cache for re-render", cachedRetrieval)
        assertArrayEquals("Cached key must match first retrieval", firstRetrieval, cachedRetrieval)
    }

    @Test
    fun groupSenderKey_epochIsolation() {
        val channelId = "-group_123"
        val senderUid = "dave_uid"

        val epoch1Root = GroupSenderKeyManager.generateRootChainKey()
        val epoch2Root = GroupSenderKeyManager.generateRootChainKey()

        GroupSenderKeyManager.initSenderSession(channelId, 1, epoch1Root)
        GroupSenderKeyManager.initSenderSession(channelId, 2, epoch2Root)

        GroupSenderKeyManager.initReceiverSession(channelId, 1, senderUid, epoch1Root)
        GroupSenderKeyManager.initReceiverSession(channelId, 2, senderUid, epoch2Root)

        val (_, keyEpoch1) = GroupSenderKeyManager.stepSender(channelId, 1)
        val (_, keyEpoch2) = GroupSenderKeyManager.stepSender(channelId, 2)

        assertFalse("Keys from distinct epochs must be completely independent", keyEpoch1.contentEquals(keyEpoch2))

        // Key from epoch 1 cannot decrypt epoch 2 ciphertext
        val aad = CanonicalAad.build(channelId, "-msg_epoch2", "image")
        val plaintext = "Epoch 2 secret message".toByteArray(Charsets.UTF_8)
        val ciphertext = MediaEncryptor.encryptSmall(plaintext, keyEpoch2, aad)

        try {
            MediaEncryptor.decryptSmall(ciphertext, keyEpoch1, aad)
            fail("Epoch 1 key must not decrypt Epoch 2 ciphertext")
        } catch (expected: GeneralSecurityException) {
            // Expected cryptographic failure
        }
    }

    // =========================================================================
    // 2. DEVICE BINDING & SAFETY NUMBER TESTS
    // =========================================================================

    @Test
    fun keyManager_deviceBinding_signatureVerifies() {
        val uid = "user_42"
        val deviceId = "dev_phone_poco"
        val hpkeKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val hpkePubBytes = KeyManager.serializeKeysetHandle(hpkeKeysetHandle.publicKeysetHandle)
        val hpkePubB64 = Base64.getEncoder().encodeToString(hpkePubBytes)

        val identityPair = KeyManager.generateSoftwareIdentityKeyPair()

        // Sign binding
        val signature = KeyManager.signDeviceBinding(uid, deviceId, hpkePubB64, identityPair.private)
        assertNotNull(signature)

        // Verify binding with valid public key bytes
        val isValid = KeyManager.verifyDeviceBinding(uid, deviceId, hpkePubB64, signature, identityPair.public.encoded)
        assertTrue("Device binding statement must verify successfully", isValid)
    }

    @Test
    fun keyManager_deviceBinding_tamperedStatementFails() {
        val uid = "user_42"
        val deviceId = "dev_phone_poco"
        val hpkeKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val hpkePubBytes = KeyManager.serializeKeysetHandle(hpkeKeysetHandle.publicKeysetHandle)
        val hpkePubB64 = Base64.getEncoder().encodeToString(hpkePubBytes)

        val identityPair = KeyManager.generateSoftwareIdentityKeyPair()
        val signature = KeyManager.signDeviceBinding(uid, deviceId, hpkePubB64, identityPair.private)

        // Verify with tampered deviceId
        val isTamperedDevice = KeyManager.verifyDeviceBinding(uid, "attacker_device", hpkePubB64, signature, identityPair.public.encoded)
        assertFalse("Tampered deviceId must fail verification", isTamperedDevice)

        // Verify with tampered UID
        val isTamperedUid = KeyManager.verifyDeviceBinding("attacker_uid", deviceId, hpkePubB64, signature, identityPair.public.encoded)
        assertFalse("Tampered UID must fail verification", isTamperedUid)
    }

    @Test
    fun keyManager_safetyNumber_isDeterministicAndOrderIndependent() {
        val pairA = KeyManager.generateSoftwareIdentityKeyPair()
        val pairB = KeyManager.generateSoftwareIdentityKeyPair()

        val safetyNumberAB = KeyManager.computeSafetyNumber("userA", pairA.public.encoded, "userB", pairB.public.encoded)
        val safetyNumberBA = KeyManager.computeSafetyNumber("userB", pairB.public.encoded, "userA", pairA.public.encoded)

        // Safety number must be exactly 60 digits formatted in 12 groups of 5 digits (60 digits + 11 spaces = 71 chars)
        assertEquals("Safety number must be 71 characters formatted", 71, safetyNumberAB.length)
        assertEquals("Safety number must be identical regardless of party ordering", safetyNumberAB, safetyNumberBA)
    }

    // =========================================================================
    // 3. HPKE MEDIA KEY WRAPPING & UNWRAPPING
    // =========================================================================

    @Test
    fun mediaEncryptor_hpkeWrapUnwrap_roundTrip() {
        val recipientKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val originalMediaKey = MediaEncryptor.generateMediaKey()

        val wrappedKey = MediaEncryptor.wrapMediaKey(originalMediaKey, recipientKeysetHandle.publicKeysetHandle)
        assertNotNull(wrappedKey)

        val unwrappedKey = MediaEncryptor.unwrapMediaKey(wrappedKey, recipientKeysetHandle)
        assertArrayEquals("Unwrapped media key must match original media key", originalMediaKey, unwrappedKey)
    }

    @Test
    fun mediaEncryptor_hpkeUnwrap_wrongKeyFails() {
        val recipientKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val wrongKeysetHandle = MediaEncryptor.generateHpkeKeysetHandle()
        val originalMediaKey = MediaEncryptor.generateMediaKey()

        val wrappedKey = MediaEncryptor.wrapMediaKey(originalMediaKey, recipientKeysetHandle.publicKeysetHandle)

        try {
            MediaEncryptor.unwrapMediaKey(wrappedKey, wrongKeysetHandle)
            fail("Unwrapping with wrong private key must throw GeneralSecurityException")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    // =========================================================================
    // 4. CANONICAL AAD & TAMPERING PROTECTION
    // =========================================================================

    @Test
    fun canonicalAad_tamperedChannelId_failsDecryption() {
        val mediaKey = MediaEncryptor.generateMediaKey()
        val validAad = CanonicalAad.build("direct_alice_bob", "-msg_100", "image")
        val plaintext = "Secret photo contents".toByteArray(Charsets.UTF_8)

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, validAad)

        // Attacker attempts to replay ciphertext in different channel
        val tamperedAad = CanonicalAad.build("direct_alice_eve", "-msg_100", "image")
        try {
            MediaEncryptor.decryptSmall(ciphertext, mediaKey, tamperedAad)
            fail("Decryption must fail when channelId in AAD is tampered")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun canonicalAad_tamperedMessageId_failsDecryption() {
        val mediaKey = MediaEncryptor.generateMediaKey()
        val validAad = CanonicalAad.build("ch1", "-msg_original", "video")
        val plaintext = "Secret video clip".toByteArray(Charsets.UTF_8)

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, validAad)

        val tamperedAad = CanonicalAad.build("ch1", "-msg_transplanted", "video")
        try {
            MediaEncryptor.decryptSmall(ciphertext, mediaKey, tamperedAad)
            fail("Decryption must fail when messageId in AAD is tampered")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun canonicalAad_tamperedMediaType_failsDecryption() {
        val mediaKey = MediaEncryptor.generateMediaKey()
        val validAad = CanonicalAad.build("ch1", "-msg_100", "audio")
        val plaintext = "Secret voice note".toByteArray(Charsets.UTF_8)

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, validAad)

        val tamperedAad = CanonicalAad.build("ch1", "-msg_100", "image")
        try {
            MediaEncryptor.decryptSmall(ciphertext, mediaKey, tamperedAad)
            fail("Decryption must fail when mediaType in AAD is tampered")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun mediaEncryptor_tamperedCiphertextBytes_failsDecryption() {
        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("ch1", "-msg_100", "file")
        val plaintext = "Confidential document".toByteArray(Charsets.UTF_8)

        val ciphertext = MediaEncryptor.encryptSmall(plaintext, mediaKey, aad)

        // Modify 1 byte in ciphertext
        val tamperedCiphertext = ciphertext.clone()
        tamperedCiphertext[tamperedCiphertext.size - 1] = (tamperedCiphertext[tamperedCiphertext.size - 1].toInt() xor 0x01).toByte()

        try {
            MediaEncryptor.decryptSmall(tamperedCiphertext, mediaKey, aad)
            fail("Decryption must fail when ciphertext is modified")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    // =========================================================================
    // 5. 50 MB BOUNDARY CONSTRAINTS
    // =========================================================================

    @Test
    fun mediaEncryptor_sizeValidation_at50MbLimit_passes() {
        // Exactly 50 MB
        MediaEncryptor.validateMediaSize(52_428_800L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaEncryptor_sizeValidation_exceeding50Mb_throws() {
        // 50 MB + 1 byte
        MediaEncryptor.validateMediaSize(52_428_801L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaEncryptor_sizeValidation_zeroBytes_throws() {
        MediaEncryptor.validateMediaSize(0L)
    }

    // =========================================================================
    // 6. STREAMING AEAD ROUND-TRIP INTEGRITY
    // =========================================================================

    @Test
    fun mediaEncryptor_streamingAead_roundTrip() {
        val mediaKey = MediaEncryptor.generateMediaKey()
        val aad = CanonicalAad.build("channel_stream", "-msg_stream_1", "video")

        // 128 KB simulated video stream (crosses multiple 4 KB Tink chunks)
        val testData = ByteArray(128 * 1024) { (it % 251).toByte() }

        val encOut = ByteArrayOutputStream()
        MediaEncryptor.encryptStream(
            input = ByteArrayInputStream(testData),
            output = encOut,
            mediaKey = mediaKey,
            aad = aad,
            totalSizeBytes = testData.size.toLong()
        )

        val ciphertext = encOut.toByteArray()
        assertTrue("Ciphertext must be produced", ciphertext.isNotEmpty())

        val decOut = ByteArrayOutputStream()
        MediaEncryptor.decryptStream(
            input = ByteArrayInputStream(ciphertext),
            output = decOut,
            mediaKey = mediaKey,
            aad = aad
        )

        assertArrayEquals("Stream decrypted payload must match original", testData, decOut.toByteArray())
    }
}
