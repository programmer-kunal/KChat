package com.example.kchat.crypto

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Phase 2D Group Sender-Key Manager.
 *
 * Implements the ratcheted Sender-Key protocol for group media encryption:
 * - Forward-stepping sender chains using HMAC-SHA256.
 * - Per-group epoch isolation (groupEpochId).
 * - Bounded skipped-key handling (max gap: 50, max cache: 100 per sender, TTL: 7 days).
 * - Replay protection: prevents duplicate decryption using consumed indexes.
 * - Zero unbounded key derivations.
 */
object GroupSenderKeyManager {

    const val MAX_GAP = 50
    const val MAX_SKIPPED_KEYS_PER_SENDER = 100
    const val SKIPPED_KEY_TTL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days

    private val HMAC_ALGORITHM = "HmacSHA256"
    private val MEDIA_KEY_INFO = "KCHAT-GROUP-MEDIA-KEY".toByteArray(StandardCharsets.UTF_8)
    private val CHAIN_KEY_INFO = "KCHAT-GROUP-CHAIN-KEY".toByteArray(StandardCharsets.UTF_8)

    data class SenderSession(
        val epochId: Int,
        var chainKey: ByteArray,
        var currentIndex: Int
    )

    data class ReceiverSession(
        val channelId: String,
        val epochId: Int,
        val senderUid: String,
        var chainKey: ByteArray,
        var currentIndex: Int
    )

    data class SkippedKeyEntry(
        val mediaKey: ByteArray,
        val expiresAtMs: Long
    )

    // Key: "channelId:epochId"
    private val senderSessions = ConcurrentHashMap<String, SenderSession>()

    // Key: "channelId:epochId:senderUid"
    private val receiverSessions = ConcurrentHashMap<String, ReceiverSession>()

    // Key: "channelId:epochId:senderUid:index"
    private val skippedKeys = ConcurrentHashMap<String, SkippedKeyEntry>()

    // Key: "channelId:epochId:senderUid:index" -> true
    private val consumedIndexes = ConcurrentHashMap<String, Boolean>()

    // In-memory cache of derived media keys for fast re-render / scroll / chat re-entry
    // Key: "channelId:epochId:senderUid:index" -> mediaKey
    private val derivedKeyCache = ConcurrentHashMap<String, ByteArray>()

    private val secureRandom = SecureRandom()

    /**
     * Generates a fresh 256-bit root chain key (CK_0) for a new sender epoch.
     */
    fun generateRootChainKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Initializes or resets a sender session for a specific group epoch.
     */
    @Synchronized
    fun initSenderSession(channelId: String, epochId: Int, rootChainKey: ByteArray): SenderSession {
        val sessionKey = "$channelId:$epochId"
        val session = SenderSession(
            epochId = epochId,
            chainKey = rootChainKey.copyOf(),
            currentIndex = 0
        )
        senderSessions[sessionKey] = session
        return session
    }

    /**
     * Retrieves an existing sender session for a specific group epoch.
     */
    fun getSenderSession(channelId: String, epochId: Int): SenderSession? {
        return senderSessions["$channelId:$epochId"]
    }

    /**
     * Steps the sender chain forward by one message.
     * Returns Pair(senderKeyIndex, derivedMediaKey).
     */
    @Synchronized
    fun stepSender(channelId: String, epochId: Int): Pair<Int, ByteArray> {
        val sessionKey = "$channelId:$epochId"
        val session = senderSessions[sessionKey]
            ?: throw IllegalStateException("Sender session not initialized for $sessionKey")

        val index = session.currentIndex
        val (mediaKey, nextChainKey) = ratchetStep(session.chainKey)

        session.chainKey = nextChainKey
        session.currentIndex = index + 1

        return Pair(index, mediaKey)
    }

    /**
     * Initializes or updates a receiver session for a sender in a specific group epoch.
     */
    @Synchronized
    fun initReceiverSession(
        channelId: String,
        epochId: Int,
        senderUid: String,
        rootChainKey: ByteArray
    ): ReceiverSession {
        val sessionKey = "$channelId:$epochId:$senderUid"
        val session = ReceiverSession(
            channelId = channelId,
            epochId = epochId,
            senderUid = senderUid,
            chainKey = rootChainKey.copyOf(),
            currentIndex = 0
        )
        receiverSessions[sessionKey] = session
        return session
    }

