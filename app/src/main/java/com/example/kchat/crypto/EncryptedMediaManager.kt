package com.example.kchat.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.example.kchat.VaultMediaManager
import com.example.kchat.model.Message
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Phase 2D Unified Encrypted Media Orchestrator.
 *
 * Responsibilities:
 * - Coordinates end-to-end media encryption for Direct Chat and Group Chat.
 * - Handles Tink AEAD (small media <= 5 MB) and Streaming AEAD (> 5 MB to 50 MB).
 * - Binds all encryption to Canonical AAD (KCHAT-AAD-V1 | channelId | messageId | mediaType).
 * - Enforces the 50 MB hard media size boundary.
 * - Resolves and decrypts incoming encrypted media.
 * - Keeps decrypted media in memory (images) or temporary private files in noBackupFilesDir (video/audio/docs).
 * - Automatically falls back to legacy plaintext media handling when mediaEncrypted != true.
 */
object EncryptedMediaManager {

    private const val TAG = "EncryptedMediaManager"
    const val MAX_MEDIA_SIZE_BYTES = 52_428_800L // 50 MB
    const val SMALL_MEDIA_THRESHOLD_BYTES = 5 * 1024 * 1024L // 5 MB

    // In-memory LRU cache for decrypted image bytes (15 MB cache size)
    private val imageMemoryCache = object : LruCache<String, ByteArray>(15 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val decryptionMutex = Mutex()

    data class DirectEncryptedUploadResult(
        val vaultUrl: String,
        val encryptedMediaKeys: Map<String, String>,
        val messageId: String
    )

    data class GroupEncryptedUploadResult(
        val vaultUrl: String,
        val epochId: Int,
        val senderKeyIndex: Int,
        val messageId: String
    )

    /**
     * Encrypts and uploads media for a Direct Chat message.
     */
    suspend fun encryptAndUploadDirectMedia(
        context: Context,
        channelId: String,
        messageId: String,
        mediaType: String,
        recipientUid: String,
        senderUid: String,
        rawBytes: ByteArray? = null,
        rawFile: File? = null,
        mimeType: String
    ): DirectEncryptedUploadResult? = withContext(Dispatchers.IO) {
        // 1. Boundary check: <= 50 MB
        val mediaSize = rawBytes?.size?.toLong() ?: rawFile?.length() ?: 0L
        MediaEncryptor.validateMediaSize(mediaSize)

        // 2. Generate per-media 256-bit key
        val mediaKey = MediaEncryptor.generateMediaKey()

        // 3. Construct canonical AAD
        val aad = CanonicalAad.build(channelId, messageId, mediaType)

        // 4. Encrypt media payload
        val fileName = "${UUID.randomUUID()}.enc"
        var uploadedVaultUrl: String? = null

        if (rawBytes != null) {
            val ciphertext = MediaEncryptor.encryptSmall(rawBytes, mediaKey, aad)
            uploadedVaultUrl = VaultMediaManager.uploadBytesToVault(channelId, fileName, ciphertext, "application/octet-stream")
        } else if (rawFile != null) {
            if (rawFile.length() <= SMALL_MEDIA_THRESHOLD_BYTES) {
                val bytes = rawFile.readBytes()
                val ciphertext = MediaEncryptor.encryptSmall(bytes, mediaKey, aad)
                uploadedVaultUrl = VaultMediaManager.uploadBytesToVault(channelId, fileName, ciphertext, "application/octet-stream")
            } else {
                // Streaming AEAD to temporary encrypted file in noBackupFilesDir
                val tempEncFile = File(context.noBackupFilesDir, "temp_enc_${messageId}.enc")
                try {
                    FileInputStream(rawFile).use { input ->
                        FileOutputStream(tempEncFile).use { output ->
                            MediaEncryptor.encryptStream(input, output, mediaKey, aad, rawFile.length())
                        }
                    }
                    uploadedVaultUrl = VaultMediaManager.uploadFileToVault(channelId, fileName, tempEncFile, "application/octet-stream")
                } finally {
                    if (tempEncFile.exists()) tempEncFile.delete()
                }
            }
        }

        if (uploadedVaultUrl == null) {
            Log.e(TAG, "Ciphertext upload failed for messageId: $messageId")
            return@withContext null
        }

        // 5. Wrap media key for recipient devices & sender devices
        val recipientDevices = DeviceKeyManager.fetchUserDevices(recipientUid)
        val senderDevices = DeviceKeyManager.fetchUserDevices(senderUid)

        val encryptedMediaKeys = mutableMapOf<String, String>()

        // Wrap for recipient's verified devices
        for (dev in recipientDevices) {
            if (DeviceKeyManager.verifyAndTrustDevice(context, recipientUid, dev)) {
                try {
                    val pubHandle = KeyManager.deserializeKeysetHandle(java.util.Base64.getDecoder().decode(dev.hpkePublicKey))
                    val wrapped = MediaEncryptor.wrapMediaKey(mediaKey, pubHandle)
                    encryptedMediaKeys[dev.deviceId] = java.util.Base64.getEncoder().encodeToString(wrapped)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to wrap media key for recipient device ${dev.deviceId}: ${e.message}")
                }
            }
        }

        // Wrap for sender's verified devices (enables multi-device sync and local viewing)
        for (dev in senderDevices) {
            if (DeviceKeyManager.verifyAndTrustDevice(context, senderUid, dev)) {
                try {
                    val pubHandle = KeyManager.deserializeKeysetHandle(java.util.Base64.getDecoder().decode(dev.hpkePublicKey))
                    val wrapped = MediaEncryptor.wrapMediaKey(mediaKey, pubHandle)
                    encryptedMediaKeys[dev.deviceId] = java.util.Base64.getEncoder().encodeToString(wrapped)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to wrap media key for sender device ${dev.deviceId}: ${e.message}")
                }
            }
        }

        DirectEncryptedUploadResult(
            vaultUrl = uploadedVaultUrl,
            encryptedMediaKeys = encryptedMediaKeys,
            messageId = messageId
        )
    }

    /**
     * Encrypts and uploads media for a Group Chat message using Ratcheted Sender Keys.
     */
    suspend fun encryptAndUploadGroupMedia(
        context: Context,
        channelId: String,
        messageId: String,
        mediaType: String,
        senderUid: String,
        rawBytes: ByteArray? = null,
        rawFile: File? = null,
        mimeType: String
    ): GroupEncryptedUploadResult? = withContext(Dispatchers.IO) {
        // 1. Boundary check: <= 50 MB
        val mediaSize = rawBytes?.size?.toLong() ?: rawFile?.length() ?: 0L
        MediaEncryptor.validateMediaSize(mediaSize)

        // 2. Fetch current channel epoch
        val epochId = fetchChannelEpoch(channelId)

        // 3. Ensure sender session exists for (channelId, epochId)
        var session = GroupSenderKeyManager.getSenderSession(channelId, epochId)
        if (session == null) {
            val rootKey = GroupSenderKeyManager.generateRootChainKey()
            GroupSenderKeyManager.initSenderSession(channelId, epochId, rootKey)

            // Distribute root chain key wrapped to all group members
            distributeGroupRootChainKey(context, channelId, epochId, senderUid, rootKey)
        }

        // 4. Advance sender chain by one step
        val (senderKeyIndex, mediaKey) = GroupSenderKeyManager.stepSender(channelId, epochId)

        // 5. Construct canonical AAD
        val aad = CanonicalAad.build(channelId, messageId, mediaType)

        // 6. Encrypt and upload ciphertext
        val fileName = "${UUID.randomUUID()}.enc"
        var uploadedVaultUrl: String? = null

        if (rawBytes != null) {
            val ciphertext = MediaEncryptor.encryptSmall(rawBytes, mediaKey, aad)
            uploadedVaultUrl = VaultMediaManager.uploadBytesToVault(channelId, fileName, ciphertext, "application/octet-stream")
        } else if (rawFile != null) {
            if (rawFile.length() <= SMALL_MEDIA_THRESHOLD_BYTES) {
                val bytes = rawFile.readBytes()
                val ciphertext = MediaEncryptor.encryptSmall(bytes, mediaKey, aad)
                uploadedVaultUrl = VaultMediaManager.uploadBytesToVault(channelId, fileName, ciphertext, "application/octet-stream")
            } else {
                val tempEncFile = File(context.noBackupFilesDir, "temp_enc_${messageId}.enc")
                try {
                    FileInputStream(rawFile).use { input ->
                        FileOutputStream(tempEncFile).use { output ->
                            MediaEncryptor.encryptStream(input, output, mediaKey, aad, rawFile.length())
                        }
                    }
                    uploadedVaultUrl = VaultMediaManager.uploadFileToVault(channelId, fileName, tempEncFile, "application/octet-stream")
                } finally {
                    if (tempEncFile.exists()) tempEncFile.delete()
                }
            }
        }

        if (uploadedVaultUrl == null) {
            Log.e(TAG, "Group ciphertext upload failed for messageId: $messageId")
            return@withContext null
        }

        GroupEncryptedUploadResult(
            vaultUrl = uploadedVaultUrl,
            epochId = epochId,
            senderKeyIndex = senderKeyIndex,
            messageId = messageId
        )
    }

    /**
     * Resolves the media key for a message (Direct Chat or Group Chat).
     */
    suspend fun resolveMediaKeyForMessage(
        context: Context,
        channelId: String,
        message: Message
    ): ByteArray? = withContext(Dispatchers.IO) {
        val myDeviceId = DeviceKeyManager.getDeviceId(context)

        // Case 1: Direct Chat (wrapped keys in message.encryptedMediaKeys)
        if (message.encryptedMediaKeys != null) {
            val wrappedB64 = message.encryptedMediaKeys[myDeviceId] ?: run {
                Log.w(TAG, "No encrypted media key found for device $myDeviceId in direct message ${message.id}")
                return@withContext null
            }
            return@withContext try {
                val wrappedBytes = java.util.Base64.getDecoder().decode(wrappedB64)
                val privHandle = DeviceKeyManager.getOrCreateHpkePrivateKeyHandle(context)
                MediaEncryptor.unwrapMediaKey(wrappedBytes, privHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap direct media key: ${e.message}")
                null
            }
        }

        // Case 2: Group Chat (Ratcheted Sender Keys)
        val epochId = message.groupEpochId ?: return@withContext null
        val targetIndex = message.senderKeyIndex ?: return@withContext null
        val senderUid = message.senderId

        // Check if receiver session exists
        val existingSession = GroupSenderKeyManager.getReceiverSession(channelId, epochId, senderUid)
        if (existingSession == null) {
            // Fetch wrapped root chain key from RTDB
            val wrappedRootKey = fetchWrappedGroupSenderKey(channelId, epochId, senderUid, myDeviceId)
            if (wrappedRootKey == null) {
                Log.w(TAG, "No group sender key envelope found at /channel_keys/$channelId/epochs/$epochId/sender_keys/$senderUid/$myDeviceId")
                return@withContext null
            }
            try {
                val privHandle = DeviceKeyManager.getOrCreateHpkePrivateKeyHandle(context)
                val rootChainKey = MediaEncryptor.unwrapMediaKey(wrappedRootKey, privHandle)
                GroupSenderKeyManager.initReceiverSession(channelId, epochId, senderUid, rootChainKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap group root chain key: ${e.message}")
                return@withContext null
            }
        }

        // Derive target media key with bounded ratcheting and skipped-key handling
        return@withContext GroupSenderKeyManager.getOrDeriveMediaKey(channelId, epochId, senderUid, targetIndex)
    }

    /**
     * Resolves and decrypts image bytes for a message. Returns in-memory ByteArray or null.
     */
    suspend fun getOrDecryptImage(
        context: Context,
        channelId: String,
        message: Message
    ): ByteArray? = withContext(Dispatchers.IO) {
        val imageUrl = message.imageUrl ?: return@withContext null

        // If not encrypted, return null to let Coil load normal URL
        if (message.mediaEncrypted != true) return@withContext null

        // 1. Check in-memory LRU cache
        val cached = imageMemoryCache.get(message.id)
        if (cached != null) return@withContext cached

        decryptionMutex.withLock {
            val doubleCheck = imageMemoryCache.get(message.id)
            if (doubleCheck != null) return@withLock doubleCheck

            // 2. Resolve media key
            val mediaKey = resolveMediaKeyForMessage(context, channelId, message) ?: return@withLock null

            // 3. Resolve signed download URL and download ciphertext
            val signedUrl = VaultMediaManager.resolveMediaUrl(imageUrl) ?: return@withLock null
            val ciphertext = downloadBytesFromUrl(signedUrl) ?: return@withLock null

            // 4. Decrypt using canonical AAD
            val aad = CanonicalAad.build(channelId, message.id, "IMAGE")
            return@withLock try {
                val plaintext = MediaEncryptor.decryptSmall(ciphertext, mediaKey, aad)
                imageMemoryCache.put(message.id, plaintext)
                plaintext
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed for image message ${message.id}: ${e.message}")
                null
            }
        }
    }

    /**
     * Resolves and decrypts an audio message to a private temp file in noBackupFilesDir.
     */
    suspend fun getOrDecryptAudioFile(
        context: Context,
        channelId: String,
        message: Message
    ): File? = withContext(Dispatchers.IO) {
        val audioUrl = message.audioUrl ?: return@withContext null
        if (message.mediaEncrypted != true) return@withContext null

        val localDecFile = File(context.noBackupFilesDir, "temp_dec_audio_${message.id}.m4a")
        if (localDecFile.exists() && localDecFile.length() > 0L) {
            return@withContext localDecFile
        }

        decryptionMutex.withLock {
            if (localDecFile.exists() && localDecFile.length() > 0L) {
                return@withLock localDecFile
            }

            val mediaKey = resolveMediaKeyForMessage(context, channelId, message) ?: return@withLock null
            val signedUrl = VaultMediaManager.resolveMediaUrl(audioUrl) ?: return@withLock null
            val ciphertext = downloadBytesFromUrl(signedUrl) ?: return@withLock null
            val aad = CanonicalAad.build(channelId, message.id, "AUDIO")

            try {
                val plaintext = MediaEncryptor.decryptSmall(ciphertext, mediaKey, aad)
                localDecFile.writeBytes(plaintext)
                localDecFile
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed for audio message ${message.id}: ${e.message}")
                null
            }
        }
    }

    /**
     * Resolves and decrypts a video message using Streaming AEAD to a temp file in noBackupFilesDir.
     */
    suspend fun getOrDecryptVideoFile(
        context: Context,
        channelId: String,
        message: Message
    ): File? = withContext(Dispatchers.IO) {
        val videoUrl = message.videoUrl ?: return@withContext null
        if (message.mediaEncrypted != true) return@withContext null

        val localDecFile = File(context.noBackupFilesDir, "temp_dec_video_${message.id}.mp4")
        if (localDecFile.exists() && localDecFile.length() > 0L) {
            return@withContext localDecFile
        }

        decryptionMutex.withLock {
            if (localDecFile.exists() && localDecFile.length() > 0L) {
                return@withLock localDecFile
            }

            val mediaKey = resolveMediaKeyForMessage(context, channelId, message) ?: return@withLock null
            val signedUrl = VaultMediaManager.resolveMediaUrl(videoUrl) ?: return@withLock null
            val aad = CanonicalAad.build(channelId, message.id, "VIDEO")

            // Download ciphertext stream and decrypt
            val tempEncFile = File(context.noBackupFilesDir, "temp_enc_video_${message.id}.enc")
            try {
                downloadFileFromUrl(signedUrl, tempEncFile)
                if (!tempEncFile.exists() || tempEncFile.length() == 0L) return@withLock null

                FileInputStream(tempEncFile).use { encInput ->
                    FileOutputStream(localDecFile).use { decOutput ->
                        MediaEncryptor.decryptStream(encInput, decOutput, mediaKey, aad)
                    }
                }
                localDecFile
            } catch (e: Exception) {
                Log.e(TAG, "Streaming decryption failed for video message ${message.id}: ${e.message}")
                if (localDecFile.exists()) localDecFile.delete()
                null
            } finally {
                if (tempEncFile.exists()) tempEncFile.delete()
            }
        }
    }

    /**
     * Resolves and decrypts a document message to a temp file in noBackupFilesDir.
     */
    suspend fun getOrDecryptDocumentFile(
        context: Context,
        channelId: String,
        message: Message
    ): File? = withContext(Dispatchers.IO) {
        val fileUrl = message.fileUrl ?: return@withContext null
        if (message.mediaEncrypted != true) return@withContext null

        val safeName = (message.fileName ?: "doc").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val localDecFile = File(context.noBackupFilesDir, "temp_dec_doc_${message.id}_$safeName")
        if (localDecFile.exists() && localDecFile.length() > 0L) {
            return@withContext localDecFile
        }

        decryptionMutex.withLock {
            if (localDecFile.exists() && localDecFile.length() > 0L) {
                return@withLock localDecFile
            }

            val mediaKey = resolveMediaKeyForMessage(context, channelId, message) ?: return@withLock null
            val signedUrl = VaultMediaManager.resolveMediaUrl(fileUrl) ?: return@withLock null
            val aad = CanonicalAad.build(channelId, message.id, "DOCUMENT")

            val tempEncFile = File(context.noBackupFilesDir, "temp_enc_doc_${message.id}.enc")
            try {
                downloadFileFromUrl(signedUrl, tempEncFile)
                if (!tempEncFile.exists() || tempEncFile.length() == 0L) return@withLock null

                FileInputStream(tempEncFile).use { encInput ->
                    FileOutputStream(localDecFile).use { decOutput ->
                        MediaEncryptor.decryptStream(encInput, decOutput, mediaKey, aad)
                    }
                }
                localDecFile
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed for document message ${message.id}: ${e.message}")
                if (localDecFile.exists()) localDecFile.delete()
                null
            } finally {
                if (tempEncFile.exists()) tempEncFile.delete()
            }
        }
    }

    // --- Network & Storage Helpers ---

    private fun downloadBytesFromUrl(remoteUrl: String): ByteArray? {
        return try {
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download bytes from $remoteUrl: ${e.message}")
            null
        }
    }

    private fun downloadFileFromUrl(remoteUrl: String, targetFile: File) {
        try {
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream download from $remoteUrl: ${e.message}")
        }
    }

    private suspend fun fetchChannelEpoch(channelId: String): Int = suspendCancellableCoroutine { cont ->
        val ref = Firebase.database.reference.child("channels").child(channelId).child("epochId")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val epoch = snapshot.getValue(Int::class.java) ?: 1
                if (cont.isActive) cont.resume(epoch)
            }

            override fun onCancelled(error: DatabaseError) {
                if (cont.isActive) cont.resume(1)
            }
        })
    }

