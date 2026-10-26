// Co-authored-by: Gemini AI Agent
package com.android.sample.model.event

import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Utility for parsing iCalendar (.ics) format files into domain [Event] models.
 *
 * Supports line unfolding (RFC 5545), timezone extraction (`TZID`), custom duration parsing, and
 * category mapping for academic course schedules (e.g., IS-Academia).
 */
object IcsParser {

  private val ICS_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
  private val DEFAULT_ZONE = ZoneId.of("Europe/Zurich")

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
  ): List<Event> {
    val rawContent = inputStream.bufferedReader().use { it.readText() }
    // 1. Unfold lines according to RFC 5545 (CRLF/LF followed by space/tab)
    val unfoldedContent = rawContent.replace(Regex("\r?\n[ \t]"), "")
    val lines = unfoldedContent.lines()

    val events = mutableListOf<Event>()
    var currentBlock: MutableMap<String, MutableList<String>>? = null

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue

      when {
        trimmed == "BEGIN:VEVENT" -> {
          currentBlock = mutableMapOf()
        }
        trimmed == "END:VEVENT" -> {
          currentBlock?.let { block ->
            parseVEvent(block, ownerId, idGenerator)?.let { events.add(it) }
          }
          currentBlock = null
        }
        currentBlock != null -> {
          val colonIndex = trimmed.indexOf(':')
          if (colonIndex != -1) {
            val key = trimmed.substring(0, colonIndex).trim()
            val value = trimmed.substring(colonIndex + 1).trim()
            currentBlock.getOrPut(key) { mutableListOf() }.add(value)
          }
        }
      }
    }

    return events
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
      properties: Map<String, List<String>>,
      ownerId: String,
      idGenerator: (String?) -> String,
  ): Event? {
    val uid = properties.firstValue("UID")
    val summary = properties.firstValue("SUMMARY") ?: return null

    // 1. Parse Start Time & Zone
    val (dtStartKey, dtStartValues) =
        properties.entries.find { it.key.startsWith("DTSTART") } ?: return null
    val dtStartRaw = dtStartValues.firstOrNull() ?: return null

    val zoneId = extractZoneId(dtStartKey) ?: DEFAULT_ZONE
    val startsAt = parseIcsDateTime(dtStartRaw, zoneId) ?: return null

    // 2. Parse Duration or DTEND
    val durationRaw = properties.firstValue("DURATION")
    // Find DTEND even if it contains parameters like DTEND;TZID=Europe/Berlin
    val dtEndEntry = properties.entries.find { it.key.startsWith("DTEND") }
    val dtEndRaw = dtEndEntry?.value?.firstOrNull()
    val dtEndZoneId = dtEndEntry?.key?.let { extractZoneId(it) } ?: zoneId

    val endsAt =
        when {
          durationRaw != null -> startsAt.plus(Duration.parse(durationRaw))
          dtEndRaw != null ->
              parseIcsDateTime(dtEndRaw, dtEndZoneId) ?: startsAt.plus(Duration.ofHours(1))
          else -> startsAt.plus(Duration.ofHours(1))
        }

    // Validate basic constraints
    if (!endsAt.isAfter(startsAt) || Duration.between(startsAt, endsAt) > Event.MAX_DURATION) {
      return null
    }

    // 3. Extract Categories and Map to EventType
    val categories = properties.filterKeys { it.startsWith("CATEGORIES") }.values.flatten()
    val type = mapCategoryToEventType(categories)

    // 4. Metadata and Fields
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
    )
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
   * @param dtStartKey Property key string (e.g. `DTSTART;TZID=Europe/Zurich`).
   * @return Extracted [ZoneId], or [DEFAULT_ZONE] if unparseable or missing.
   */
  private fun extractZoneId(dtStartKey: String): ZoneId? {
    val match = Regex("TZID=([^;:]+)").find(dtStartKey)
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
      if (value.endsWith("Z")) {
        val clean = value.removeSuffix("Z")
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
  private fun Map<String, List<String>>.firstValue(key: String): String? {
    return this[key]?.firstOrNull()
  }
}
