package com.kampusagi.android.core.time

import android.content.Context
import com.kampusagi.android.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Göreli zaman parçası: saf hesap (testlenir), metne çevirme ayrı. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class MinutesAgo(val minutes: Long) : RelativeTime
    data class HoursAgo(val hours: Long) : RelativeTime
    data class DaysAgo(val days: Long) : RelativeTime
    data class WeeksAgo(val weeks: Long) : RelativeTime
    data class Absolute(val instant: Instant) : RelativeTime
}

/** `then` gelecekteyse (saat farkı) "az önce" sayılır. 4 haftadan eskisi mutlak tarih olur. */
fun relativeTimeOf(then: Instant, now: Instant): RelativeTime {
    val elapsed = Duration.between(then, now)
    val minutes = elapsed.toMinutes()
    return when {
        minutes < 1 -> RelativeTime.JustNow
        minutes < 60 -> RelativeTime.MinutesAgo(minutes)
        elapsed.toHours() < 24 -> RelativeTime.HoursAgo(elapsed.toHours())
        elapsed.toDays() < 7 -> RelativeTime.DaysAgo(elapsed.toDays())
        elapsed.toDays() < 28 -> RelativeTime.WeeksAgo(elapsed.toDays() / 7)
        else -> RelativeTime.Absolute(then)
    }
}

private val TurkishLocale: Locale = Locale.forLanguageTag("tr-TR")

/** "az önce", "2 saat önce", "3 gün önce" — mutlak tarihlerde yerel kısa tarih ("14 Eyl 2026"). */
fun formatRelativeTime(context: Context, then: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
    when (val relative = relativeTimeOf(then, now)) {
        RelativeTime.JustNow -> context.getString(R.string.relative_just_now)
        is RelativeTime.MinutesAgo -> context.getString(R.string.relative_minutes_ago, relative.minutes)
        is RelativeTime.HoursAgo -> context.getString(R.string.relative_hours_ago, relative.hours)
        is RelativeTime.DaysAgo -> context.getString(R.string.relative_days_ago, relative.days)
        is RelativeTime.WeeksAgo -> context.getString(R.string.relative_weeks_ago, relative.weeks)
        is RelativeTime.Absolute ->
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(TurkishLocale).format(relative.instant.atZone(zone))
    }

/** "26 Eyl 2026 18:00" — yerel saat dilimi ve Türkçe kısa biçim. */
fun formatDateTime(context: Context, instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(TurkishLocale).format(instant.atZone(zone))

/** Sohbet balonu saati: "14:03" (yerel saat dilimi). */
fun formatClockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(TurkishLocale).format(instant.atZone(zone))
