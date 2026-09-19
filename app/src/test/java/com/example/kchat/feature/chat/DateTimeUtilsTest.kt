package com.example.kchat.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DateTimeUtilsTest {

    private val zoneId = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 9, 16, 15, 0, 0, 0, zoneId).toInstant()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun formatPresence_whenOnline_returnsOnline() {
        val result = DateTimeUtils.formatPresence(isOnline = true, lastSeen = null, now = now, zoneId = zoneId)
        assertEquals("online", result)

        val resultWithTimestamp = DateTimeUtils.formatPresence(isOnline = true, lastSeen = 123456789L, now = now, zoneId = zoneId)
        assertEquals("online", resultWithTimestamp)
    }

    @Test
    fun formatPresence_whenOfflineAndNullOrInvalidLastSeen_returnsOffline() {
        assertEquals("offline", DateTimeUtils.formatPresence(isOnline = false, lastSeen = null, now = now, zoneId = zoneId))
        assertEquals("offline", DateTimeUtils.formatPresence(isOnline = false, lastSeen = 0L, now = now, zoneId = zoneId))
        assertEquals("offline", DateTimeUtils.formatPresence(isOnline = false, lastSeen = -100L, now = now, zoneId = zoneId))
    }

    @Test
    fun formatPresence_whenOfflineToday_returnsTodayAtTime() {
        val lastSeen = ZonedDateTime.of(2026, 9, 16, 13, 30, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = DateTimeUtils.formatPresence(isOnline = false, lastSeen = lastSeen, now = now, zoneId = zoneId)
        assertEquals("last seen today at 1:30 PM", result)
    }

    @Test
    fun formatPresence_whenOfflineYesterday_returnsYesterdayAtTime() {
        val lastSeen = ZonedDateTime.of(2026, 9, 15, 20, 45, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = DateTimeUtils.formatPresence(isOnline = false, lastSeen = lastSeen, now = now, zoneId = zoneId)
        assertEquals("last seen yesterday at 8:45 PM", result)
    }

    @Test
    fun formatPresence_whenOfflineOlderSameYear_returnsDateAtTime() {
        val lastSeen = ZonedDateTime.of(2026, 9, 10, 7, 20, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = DateTimeUtils.formatPresence(isOnline = false, lastSeen = lastSeen, now = now, zoneId = zoneId)
        assertEquals("last seen 10 Sep at 7:20 AM", result)
    }

    @Test
    fun formatPresence_whenOfflinePreviousYear_returnsDateWithYearAtTime() {
        val lastSeen = ZonedDateTime.of(2025, 12, 25, 9, 15, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = DateTimeUtils.formatPresence(isOnline = false, lastSeen = lastSeen, now = now, zoneId = zoneId)
        assertEquals("last seen 25 Dec 2025 at 9:15 AM", result)
    }

    @Test
    fun formatMessageTime_validAndInvalidTimestamps() {
        val timestamp = ZonedDateTime.of(2026, 9, 16, 13, 30, 0, 0, zoneId).toInstant().toEpochMilli()
        assertEquals("1:30 PM", DateTimeUtils.formatMessageTime(timestamp, zoneId = zoneId))
        assertEquals("", DateTimeUtils.formatMessageTime(0L, zoneId = zoneId))
        assertEquals("", DateTimeUtils.formatMessageTime(-50L, zoneId = zoneId))
    }

    @Test
    fun formatConversationTime_todayYesterdayOlderAndInvalid() {
        val todayTimestamp = ZonedDateTime.of(2026, 9, 16, 13, 30, 0, 0, zoneId).toInstant().toEpochMilli()
        val yesterdayTimestamp = ZonedDateTime.of(2026, 9, 15, 8, 15, 0, 0, zoneId).toInstant().toEpochMilli()
        val olderTimestamp = ZonedDateTime.of(2026, 9, 10, 11, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        assertEquals("1:30 PM", DateTimeUtils.formatConversationTime(todayTimestamp, now = now, zoneId = zoneId))
        assertEquals("Yesterday", DateTimeUtils.formatConversationTime(yesterdayTimestamp, now = now, zoneId = zoneId))
        assertEquals("10/09/2026", DateTimeUtils.formatConversationTime(olderTimestamp, now = now, zoneId = zoneId))
        assertEquals("", DateTimeUtils.formatConversationTime(0L, now = now, zoneId = zoneId))
        assertEquals("", DateTimeUtils.formatConversationTime(-1L, now = now, zoneId = zoneId))
    }

    @Test
    fun getDateHeader_todayYesterdayOlderAndInvalid() {
        val todayTimestamp = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val yesterdayTimestamp = ZonedDateTime.of(2026, 9, 15, 23, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val olderTimestamp = ZonedDateTime.of(2026, 9, 10, 14, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        assertEquals("Today", DateTimeUtils.getDateHeader(todayTimestamp, now = now, zoneId = zoneId))
        assertEquals("Yesterday", DateTimeUtils.getDateHeader(yesterdayTimestamp, now = now, zoneId = zoneId))
        assertEquals("10 September 2026", DateTimeUtils.getDateHeader(olderTimestamp, now = now, zoneId = zoneId))
        assertEquals("", DateTimeUtils.getDateHeader(0L, now = now, zoneId = zoneId))
        assertEquals("", DateTimeUtils.getDateHeader(-100L, now = now, zoneId = zoneId))
    }

    @Test
    fun isSameCalendarDay_comparesCorrectly() {
        val time1 = ZonedDateTime.of(2026, 9, 16, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val time2 = ZonedDateTime.of(2026, 9, 16, 22, 30, 0, 0, zoneId).toInstant().toEpochMilli()
        val time3 = ZonedDateTime.of(2026, 9, 15, 23, 59, 0, 0, zoneId).toInstant().toEpochMilli()

        assertTrue(DateTimeUtils.isSameCalendarDay(time1, time2, zoneId))
        assertFalse(DateTimeUtils.isSameCalendarDay(time1, time3, zoneId))
        assertFalse(DateTimeUtils.isSameCalendarDay(0L, time1, zoneId))
        assertFalse(DateTimeUtils.isSameCalendarDay(time1, -5L, zoneId))
        assertFalse(DateTimeUtils.isSameCalendarDay(0L, 0L, zoneId))
    }
}
