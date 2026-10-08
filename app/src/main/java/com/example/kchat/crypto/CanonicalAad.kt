package com.example.kchat.crypto

/**
 * Phase 2D Canonical Authenticated Associated Data (AAD) generator.
 *
 * Cryptographically binds media ciphertext to its specific location in KChat:
 *   "KCHAT-AAD-V1" + 0x00 + channelId + 0x00 + messageId + 0x00 + mediaType
 *
 * Prevents ciphertext transplantation attacks (moving an encrypted file from one chat to another,
 * from one message to another, or from one media type to another).
 */
object CanonicalAad {

    private const val PROTOCOL_HEADER = "KCHAT-AAD-V1"
    private const val DELIMITER: Byte = 0x00

    val VALID_MEDIA_TYPES = setOf("image", "video", "audio", "file", "document")

    /**
     * Builds the exact canonical AAD byte sequence.
     * Throws [IllegalArgumentException] if any parameter is invalid.
     */
    fun build(channelId: String, messageId: String, mediaType: String): ByteArray {
        val cleanChannelId = channelId.trim()
        val cleanMessageId = messageId.trim()
        val cleanMediaType = mediaType.trim().lowercase()

        require(cleanChannelId.isNotEmpty()) { "channelId cannot be blank" }
        require(cleanMessageId.isNotEmpty()) { "messageId cannot be blank" }
        require(VALID_MEDIA_TYPES.contains(cleanMediaType)) {
            "Invalid mediaType '$cleanMediaType'. Allowed values: $VALID_MEDIA_TYPES"
        }

        val headerBytes = PROTOCOL_HEADER.toByteArray(Charsets.US_ASCII)
        val channelBytes = cleanChannelId.toByteArray(Charsets.UTF_8)
        val messageBytes = cleanMessageId.toByteArray(Charsets.UTF_8)
        val typeBytes = cleanMediaType.toByteArray(Charsets.US_ASCII)

        val totalLength = headerBytes.size + 1 + channelBytes.size + 1 + messageBytes.size + 1 + typeBytes.size
        val result = ByteArray(totalLength)

        var offset = 0
        System.arraycopy(headerBytes, 0, result, offset, headerBytes.size)
        offset += headerBytes.size
        result[offset++] = DELIMITER

        System.arraycopy(channelBytes, 0, result, offset, channelBytes.size)
        offset += channelBytes.size
        result[offset++] = DELIMITER

        System.arraycopy(messageBytes, 0, result, offset, messageBytes.size)
        offset += messageBytes.size
        result[offset++] = DELIMITER

        System.arraycopy(typeBytes, 0, result, offset, typeBytes.size)

        return result
    }
}
