package com.example.kchat.crypto

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.KeysetHandle
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Phase 2D Device Key & Identity Manager.
 *
 * Responsibilities:
 * - Manages hardware-backed device identity keys (Android Keystore EC P-256).
 * - Manages Tink HPKE keypairs and encrypts them at rest.
 * - Signs device binding statements.
 * - Registers device public keys in Firebase RTDB (/user_devices/{uid}/{deviceId}).
 * - Enforces Trust-On-First-Use (TOFU) and detects identity key changes.
 * - Verifies peer device bindings.
 */
object DeviceKeyManager {

    private const val TAG = "DeviceKeyManager"
    private const val PREFS_NAME = "kchat_crypto_prefs"
    private const val PREF_DEVICE_ID = "kchat_device_id"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IDENTITY_KEY_ALIAS = "kchat_identity_ec"
    private const val MASTER_KEY_ALIAS = "kchat_storage_master"

    data class DeviceRecord(
        val deviceId: String = "",
        val identityPublicKey: String = "",
        val hpkePublicKey: String = "",
        val deviceBindingSignature: String = "",
        val registeredAt: Long = 0L
    )

    init {
        try {
            com.google.crypto.tink.aead.AeadConfig.register()
            com.google.crypto.tink.hybrid.HybridConfig.register()
        } catch (e: Exception) {
            Log.e(TAG, "Tink registration failed: ${e.message}")
        }
    }

    private var cachedDeviceId: String? = null
    private var inMemoryIdentityKeyPair: KeyPair? = null
    private var inMemoryHpkePrivateKeyHandle: KeysetHandle? = null

