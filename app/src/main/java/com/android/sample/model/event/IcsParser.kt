// Co-authored-by: Gemini AI Agent
package com.android.sample.model.event

import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Outcome of parsing a calendar: the valid [events] and how many `VEVENT`s were [skipped]. */
data class ParseResult(val events: List<Event>, val skipped: Int)

/**
 * Utility for parsing iCalendar (.ics) format files into domain [Event] models.
 *
 * Supports line unfolding (RFC 5545), timezone extraction (`TZID`), custom duration parsing, and
 * category mapping for academic course schedules (e.g., IS-Academia).
 */
object IcsParser {

  private val ICS_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
  private val DEFAULT_ZONE = ZoneId.of("Europe/Zurich")
  private val WEEK_DURATION_REGEX = Regex("^([+-]?)P([0-9]+)W$", RegexOption.IGNORE_CASE)

  /**
   * Parses an iCalendar (.ics) input stream and maps its `VEVENT` entries to [Event] instances.
   *
   * Unfolds folded lines according to RFC 5545, parses VEVENT property blocks, and maps academic
   * categories to [EventType].
   *
   * @param inputStream The raw input stream containing .ics data.
   * @param ownerId The user ID to assign to each parsed event.
   * @param idGenerator Lambda producing stable or random unique IDs for each event.
   * @return List of parsed and validated [Event] instances.
   */
  fun parseCalendar(
      inputStream: InputStream,
      ownerId: String,
      idGenerator: (veventUid: String?) -> String = { it ?: UUID.randomUUID().toString() },
  ): List<Event> = parseCalendarWithReport(inputStream, ownerId, idGenerator).events

  /**
   * Like [parseCalendar], but also counts the `VEVENT` blocks that were dropped because they were
   * malformed, so callers can tell the user that part of the file was ignored.
   */
  fun parseCalendarWithReport(
      inputStream: InputStream,
      ownerId: String,
      idGenerator: (veventUid: String?) -> String = { it ?: UUID.randomUUID().toString() },
  ): ParseResult {
    val rawContent = inputStream.bufferedReader().use { it.readText() }
    // 1. Unfold lines according to RFC 5545 (CRLF/LF followed by space/tab)
    val unfoldedContent = rawContent.replace(Regex("\r?\n[ \t]"), "")
    val lines = unfoldedContent.lines()

    val events = mutableListOf<Event>()
    var skipped = 0
    // Store property name -> list of (rawKeyWithParams, value) pairs
    var currentBlock: MutableMap<String, MutableList<Pair<String, String>>>? = null

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue

      when {
        trimmed == "BEGIN:VEVENT" -> {
          // A block still open here was never closed by END:VEVENT: it is lost, so count it.
          if (currentBlock != null) skipped++
          currentBlock = mutableMapOf()
        }
        trimmed == "END:VEVENT" -> {
          currentBlock?.let { block ->
            val event = parseVEvent(block, ownerId, idGenerator)
            if (event != null) events.add(event) else skipped++
          }
          currentBlock = null
        }
        currentBlock != null -> addProperty(currentBlock, trimmed)
      }
    }

    // The file ended inside a VEVENT (truncated file): that event is dropped, so report it too.
    if (currentBlock != null) skipped++

