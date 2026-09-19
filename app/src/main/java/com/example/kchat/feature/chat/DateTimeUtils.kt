package com.example.kchat.feature.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

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

            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val timeStr = lastSeenDateTime.format(timeFormatter)

            when (lastSeenDate) {
                today -> "last seen today at $timeStr"
                yesterday -> "last seen yesterday at $timeStr"
                else -> {
                    val datePattern = if (lastSeenDate.year == today.year) "d MMM" else "d MMM yyyy"
                    val dateFormatter = DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())
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
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            dateTime.format(timeFormatter)
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
                today -> {
                    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                    dateTime.format(timeFormatter)
                }
                yesterday -> "Yesterday"
                else -> {
                    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                    dateTime.format(dateFormatter)
                }
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
                else -> {
                    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
                    dateTime.format(dateFormatter)
                }
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
}
