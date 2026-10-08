package com.example.kchat

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Phase 2C Secure Vault Media Manager for KChat.
 *
 * Responsibilities:
 * - Coordinates private direct-chat and group-chat media uploads to Supabase Storage `chatter_vault`
 *   via short-lived upload grants from the Render backend.
 * - Resolves `vault:<channelId>/<filename>` references into temporary signed URLs for display/playback.
 * - Maintains a thread-safe, in-memory cache of signed URLs with early expiration (30 seconds before TTL)
 *   to avoid redundant token generation and network requests during UI rendering and Compose recomposition.
 * - Strictly preserves legacy public URLs (e.g. `chatter_images`) by passing them through directly.
 */
object VaultMediaManager {

    private const val TAG = "VaultMediaManager"
    private const val BASE_URL = "https://kchat-notification-server.onrender.com"
    const val VAULT_PREFIX = "vault:"
    const val BUCKET_NAME = "chatter_vault"

    private data class CacheEntry(
        val signedUrl: String,
        val expiresAtMs: Long
    )

    // Thread-safe in-memory cache keyed by objectPath (e.g., "uidA_uidB/uuid.jpg")
    private val signedUrlCache = ConcurrentHashMap<String, CacheEntry>()
    private val fetchMutex = Mutex()

    /**
     * Determines if a raw URL string is a private chatter_vault reference.
     */
    fun isVaultUrl(url: String?): Boolean {
        return url != null && url.startsWith(VAULT_PREFIX)
    }

    /**
     * Parses a vault reference string:
     *   "vault:<channelId>/<filename>"
     * into:
     *   Pair(channelId, objectPath)
     * e.g., "vault:uidA_uidB/uuid.jpg" -> Pair("uidA_uidB", "uidA_uidB/uuid.jpg")
     */
    fun parseVaultReference(raw: String): Pair<String, String>? {
        if (!isVaultUrl(raw)) return null
        val fullPath = raw.removePrefix(VAULT_PREFIX).trim()
        if (!fullPath.contains('/')) return null
        val channelId = fullPath.substringBefore('/')
        if (channelId.isBlank()) return null
        return Pair(channelId, fullPath)
    }

    /**
     * Synchronously checks if a signed URL is present and not expired in the local memory cache.
     * Returns null if missing or expired.
     */
    fun getCachedUrlOnly(rawUrl: String?): String? {
        if (rawUrl == null) return null
        val parsed = parseVaultReference(rawUrl) ?: return rawUrl // If legacy HTTP URL, return as-is
        val (_, objectPath) = parsed
        val entry = signedUrlCache[objectPath] ?: return null
        val now = System.currentTimeMillis()
        // Treat URL as expired 30 seconds before server expiry
        return if (now < entry.expiresAtMs - 30_000L) {
            entry.signedUrl
        } else {
            signedUrlCache.remove(objectPath)
            null
        }
    }