    return ParseResult(events, skipped)
  }

  /**
   * Adds the `NAME;PARAMS:value` line [line] to [block], keyed by the upper-cased property name
   * without its parameters. Lines without a colon are ignored.
   */
  private fun addProperty(
      block: MutableMap<String, MutableList<Pair<String, String>>>,
      line: String,
  ) {
    val colonIndex = line.indexOf(':')
    if (colonIndex == -1) return
    val rawKey = line.substring(0, colonIndex).trim()
    val value = line.substring(colonIndex + 1).trim()
    // Normalize property name by stripping parameters after ';'
    val propName = rawKey.substringBefore(';').uppercase()
    block.getOrPut(propName) { mutableListOf() }.add(rawKey to value)
  }

  /**
   * Parses a single `VEVENT` property map into an [Event] domain model.
   *
   * @param properties Map of property keys to lists of values extracted from a `VEVENT` block.
   * @param ownerId User ID owning the event.
   * @param idGenerator Lambda generating the event ID.
   * @return The parsed [Event], or `null` if required fields are missing or invalid.
   */
  private fun parseVEvent(
      properties: Map<String, List<Pair<String, String>>>,
      ownerId: String,
      idGenerator: (String?) -> String,
  ): Event? {
    val uid = properties.firstValue("UID")

    // 1. Validate Subject / Summary (reject blank strings)
    val summary = properties.firstValue("SUMMARY")?.takeIf { it.isNotBlank() }

    // 2. Extract Categories and Map to EventType first
    val categories = properties["CATEGORIES"].orEmpty().map { it.second }
    val type = mapCategoryToEventType(categories)

    // Enforce Event domain invariant: non-OTHER types require a non-blank subject
    if (summary == null && type != EventType.OTHER) {
      return null
    }

    // 3. Parse Start Time & Zone
    val dtStartEntry = properties["DTSTART"]?.firstOrNull() ?: return null
    val (dtStartRawKey, dtStartRawVal) = dtStartEntry
    val zoneId = extractZoneId(dtStartRawKey) ?: DEFAULT_ZONE
    val startsAt = parseIcsDateTime(dtStartRawVal, zoneId) ?: return null

    // 4. Parse Duration or DTEND with safe fallback
    val durationRaw = properties.firstValue("DURATION")
    val dtEndEntry = properties["DTEND"]?.firstOrNull()

    val parsedDuration = durationRaw?.let { parseSafeDuration(it) }

    val endsAt =
        when {
          parsedDuration != null -> startsAt.plus(parsedDuration)
          dtEndEntry != null -> {
            val dtEndZoneId = extractZoneId(dtEndEntry.first) ?: zoneId
            parseIcsDateTime(dtEndEntry.second, dtEndZoneId) ?: startsAt.plus(Duration.ofHours(1))
          }
          else -> startsAt.plus(Duration.ofHours(1))
        }

    // Validate Duration constraints: must end after start, and duration <= MAX_DURATION (24h)
    if (!endsAt.isAfter(startsAt) || Duration.between(startsAt, endsAt) > Event.MAX_DURATION) {
      return null
    }

    // 5. Metadata and Fields
    val location = properties.firstValue("LOCATION")?.takeIf { it.isNotBlank() }
    val description = properties.firstValue("DESCRIPTION")?.takeIf { it.isNotBlank() }

    val dtstampRaw = properties.firstValue("DTSTAMP")
    val createdAt = dtstampRaw?.let { parseIcsDateTime(it, ZoneId.of("UTC")) } ?: Instant.now()

    return Event(
        id = idGenerator(uid),
        ownerId = ownerId,
        type = type,
        subject = summary,
        startsAt = startsAt,
        endsAt = endsAt,
        createdAt = createdAt,
        updatedAt = createdAt,
        location = location,
        description = description,
        tasks = emptyList(),
        importKey = uid?.takeIf { it.isNotBlank() },
    )
  }

  /**
   * Safely parses an RFC 5545 duration string into a [Duration].
   *
   * Supports standard ISO-8601 duration representations (e.g., `PT105M`, `PT1H30M`, `P1D`) via
   * [Duration.parse], as well as RFC 5545 week-based durations (e.g., `P1W`, `P2W`) which standard
   * Java [Duration] parsers do not natively handle.
   *
   * @param raw The raw duration string extracted from the `DURATION` property.
   * @return The parsed [Duration], or `null` if the string cannot be parsed or represents an
   *   invalid value.
   */
  private fun parseSafeDuration(raw: String): Duration? {
    return try {
      Duration.parse(raw)
    } catch (e: Exception) {
      // Check for week notation (e.g., P1W, P2W)
      val weekMatch = WEEK_DURATION_REGEX.matchEntire(raw.trim())
      if (weekMatch != null) {
        val weeks = weekMatch.groupValues[2].toLongOrNull() ?: return null
        Duration.ofDays(weeks * 7)
      } else {
        null
      }
    }
  }

  /**
   * Maps iCalendar category strings to an [EventType] domain enum.
   *
   * @param categories List of category strings from the VEVENT block.
   * @return Corresponding [EventType], defaulting to [EventType.OTHER].
   */
  private fun mapCategoryToEventType(categories: List<String>): EventType {
    for (cat in categories) {
      when (cat.trim().lowercase()) {
        "cours" -> return EventType.COURSE
        "exercices" -> return EventType.EXERCISE_SESSION
        "labo" -> return EventType.LAB
        "projet" -> return EventType.PROJECT
        "examen" -> return EventType.EXAM
      }
    }
    return EventType.OTHER
  }

  /**
   * Extracts the timezone ID (`TZID`) parameter from a property key string.
   *
   * @param rawKey Property key string (e.g. `DTSTART;TZID=Europe/Zurich`).
   * @return Extracted [ZoneId], or [DEFAULT_ZONE] if unparseable or missing.
   */
  private fun extractZoneId(rawKey: String): ZoneId? {
    val match = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(rawKey)
    return match?.groupValues?.get(1)?.let {
      try {
        ZoneId.of(it)
      } catch (e: Exception) {
        DEFAULT_ZONE
      }
    }
  }

  /**
   * Parses an iCalendar date-time string into an [Instant].
   *
   * Handles UTC timestamps ending in `Z` as well as localized date-time strings.
   *
   * @param value Raw date-time string (e.g., `20261015T143000Z` or `20261015T143000`).
   * @param zoneId Timezone to apply when no UTC suffix `Z` is present.
   * @return Parsed [Instant], or `null` if parsing fails.
   */
  private fun parseIcsDateTime(value: String, zoneId: ZoneId): Instant? {
    return try {
      if (value.endsWith("Z", ignoreCase = true)) {
        val clean = value.dropLast(1)
        LocalDateTime.parse(clean, ICS_DATE_TIME_FORMATTER).atZone(ZoneId.of("UTC")).toInstant()
      } else {
        LocalDateTime.parse(value, ICS_DATE_TIME_FORMATTER).atZone(zoneId).toInstant()
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Helper extension function to retrieve the first value for a given key from a property map.
   *
   * @param key Property key to look up.
   * @return First value string for the key, or `null` if absent.
   */
  private fun Map<String, List<Pair<String, String>>>.firstValue(key: String): String? {
    return this[key]?.firstOrNull()?.second
  }
}
