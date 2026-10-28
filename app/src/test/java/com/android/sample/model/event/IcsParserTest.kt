// Co-authored-by: Gemini AI Agent
package com.android.sample.model.event

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

  private val defaultOwnerId = "test_user_42"

  private fun stringToStream(content: String): ByteArrayInputStream {
    return ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
  }

  @Test
  fun parsesValidIsAcademiaCourseEventWithDurationAndZurichTimezone() {
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:horaire_5224679874@is-academia
        DTSTAMP:20261010T113334
        SUMMARY:Modèles stochastiques pour les communications
        DESCRIPTION:Thiran Patrick
        DTSTART;TZID=Europe/Berlin:20261013T101500
        DURATION:PT105M
        CATEGORIES:Horaires enseignés FBM
        LOCATION:CM3
        CATEGORIES:Cours
        CATEGORIES:
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    assertEquals(1, events.size)
    val event = events.first()

    assertEquals("horaire_5224679874@is-academia", event.id)
    assertEquals("horaire_5224679874@is-academia", event.importKey)
    assertEquals(defaultOwnerId, event.ownerId)
    assertEquals(EventType.COURSE, event.type)
    assertEquals("Modèles stochastiques pour les communications", event.subject)
    assertEquals("CM3", event.location)
    assertEquals("Thiran Patrick", event.description)

    // Europe/Berlin on Oct 13, 2026 is CEST (UTC+2) -> 10:15 local is 08:15 UTC
    val expectedStart = Instant.parse("2026-10-13T08:15:00Z")
    val expectedEnd = expectedStart.plus(Duration.ofMinutes(105)) // + 1h45m -> 10:00 UTC

    assertEquals(expectedStart, event.startsAt)
    assertEquals(expectedEnd, event.endsAt)
    assertTrue(event.endsAt.isAfter(event.startsAt))

    // DTSTAMP is parsed as UTC
    assertEquals(Instant.parse("2026-10-10T11:33:34Z"), event.createdAt)
    assertEquals(event.createdAt, event.updatedAt)
    assertTrue(event.tasks.isEmpty())
  }

  @Test
  fun mapsAllAcademicCategoriesToCorrectEventTypes() {
    val categoriesAndExpected =
        listOf(
            "Exercices" to EventType.EXERCISE_SESSION,
            "Labo" to EventType.LAB,
            "Projet" to EventType.PROJECT,
            "Examen" to EventType.EXAM,
            "Cours" to EventType.COURSE,
            "UnknownOrOther" to EventType.OTHER,
        )

    categoriesAndExpected.forEachIndexed { index, (cat, expectedType) ->
      val ics =
          """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:test_uid_$index
            SUMMARY:Course $index
            DTSTART;TZID=Europe/Berlin:20261013T080000
            DURATION:PT45M
            CATEGORIES:$cat
            END:VEVENT
            END:VCALENDAR
            """
              .trimIndent()

      val parsed = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
      assertEquals("Category '$cat' should map to $expectedType", expectedType, parsed.first().type)
    }
  }

  @Test
  fun unfoldsWrappedIcalendarLinesCorrectly() {
    // RFC 5545 specifies long lines wrapped with CRLF + whitespace
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:unfold_uid_1
        SUMMARY:The software enterprise - from 
         ideas to products
        DESCRIPTION:Prof. Candea George, 
         Assistant John Doe
        DTSTART;TZID=Europe/Berlin:20261016T091500
        DURATION:PT60M
        CATEGORIES:Projet
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    assertEquals(1, events.size)
    assertEquals("The software enterprise - from ideas to products", events.first().subject)
    assertEquals("Prof. Candea George, Assistant John Doe", events.first().description)
  }

  @Test
  fun fallsBackToDefaultOneHourDurationWhenNeitherDurationNorDtEndSpecified() {
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:no_duration_uid
        SUMMARY:Lecture without explicit end
        DTSTART;TZID=Europe/Berlin:20261015T140000
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    assertEquals(1, events.size)

    val event = events.first()
    assertEquals(Duration.ofHours(1), Duration.between(event.startsAt, event.endsAt))
  }

  @Test
  fun parsesExplicitDtEndInsteadOfDurationWhenProvided() {
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:dtend_uid
        SUMMARY:Exam session
        DTSTART;TZID=Europe/Berlin:20261020T100000
        DTEND;TZID=Europe/Berlin:20261020T123000
        CATEGORIES:Examen
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    assertEquals(1, events.size)
    assertEquals(
        Duration.ofMinutes(150),
        Duration.between(events.first().startsAt, events.first().endsAt),
    )
  }

  @Test
  fun discardsEventsWithViolatedInvariants() {
    // 1. Duration exceeds MAX_DURATION (24 hours)
    val tooLongIcs =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:invalid_too_long
        SUMMARY:Super long marathon
        DTSTART;TZID=Europe/Berlin:20261013T080000
        DURATION:PT25H
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    // 2. Negative duration (DTEND before DTSTART)
    val invertedTimeIcs =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:invalid_inverted
        SUMMARY:Time travel session
        DTSTART;TZID=Europe/Berlin:20261013T120000
        DTEND;TZID=Europe/Berlin:20261013T100000
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    // 3. Missing SUMMARY (required for non-OTHER events)
    val missingSummaryIcs =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:missing_summary
        DTSTART;TZID=Europe/Berlin:20261013T120000
        DURATION:PT60M
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    assertTrue(IcsParser.parseCalendar(stringToStream(tooLongIcs), defaultOwnerId).isEmpty())
    assertTrue(IcsParser.parseCalendar(stringToStream(invertedTimeIcs), defaultOwnerId).isEmpty())
    assertTrue(IcsParser.parseCalendar(stringToStream(missingSummaryIcs), defaultOwnerId).isEmpty())
  }

  @Test
  fun customIdGeneratorIsInvokedWhenProvided() {
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:original_uid_123
        SUMMARY:Lab session
        DTSTART;TZID=Europe/Berlin:20261013T140000
        DURATION:PT45M
        CATEGORIES:Labo
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events =
        IcsParser.parseCalendar(
            stringToStream(ics),
            defaultOwnerId,
            idGenerator = { rawUid -> "custom_prefix_$rawUid" },
        )

    assertEquals("custom_prefix_original_uid_123", events.first().id)
  }

  @Test
  fun parsesUtcTimestampWithTrailingZSuffixCorrectly() {
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:utc_event_123
        SUMMARY:UTC Timed Lecture
        DTSTART:20261013T080000Z
        DTEND:20261013T093000Z
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    assertEquals(1, events.size)
    val event = events.first()
    assertEquals(Instant.parse("2026-10-13T08:00:00Z"), event.startsAt)
    assertEquals(Instant.parse("2026-10-13T09:30:00Z"), event.endsAt)
  }

  @Test
  fun fallsBackToDefaultZoneWhenTzidIsInvalid() {
    // Passes an invalid time zone to trigger the catch block inside extractZoneId
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:invalid_tz_uid
        SUMMARY:Unknown Zone Event
        DTSTART;TZID=Invalid/Unknown_Zone:20261013T101500
        DURATION:PT45M
        CATEGORIES:Exercices
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    assertEquals(1, events.size)
    // When using DEFAULT_ZONE (Europe/Zurich), 10:15 CEST corresponds to 08:15 UTC
    assertEquals(Instant.parse("2026-10-13T08:15:00Z"), events.first().startsAt)
  }

  @Test
  fun skipsEventWhenDateTimeStringIsMalformedToTriggerParseCatchBlock() {
    // Passes unparseable malformed text in DTSTART to trigger the catch block in parseIcsDateTime
    val ics =
        """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:malformed_date_uid
        SUMMARY:Corrupted Date Event
        DTSTART;TZID=Europe/Berlin:NOT_A_VALID_DATE_TIME
        DURATION:PT45M
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    // parseIcsDateTime catches the exception, returns null, and parseVEvent discards the event
    assertTrue(events.isEmpty())
  }

  @Test
  fun discardsEventWhenSummaryIsPresentButEmptyForNonOtherType() {
    // Matches blank-summary.ics
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:blank_summary
        SUMMARY:
        DURATION:PT60M
        DTSTART;TZID=Europe/Berlin:20261013T101500
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    // Non-OTHER events require a non-blank subject; event must be gracefully dropped
    assertTrue(events.isEmpty())
  }

  @Test
  fun parsesSummaryWithParametersCorrectly() {
    // Matches summary-with-param.ics
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:param_summary
        SUMMARY;LANGUAGE=fr:Algebra
        DURATION:PT60M
        DTSTART;TZID=Europe/Berlin:20261013T101500
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    assertEquals(1, events.size)
    assertEquals("Algebra", events.first().subject)
  }

  @Test
  fun handlesWeekDurationGracefullyWithoutCrashing() {
    // Matches week-duration.ics (P1W exceeds 24-hour MAX_DURATION)
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:week_duration
        SUMMARY:Algebra
        DURATION:P1W
        DTSTART;TZID=Europe/Berlin:20261013T101500
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    // Does not throw DateTimeParseException; properly rejected by MAX_DURATION invariant
    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)
    assertTrue(events.isEmpty())
  }

  @Test
  fun fallsBackToOneHourWhenDurationIsCompletelyUnparseable() {
    // Tests the 'else -> null' branch in parseSafeDuration
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:bad_duration_fallback
        SUMMARY:Fallback Course
        DURATION:GARBAGE_NOT_A_DURATION
        DTSTART;TZID=Europe/Berlin:20261013T101500
        CATEGORIES:Cours
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    // Falls back safely to 1 hour instead of crashing
    assertEquals(1, events.size)
    val event = events.first()
    assertEquals(Duration.ofHours(1), Duration.between(event.startsAt, event.endsAt))
  }

  @Test
  fun allowsBlankSummaryWhenEventTypeIsOther() {
    // Tests the (summary == null && type == EventType.OTHER) branch
    val ics =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:other_without_summary
        SUMMARY:
        DURATION:PT45M
        DTSTART;TZID=Europe/Berlin:20261013T101500
        CATEGORIES:UnknownCategory
        END:VEVENT
        END:VCALENDAR
        """
            .trimIndent()

    val events = IcsParser.parseCalendar(stringToStream(ics), defaultOwnerId)

    assertEquals(1, events.size)
    assertEquals(EventType.OTHER, events.first().type)
    assertEquals(null, events.first().subject)
  }
}