    /**
     * Gets or generates the persistent Device ID.
     */
    fun getDeviceId(context: Context?): String {
        cachedDeviceId?.let { return it }
        if (context == null) {
            val id = UUID.randomUUID().toString()
            cachedDeviceId = id
            return id
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(PREF_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
        }
        cachedDeviceId = id
        return id
    }

    /**
     * Sets a manual device ID (for testing).
     */
    fun setDeviceIdForTesting(deviceId: String) {
        cachedDeviceId = deviceId
    }

    /**
     * Retrieves or generates the device identity keypair.
     * Uses Android Keystore on Android, with fallback to software keypair in JVM unit tests.
     */
    fun getOrCreateIdentityKeyPair(context: Context?): KeyPair {
        inMemoryIdentityKeyPair?.let { return it }

        if (context != null) {
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (ks.containsAlias(IDENTITY_KEY_ALIAS)) {
                    val entry = ks.getEntry(IDENTITY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    if (entry != null) {
                        val pair = KeyPair(entry.certificate.publicKey, entry.privateKey)
                        inMemoryIdentityKeyPair = pair
                        return pair
                    }
                }

                // Generate in Android Keystore
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                val parameterSpec = KeyGenParameterSpec.Builder(
                    IDENTITY_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .build()
                kpg.initialize(parameterSpec)
                val pair = kpg.generateKeyPair()
                inMemoryIdentityKeyPair = pair
                return pair
            } catch (e: Exception) {
                Log.w(TAG, "Android Keystore unavailable or failed, falling back to software keypair: ${e.message}")
            }
        }

        // Software fallback
        val pair = KeyManager.generateSoftwareIdentityKeyPair()
        inMemoryIdentityKeyPair = pair
        return pair
    }

    /**
     * Retrieves or generates the device HPKE private keyset handle.
     */
    fun getOrCreateHpkePrivateKeyHandle(context: Context?): KeysetHandle {
        inMemoryHpkePrivateKeyHandle?.let { return it }

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encData = prefs.getString("hpke_private_keyset_enc", null)
            val ivStr = prefs.getString("hpke_private_keyset_iv", null)

            if (!encData.isNullOrBlank() && !ivStr.isNullOrBlank()) {
                try {
                    val decryptedBytes = decryptWithMasterKey(
                        Base64.decode(encData, Base64.NO_WRAP),
                        Base64.decode(ivStr, Base64.NO_WRAP)
                    )
                    val handle = KeyManager.deserializeKeysetHandle(decryptedBytes)
                    inMemoryHpkePrivateKeyHandle = handle
                    return handle
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decrypt stored HPKE keyset, regenerating: ${e.message}")
                }
            }

            // Generate fresh HPKE key
            val handle = MediaEncryptor.generateHpkeKeysetHandle()
            try {
                val serialized = KeyManager.serializeKeysetHandle(handle)
                val (ciphertext, iv) = encryptWithMasterKey(serialized)
                prefs.edit()
                    .putString("hpke_private_keyset_enc", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString("hpke_private_keyset_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Could not store encrypted HPKE keyset to Keystore: ${e.message}")
            }
            inMemoryHpkePrivateKeyHandle = handle
            return handle
        }

        val handle = MediaEncryptor.generateHpkeKeysetHandle()
        inMemoryHpkePrivateKeyHandle = handle
        return handle
    }

    /**
     * Gets public HPKE keyset handle for this device.
     */
    fun getHpkePublicKeyHandle(context: Context?): KeysetHandle {
        val privateHandle = getOrCreateHpkePrivateKeyHandle(context)
        return privateHandle.publicKeysetHandle
    }

    /**
     * Obtains the Base64 representation of this device's HPKE public keyset.
     */
    fun getHpkePublicKeyBase64(context: Context?): String {
        val pubHandle = getHpkePublicKeyHandle(context)
        val bytes = KeyManager.serializeKeysetHandle(pubHandle)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Obtains the Base64 representation of this device's identity public key.
     */
    fun getIdentityPublicKeyBase64(context: Context?): String {
        val pair = getOrCreateIdentityKeyPair(context)
        return java.util.Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    /**
     * Signs this device's binding statement.
     */
    fun signMyDeviceBinding(context: Context?, uid: String): String {
        val pair = getOrCreateIdentityKeyPair(context)
        val deviceId = getDeviceId(context)
        val hpkePubB64 = getHpkePublicKeyBase64(context)
        val sigBytes = KeyManager.signDeviceBinding(uid, deviceId, hpkePubB64, pair.private)
        return java.util.Base64.getEncoder().encodeToString(sigBytes)
    }

    /**
     * Registers current device in Firebase Realtime Database (/user_devices/{uid}/{deviceId}).
     */
    suspend fun registerCurrentDevice(context: Context, uid: String): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext false
        try {
            val deviceId = getDeviceId(context)
            val identityPubB64 = getIdentityPublicKeyBase64(context)
            val hpkePubB64 = getHpkePublicKeyBase64(context)
            val signatureB64 = signMyDeviceBinding(context, uid)

            val record = DeviceRecord(
                deviceId = deviceId,
                identityPublicKey = identityPubB64,
                hpkePublicKey = hpkePubB64,
                deviceBindingSignature = signatureB64,
                registeredAt = System.currentTimeMillis()
            )

            val ref = Firebase.database.reference.child("user_devices").child(uid).child(deviceId)
            suspendCancellableCoroutine<Boolean> { cont ->
                ref.setValue(record).addOnCompleteListener { task ->
                    if (cont.isActive) {
                        cont.resume(task.isSuccessful)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering current device: ${e.message}")
            false
        }
    }

    /**
     * Fetches all registered devices for a user from /user_devices/{uid}.
     */
    suspend fun fetchUserDevices(uid: String): List<DeviceRecord> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext emptyList()
        try {
            val ref = Firebase.database.reference.child("user_devices").child(uid)
            suspendCancellableCoroutine { cont ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val devices = mutableListOf<DeviceRecord>()
                        for (child in snapshot.children) {
                            val record = child.getValue(DeviceRecord::class.java)
                            if (record != null && record.deviceId.isNotBlank()) {
                                devices.add(record)
                            }
                        }
                        if (cont.isActive) cont.resume(devices)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to fetch devices for $uid: ${error.message}")
                        if (cont.isActive) cont.resume(emptyList())
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching devices for $uid: ${e.message}")
            emptyList()
        }
    }

    /**
     * Verifies a device record against device binding signature and TOFU (Trust On First Use).
     * Returns true if valid and trusted, false if invalid or identity key changed unexpectedly.
     */
    fun verifyAndTrustDevice(
        context: Context?,
        ownerUid: String,
        record: DeviceRecord
    ): Boolean {
        try {
            val sigBytes = java.util.Base64.getDecoder().decode(record.deviceBindingSignature)
            val identityPubBytes = java.util.Base64.getDecoder().decode(record.identityPublicKey)

            // 1. Cryptographic signature verification
            val isValid = KeyManager.verifyDeviceBinding(
                uid = ownerUid,
                deviceId = record.deviceId,
                hpkePublicKeyBase64 = record.hpkePublicKey,
                signature = sigBytes,
                identityPublicKeyBytes = identityPubBytes
            )
            if (!isValid) {
                Log.w(TAG, "Device binding signature verification failed for $ownerUid:${record.deviceId}")
                return false
            }

            // 2. Trust-On-First-Use (TOFU) validation
            if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val tofuKey = "tofu_${ownerUid}_${record.deviceId}"
                val knownIdentityKey = prefs.getString(tofuKey, null)
                if (knownIdentityKey != null) {
                    if (knownIdentityKey != record.identityPublicKey) {
                        Log.e(TAG, "SECURITY WARNING: Identity key changed for device $ownerUid:${record.deviceId}!")
                        return false
                    }
                } else {
                    // First use: save identity key
                    prefs.edit().putString(tofuKey, record.identityPublicKey).apply()
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying device record: ${e.message}")
            return false
        }
    }

    // --- Android Keystore Master Key AES-GCM Encryption for HPKE Keyset at rest ---

    private fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = ks.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun encryptWithMasterKey(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(ciphertext, cipher.iv)
    }

    private fun decryptWithMasterKey(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Clears all in-memory keys and session state (called on logout).
     */
    fun clear() {
        inMemoryIdentityKeyPair = null
        inMemoryHpkePrivateKeyHandle = null
        cachedDeviceId = null
    }
}
