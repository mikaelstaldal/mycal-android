package nu.staldal.mycal.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {
    private val rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val dateOnlyFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

    fun parseToLocalDateTime(dateStr: String): LocalDateTime? {
        return try {
            ZonedDateTime.parse(dateStr, rfc3339Formatter)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(dateStr, dateOnlyFormatter).atStartOfDay()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    fun parseToLocalDate(dateStr: String): LocalDate? {
        return parseToLocalDateTime(dateStr)?.toLocalDate()
    }

    fun formatDisplayDate(dateStr: String): String {
        return parseToLocalDateTime(dateStr)?.format(displayDateFormatter) ?: dateStr
    }

    fun formatDisplayTime(dateStr: String): String {
        return parseToLocalDateTime(dateStr)?.format(displayTimeFormatter) ?: ""
    }

    fun formatDisplayDateTime(dateStr: String): String {
        return parseToLocalDateTime(dateStr)?.format(displayDateTimeFormatter) ?: dateStr
    }

    fun toRfc3339(dateTime: LocalDateTime): String {
        return dateTime.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("UTC"))
            .format(rfc3339Formatter)
    }

    fun toDateOnly(date: LocalDate): String {
        return date.format(dateOnlyFormatter)
    }
}