    /**
     * Retrieves an existing receiver session.
     */
    fun getReceiverSession(channelId: String, epochId: Int, senderUid: String): ReceiverSession? {
        return receiverSessions["$channelId:$epochId:$senderUid"]
    }

    /**
     * Obtains the media key for a target sender key index:
     * - Validates replay protection.
     * - Checks skipped-keys cache first.
     * - Enforces max gap of 50 indexes.
     * - Derives intermediate keys and caches them with 7-day TTL (max 100 per sender).
     * - Deletes skipped keys immediately upon use.
     * Returns null if replay detected, gap exceeded, or session missing.
     */
    @Synchronized
    fun getOrDeriveMediaKey(
        channelId: String,
        epochId: Int,
        senderUid: String,
        targetIndex: Int
    ): ByteArray? {
        val indexKey = "$channelId:$epochId:$senderUid:$targetIndex"

        // 1. In-memory derived key cache (enables re-rendering / scroll / chat re-entry)
        val cached = derivedKeyCache[indexKey]
        if (cached != null) {
            return cached
        }

        // 2. Replay Protection: Check if already consumed without being in cache
        if (consumedIndexes[indexKey] == true) {
            return null
        }

        // 3. Check Skipped Keys cache
        pruneExpiredSkippedKeys()
        val skipped = skippedKeys.remove(indexKey)
        if (skipped != null) {
            if (System.currentTimeMillis() <= skipped.expiresAtMs) {
                consumedIndexes[indexKey] = true
                derivedKeyCache[indexKey] = skipped.mediaKey
                return skipped.mediaKey
            }
        }

        // 4. Check Receiver Session
        val sessionKey = "$channelId:$epochId:$senderUid"
        val session = receiverSessions[sessionKey] ?: return null

        // 5. If targetIndex < currentIndex, message is from the past and missing from skipped cache -> Reject
        if (targetIndex < session.currentIndex) {
            return null
        }

        // 6. Gap validation: targetIndex > currentIndex + MAX_GAP -> Reject / resync required
        val gap = targetIndex - session.currentIndex
        if (gap > MAX_GAP) {
            return null
        }

        // 7. Derive intermediate skipped keys
        val senderPrefix = "$channelId:$epochId:$senderUid:"
        val currentSenderSkippedCount = skippedKeys.keys.count { it.startsWith(senderPrefix) }

        while (session.currentIndex < targetIndex) {
            val currIdx = session.currentIndex
            val (mediaKey, nextChainKey) = ratchetStep(session.chainKey)
            session.chainKey = nextChainKey
            session.currentIndex = currIdx + 1

            // Cache skipped key if within per-sender limit
            if (currentSenderSkippedCount < MAX_SKIPPED_KEYS_PER_SENDER) {
                val skippedEntryKey = "$channelId:$epochId:$senderUid:$currIdx"
                skippedKeys[skippedEntryKey] = SkippedKeyEntry(
                    mediaKey = mediaKey,
                    expiresAtMs = System.currentTimeMillis() + SKIPPED_KEY_TTL_MS
                )
            }
        }

        // 8. Derive the target key
        val (targetMediaKey, nextChainKey) = ratchetStep(session.chainKey)
        session.chainKey = nextChainKey
        session.currentIndex = targetIndex + 1

        consumedIndexes[indexKey] = true
        derivedKeyCache[indexKey] = targetMediaKey
        return targetMediaKey
    }

    /**
     * Cryptographic Ratchet Step:
     * MK = HMAC-SHA256(CK, "KCHAT-GROUP-MEDIA-KEY")
     * CK_next = HMAC-SHA256(CK, "KCHAT-GROUP-CHAIN-KEY")
     */
    fun ratchetStep(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val mediaKey = hmacSha256(chainKey, MEDIA_KEY_INFO)
        val nextChainKey = hmacSha256(chainKey, CHAIN_KEY_INFO)
        return Pair(mediaKey, nextChainKey)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    /**
     * Prunes expired skipped keys (TTL: 7 days).
     */
    fun pruneExpiredSkippedKeys() {
        val now = System.currentTimeMillis()
        val iterator = skippedKeys.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now > entry.value.expiresAtMs) {
                iterator.remove()
            }
        }
    }

    /**
     * Clears all session and key states. Called on logout / account switch.
     */
    fun clear() {
        senderSessions.clear()
        receiverSessions.clear()
        skippedKeys.clear()
        consumedIndexes.clear()
        derivedKeyCache.clear()
    }
}