    private suspend fun distributeGroupRootChainKey(
        context: Context,
        channelId: String,
        epochId: Int,
        senderUid: String,
        rootChainKey: ByteArray
    ) {
        // Fetch group members
        val memberUids = fetchGroupMemberUids(channelId)
        val allUids = (memberUids + senderUid).distinct()

        val updates = mutableMapOf<String, Any>()
        for (uid in allUids) {
            val devices = DeviceKeyManager.fetchUserDevices(uid)
            for (dev in devices) {
                if (DeviceKeyManager.verifyAndTrustDevice(context, uid, dev)) {
                    try {
                        val pubHandle = KeyManager.deserializeKeysetHandle(java.util.Base64.getDecoder().decode(dev.hpkePublicKey))
                        val wrapped = MediaEncryptor.wrapMediaKey(rootChainKey, pubHandle)
                        val wrappedB64 = java.util.Base64.getEncoder().encodeToString(wrapped)
                        val path = "/channel_keys/$channelId/epochs/$epochId/sender_keys/$senderUid/${dev.deviceId}"
                        updates[path] = wrappedB64
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to wrap group root key for $uid:${dev.deviceId}: ${e.message}")
                    }
                }
            }
        }

        if (updates.isNotEmpty()) {
            suspendCancellableCoroutine<Unit> { cont ->
                Firebase.database.reference.updateChildren(updates).addOnCompleteListener {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    private suspend fun fetchGroupMemberUids(channelId: String): List<String> = suspendCancellableCoroutine { cont ->
        val ref = Firebase.database.reference.child("channels").child(channelId).child("users")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uids = snapshot.children.mapNotNull { it.key }
                if (cont.isActive) cont.resume(uids)
            }

            override fun onCancelled(error: DatabaseError) {
                if (cont.isActive) cont.resume(emptyList())
            }
        })
    }

    private suspend fun fetchWrappedGroupSenderKey(
        channelId: String,
        epochId: Int,
        senderUid: String,
        recipientDeviceId: String
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        val ref = Firebase.database.reference
            .child("channel_keys")
            .child(channelId)
            .child("epochs")
            .child(epochId.toString())
            .child("sender_keys")
            .child(senderUid)
            .child(recipientDeviceId)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val wrappedB64 = snapshot.getValue(String::class.java)
                if (wrappedB64 != null) {
                    try {
                        val bytes = java.util.Base64.getDecoder().decode(wrappedB64)
                        if (cont.isActive) cont.resume(bytes)
                        return
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                if (cont.isActive) cont.resume(null)
            }

            override fun onCancelled(error: DatabaseError) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }

    /**
     * Cleans up temporary plaintext/ciphertext decrypted files in noBackupFilesDir.
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val dir = context.noBackupFilesDir
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_dec_") || file.name.startsWith("temp_enc_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning temp files: ${e.message}")
        }
    }

    /**
     * Clears all in-memory media keys, cached images, and temporary state on logout.
     */
    fun clear(context: Context?) {
        imageMemoryCache.evictAll()
        GroupSenderKeyManager.clear()
        DeviceKeyManager.clear()
        if (context != null) {
            cleanupTempFiles(context)
        }
    }
}
