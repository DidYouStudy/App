// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [CampusLocationRepository] backed by the `campus_locations.json` file bundled with the app.
 *
 * The file is read and parsed once, on [ioDispatcher], the first time a function is called. The
 * parsed list is then kept in memory, so later calls are cheap. A failed load is not cached: the
 * next call tries again.
 *
 * @param readJson Returns the full text of the JSON file. It is injected so unit tests do not need
 *   an Android `Context`; the app passes a reader of the raw resource.
 * @param ioDispatcher Dispatcher used to read and parse the file.
 */
class LocalCampusLocationRepository(
    private val readJson: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CampusLocationRepository {

  /** Guards [cache] so concurrent first calls load the file only once. */
  private val loadMutex = Mutex()

  /** The parsed locations, or `null` until the first successful load. */
  @Volatile private var cache: List<CampusLocation>? = null

  override suspend fun getAllLocations(): Result<List<CampusLocation>> = load()

  override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
      load().map { all -> applyFilter(all, filter) }

  override suspend fun getZones(): Result<List<String>> =
      load().map { all -> all.map { it.zone }.distinct().sorted() }

  /**
   * Returns the cached locations, loading and parsing the file first if needed. Any failure
   * (unreadable file, malformed JSON, invalid coordinates) is returned as [Result.failure];
   * cancellation is rethrown so the caller's coroutine still stops.
   */
  private suspend fun load(): Result<List<CampusLocation>> {
    cache?.let {
      return Result.success(it)
    }
    return loadMutex.withLock {
      // Another caller may have finished loading while this one waited for the lock.
      cache?.let {
        return@withLock Result.success(it)
      }
      try {
        val parsed = withContext(ioDispatcher) { parse(readJson()) }
        cache = parsed
        Result.success(parsed)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  private companion object {
    /** Unknown keys are ignored so the data file can gain fields without breaking old apps. */
    val json = Json { ignoreUnknownKeys = true }

    /** Parses the JSON text into domain locations. Throws if the text or a value is invalid. */
    fun parse(text: String): List<CampusLocation> =
        json.decodeFromString<List<CampusLocationJson>>(text).map { it.toDomain() }

    /**
     * Keeps the locations that match every criterion set in [filter], then puts exact name matches
     * first. The sort is stable, so every other location keeps the order of the data file.
     */
    fun applyFilter(
        locations: List<CampusLocation>,
        filter: CampusLocationFilter,
    ): List<CampusLocation> {
      val query = filter.query?.let(::normalizeName).orEmpty()
      val matches = locations.filter { location ->
        (filter.zone == null || location.zone.equals(filter.zone, ignoreCase = true)) &&
            (filter.types.isEmpty() || location.type in filter.types) &&
            (query.isEmpty() || normalizeName(location.name).contains(query))
      }
      if (query.isEmpty()) return matches
      // false sorts before true, so exact matches (e.g. "BC 01" for "BC 01") come first.
      return matches.sortedBy { normalizeName(it.name) != query }
    }

    /** Lowercases [text] and removes all whitespace, so "CM 1 121" and "cm1121" compare equal. */
    fun normalizeName(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()
  }
}

/** Shape of one entry of `campus_locations.json`; only used for parsing. */
@Serializable
private class CampusLocationJson(
    val name: String,
    val zone: String,
    val type: String? = null,
    val floor: Int? = null,
    val latitude: Double,
    val longitude: Double,
) {
  /** Maps the file entry to the domain model; an unrecognized type becomes UNKNOWN. */
  fun toDomain(): CampusLocation =
      CampusLocation(
          name = name,
          zone = zone,
          type = CampusLocationType.fromString(type),
          floor = floor,
          location = Location(latitude, longitude),
      )
}
