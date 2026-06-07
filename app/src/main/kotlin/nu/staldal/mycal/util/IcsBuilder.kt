package nu.staldal.mycal.util

import android.text.Html
import nu.staldal.mycal.data.api.EventDto
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object IcsBuilder {
    private val rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val icalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val icalDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun buildIcs(event: EventDto): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//MyCal//MyCal Android//EN",
            "BEGIN:VEVENT",
            "UID:${event.id}@mycal",
        )
        if (event.allDay) {
            lines += "DTSTART;VALUE=DATE:${toIcalDate(event.startTime)}"
            lines += "DTEND;VALUE=DATE:${toIcalDate(event.endTime)}"
        } else {
            lines += "DTSTART:${toIcalDateTime(event.startTime)}"
            lines += "DTEND:${toIcalDateTime(event.endTime)}"
        }
        lines += "SUMMARY:${escape(event.title)}"
        if (event.description.isNotBlank()) {
            lines += "DESCRIPTION:${escape(htmlToPlainText(event.description))}"
        }
        if (event.location.isNotBlank()) lines += "LOCATION:${escape(event.location)}"
        if (event.url.isNotBlank()) lines += "URL:${escape(event.url)}"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n")
    }

    private fun toIcalDateTime(dateStr: String): String =
        try {
            ZonedDateTime.parse(dateStr, rfc3339Formatter)
                .withZoneSameInstant(ZoneId.of("UTC"))
                .format(icalDateTimeFormatter)
        } catch (e: DateTimeParseException) {
            dateStr
        }

    private fun toIcalDate(dateStr: String): String =
        DateUtils.parseToLocalDate(dateStr)?.format(icalDateFormatter) ?: dateStr

    private fun htmlToPlainText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()

    private fun escape(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\r", "\\n")
            .replace("\n", "\\n")
}
