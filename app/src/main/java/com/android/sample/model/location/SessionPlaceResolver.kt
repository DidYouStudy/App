// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

/**
 * Finds the campus room a group session takes place in, from the free-text location typed by its
 * creator.
 *
 * A session location is only an optional string, so the match is deliberately strict: the text must
 * equal a room name, ignoring case and whitespace ("aab026" finds "AAB 0 26"). Partial text such as
 * "AAB 026 near the stairs" does not match, because a wrong marker is worse than no marker.
 *
 * Build one resolver per list of rooms and reuse it for every session.
 *
 * Not used by the app yet: it is meant for when the map is wired to the group sessions ViewModel,
 * which would build one resolver from the rooms of the campus location repository and use it to
 * place the screen's sessions on the map (see `groupByRoom`).
 *
 * @param rooms Campus rooms the text can resolve to. If two rooms normalize to the same name, the
 *   first one in the list wins.
 */
class SessionPlaceResolver(rooms: List<CampusLocation>) {

  private val roomsByKey: Map<String, CampusLocation> = buildMap {
    for (room in rooms) {
      val key = normalize(room.name)
      if (key.isNotEmpty()) putIfAbsent(key, room)
    }
  }

  /**
   * Returns the room named by [locationText], or `null` when the text is missing, blank or does not
   * match any room.
   */
  fun resolve(locationText: String?): CampusLocation? {
    val key = normalize(locationText ?: return null)
    return if (key.isEmpty()) null else roomsByKey[key]
  }

  private fun normalize(text: String): String = text.filterNot(Char::isWhitespace).lowercase()
}
