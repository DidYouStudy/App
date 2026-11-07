// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

/**
 * Read-only access to the campus rooms bundled with the app.
 *
 * Failures (for example unreadable data) are returned as [Result.failure]. A search that matches
 * nothing is not a failure: it returns a successful empty list. Callers depend on this interface,
 * never on the implementation.
 */
interface CampusLocationRepository {

  /** Returns every known campus location. */
  suspend fun getAllLocations(): Result<List<CampusLocation>>

  /**
   * Returns the locations that match [filter]. See [CampusLocationFilter] for how each criterion is
   * matched. An empty filter returns every location, and a filter that matches nothing returns an
   * empty list.
   *
   * Note: if shortcuts such as `findByRoom`, `findByZone` or `findByType` are added later, they
   * would be one-line calls to this function with every other criterion left empty. They would not
   * replace [CampusLocationFilter], which stays the way to combine criteria. Each call is
   * independent: a shortcut never narrows the result of a previous call.
   */
  suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>>

  /**
   * Returns the distinct building codes of all locations, sorted alphabetically, for example to
   * fill a building picker. Every returned code is accepted by [CampusLocationFilter.zone].
   */
  suspend fun getZones(): Result<List<String>>
}