    /**
     * Asynchronously retrieves the current Firebase ID token.
     */
    suspend fun getFirebaseIdToken(): String? = suspendCancellableCoroutine { cont ->
        val user = Firebase.auth.currentUser
        if (user == null) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        user.getIdToken(false)
            .addOnSuccessListener { result ->
                if (cont.isActive) {
                    cont.resume(result.token)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to retrieve Firebase ID token: ${e.message}")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
    }

    /**
     * Resolves a media reference (vault: or legacy HTTP URL) into an active, loadable URL.
     * Legacy public URLs are returned immediately ($O(1)$).
     * Vault references are resolved via the in-memory cache or fetched via the Render server.
     */
    suspend fun resolveMediaUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val parsed = parseVaultReference(rawUrl) ?: return rawUrl // Legacy URL, return directly

        val (channelId, objectPath) = parsed

        // 1. Check in-memory cache first (O(1))
        val cached = getCachedUrlOnly(rawUrl)
        if (cached != null) return cached

        // 2. Fetch from Render server with concurrency lock
        return fetchMutex.withLock {
            // Re-check cache inside lock to deduplicate concurrent requests
            val doubleCheck = getCachedUrlOnly(rawUrl)
            if (doubleCheck != null) return@withLock doubleCheck

            fetchSignedUrlFromBackend(channelId, objectPath)
        }
    }

    private suspend fun fetchSignedUrlFromBackend(
        channelId: String,
        objectPath: String,
        expiresInSeconds: Int = 300
    ): String? = withContext(Dispatchers.IO) {
        val idToken = getFirebaseIdToken()
        if (idToken.isNullOrEmpty()) {
            Log.e(TAG, "Cannot fetch signed URL: Missing Firebase ID token")
            return@withContext null
        }

        try {
            val url = URL("$BASE_URL/media/signed-url")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val requestBody = JSONObject().apply {
                put("channelId", channelId)
                put("objectPath", objectPath)
                put("bucket", BUCKET_NAME)
                put("expiresIn", expiresInSeconds)
            }.toString()

            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success")) {
                    val signedUrl = json.optString("signedUrl", "")
                    val serverExpiresIn = json.optInt("expiresIn", expiresInSeconds)
                    if (signedUrl.isNotEmpty()) {
                        val expiresAtMs = System.currentTimeMillis() + (serverExpiresIn * 1000L)
                        signedUrlCache[objectPath] = CacheEntry(signedUrl, expiresAtMs)
                        return@withContext signedUrl
                    }
                }
            } else {
                val errorStream = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.e(TAG, "fetchSignedUrl failed ($responseCode): $errorStream")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching signed URL for $objectPath: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Uploads in-memory byte payload to chatter_vault via signed upload URL.
     * Returns "vault:$channelId/$fileName" on success, or null on failure.
     */
    suspend fun uploadBytesToVault(
        channelId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        val idToken = getFirebaseIdToken()
        if (idToken.isNullOrEmpty()) {
            Log.e(TAG, "Cannot upload to vault: Missing Firebase ID token")
            return@withContext null
        }

        val objectPath = "$channelId/$fileName"

        // 1. Request signed upload URL from Render backend
        val uploadUrl = requestSignedUploadUrl(channelId, objectPath, idToken)
        if (uploadUrl.isNullOrEmpty()) {
            Log.e(TAG, "Failed to obtain signed upload URL for $objectPath")
            return@withContext null
        }

        // 2. PUT raw file bytes directly to Supabase Storage
        val uploadSuccess = putFileBytesToSupabase(uploadUrl, bytes, mimeType)
        if (!uploadSuccess) {
            Log.e(TAG, "Failed to upload file bytes to Supabase for $objectPath")
            return@withContext null
        }

        // 3. Return canonical vault reference to be stored in Firebase RTDB
        return@withContext "$VAULT_PREFIX$objectPath"
    }

    /**
     * Streams file from disk to chatter_vault via signed upload URL.
     * Avoids reading entire file into RAM, suitable for audio, video, and documents.
     * Returns "vault:$channelId/$fileName" on success, or null on failure.
     */
    suspend fun uploadFileToVault(
        channelId: String,
        fileName: String,
        file: File,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        val idToken = getFirebaseIdToken()
        if (idToken.isNullOrEmpty()) {
            Log.e(TAG, "Cannot upload to vault: Missing Firebase ID token")
            return@withContext null
        }

        val objectPath = "$channelId/$fileName"

        // 1. Request signed upload URL from Render backend
        val uploadUrl = requestSignedUploadUrl(channelId, objectPath, idToken)
        if (uploadUrl.isNullOrEmpty()) {
            Log.e(TAG, "Failed to obtain signed upload URL for $objectPath")
            return@withContext null
        }

        // 2. PUT raw file stream directly to Supabase Storage
        val uploadSuccess = putStreamToSupabase(uploadUrl, file, mimeType)
        if (!uploadSuccess) {
            Log.e(TAG, "Failed to stream file to Supabase for $objectPath")
            return@withContext null
        }

        // 3. Return canonical vault reference to be stored in Firebase RTDB
        return@withContext "$VAULT_PREFIX$objectPath"
    }

    private fun requestSignedUploadUrl(
        channelId: String,
        objectPath: String,
        idToken: String
    ): String? {
        try {
            val url = URL("$BASE_URL/media/signed-upload-url")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val requestBody = JSONObject().apply {
                put("channelId", channelId)
                put("objectPath", objectPath)
                put("bucket", BUCKET_NAME)
            }.toString()

            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success")) {
                    val uploadUrl = json.optString("uploadUrl", "")
                    if (uploadUrl.isNotEmpty()) {
                        return uploadUrl
                    }
                }
            } else {
                val errorStream = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.e(TAG, "requestSignedUploadUrl failed ($responseCode): $errorStream")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error requesting signed upload URL: ${e.message}")
        }
        return null
    }

    private fun putFileBytesToSupabase(
        uploadUrl: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Boolean {
        try {
            val url = URL(uploadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
                // Do NOT send Firebase Authorization
                // Do NOT send Supabase API key
                setRequestProperty("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                setFixedLengthStreamingMode(fileBytes.size)
            }

            conn.outputStream.use { os ->
                os.write(fileBytes)
                os.flush()
            }

            return conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Network error uploading bytes to Supabase: ${e.message}")
            return false
        }
    }

    private fun putStreamToSupabase(
        uploadUrl: String,
        file: File,
        mimeType: String
    ): Boolean {
        try {
            val url = URL(uploadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
                // Do NOT send Firebase Authorization
                // Do NOT send Supabase API key
                setRequestProperty("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                setFixedLengthStreamingMode(file.length())
            }

            file.inputStream().use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            return conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Network error streaming file to Supabase: ${e.message}")
            return false
        }
    }
}
