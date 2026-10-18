package com.example.kchat.feature.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val DATE_FORMATTER_LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val CONVERSATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
    private val DATE_HEADER_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

    /**
     * Formats user presence status for direct chat.
     * - If [isOnline] is true: "online"
     * - If offline and [lastSeen] was today: "last seen today at 1:30 PM"
     * - If offline and [lastSeen] was yesterday: "last seen yesterday at 8:45 PM"
     * - If offline and [lastSeen] was older: "last seen 15 Sep at 7:20 PM"
     * - If [lastSeen] is null or <= 0L: "offline"
     */
    fun formatPresence(
        isOnline: Boolean,
        lastSeen: Long?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isOnline) return "online"
        if (lastSeen == null || lastSeen <= 0L) return "offline"

        return try {
            val lastSeenDateTime = Instant.ofEpochMilli(lastSeen).atZone(zoneId)
            val lastSeenDate = lastSeenDateTime.toLocalDate()
            val today = now.atZone(zoneId).toLocalDate()
            val yesterday = today.minusDays(1)

            val timeStr = lastSeenDateTime.format(TIME_FORMATTER)

            when (lastSeenDate) {
                today -> "last seen today at $timeStr"
                yesterday -> "last seen yesterday at $timeStr"
                else -> {
                    val dateFormatter = if (lastSeenDate.year == today.year) DATE_FORMATTER_SHORT else DATE_FORMATTER_LONG
                    val dateStr = lastSeenDateTime.format(dateFormatter)
                    "last seen $dateStr at $timeStr"
                }
            }
        } catch (e: Exception) {
            "offline"
        }
    }

    /**
     * Formats the timestamp for an individual message bubble.
     * Expected: "1:30 PM"
     * Returns empty string for <= 0L or invalid timestamps.
     */
    fun formatMessageTime(
        timestamp: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestamp <= 0L) return ""

        return try {
            val dateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            dateTime.format(TIME_FORMATTER)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Formats conversation item timestamp for the Direct Chat list.
     * - Today: "1:30 PM"
     * - Yesterday: "Yesterday"
     * - Older: "15/09/2026"
     */
    fun formatConversationTime(
        timestamp: Long,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestamp <= 0L) return ""

        return try {
            val dateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            val date = dateTime.toLocalDate()
            val today = now.atZone(zoneId).toLocalDate()
            val yesterday = today.minusDays(1)

            when (date) {
                today -> dateTime.format(TIME_FORMATTER)
                yesterday -> "Yesterday"
                else -> dateTime.format(CONVERSATION_DATE_FORMATTER)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns date header text for date separators in the message list.
     * - Today: "Today"
     * - Yesterday: "Yesterday"
     * - Older: "15 September 2026"
     */
    fun getDateHeader(
        timestamp: Long,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestamp <= 0L) return ""

        return try {
            val dateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            val date = dateTime.toLocalDate()
            val today = now.atZone(zoneId).toLocalDate()
            val yesterday = today.minusDays(1)

            when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> dateTime.format(DATE_HEADER_FORMATTER)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns true if two timestamps occur on the same local calendar day.
     */
    fun isSameCalendarDay(
        timestamp1: Long,
        timestamp2: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (timestamp1 <= 0L || timestamp2 <= 0L) return false

        return try {
            val date1 = Instant.ofEpochMilli(timestamp1).atZone(zoneId).toLocalDate()
            val date2 = Instant.ofEpochMilli(timestamp2).atZone(zoneId).toLocalDate()
            date1 == date2
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formats the number of online members for a group chat.
     * Examples:
     * - 0 -> "0 members online"
     * - 1 -> "1 member online"
     * - 5 -> "5 members online"
     */
    fun formatGroupOnlineCount(count: Int): String {
        return if (count == 1) {
            "1 member online"
        } else {
            "$count members online"
        }
    }

    private val RECEIPT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.getDefault())
    private val RECEIPT_DATE_TIME_YEAR_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())

    /**
     * Formats receipt timestamp for Message Info (Read / Delivered timestamps).
     * - Today: "2:15 PM"
     * - Yesterday: "Yesterday, 2:15 PM"
     * - Same year: "21 September, 22:02"
     * - Older: "21 September 2025, 22:02"
     */
    fun formatReceiptDateTime(
        timestamp: Long?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestamp == null || timestamp <= 0L) return ""
        return try {
            val dateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            val date = dateTime.toLocalDate()
            val today = now.atZone(zoneId).toLocalDate()
            val yesterday = today.minusDays(1)

            when {
                date == today -> dateTime.format(TIME_FORMATTER)
                date == yesterday -> "Yesterday, ${dateTime.format(TIME_FORMATTER)}"
                date.year == today.year -> dateTime.format(RECEIPT_DATE_TIME_FORMATTER)
                else -> dateTime.format(RECEIPT_DATE_TIME_YEAR_FORMATTER)
            }
        } catch (e: Exception) {
            ""
        }
    }
}


