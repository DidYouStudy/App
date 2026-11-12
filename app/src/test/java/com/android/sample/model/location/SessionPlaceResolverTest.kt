// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [SessionPlaceResolver]. */
class SessionPlaceResolverTest {

  private fun room(name: String, latitude: Double = 46.52, longitude: Double = 6.56) =
      CampusLocation(
          name = name,
          zone = name.substringBefore(' '),
          type = CampusLocationType.LECTURE_ROOM,
          floor = 0,
          location = Location(latitude, longitude),
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val resolver = SessionPlaceResolver(listOf(aab, cm))

  @Test
  fun resolve_exactName_returnsRoom() {
    assertEquals(aab, resolver.resolve("AAB 0 26"))
  }

  @Test
  fun resolve_differentCase_returnsRoom() {
    assertEquals(cm, resolver.resolve("cm 1 121"))
  }

  @Test
  fun resolve_differentSpacing_returnsRoom() {
    assertEquals(cm, resolver.resolve("  CM1121 "))
    assertEquals(aab, resolver.resolve("A A B 0\t26"))
  }

  @Test
  fun resolve_null_returnsNull() {
    assertNull(resolver.resolve(null))
  }

  @Test
  fun resolve_blank_returnsNull() {
    assertNull(resolver.resolve(""))
    assertNull(resolver.resolve("   "))
  }

  @Test
  fun resolve_unknownRoom_returnsNull() {
    assertNull(resolver.resolve("XYZ 9 99"))
  }

  @Test
  fun resolve_partialText_returnsNull() {
    assertNull(resolver.resolve("AAB 0"))
    assertNull(resolver.resolve("AAB 0 26 near the stairs"))
  }

  @Test
  fun resolve_duplicateRoomNames_firstWins() {
    val first = room("BC 01", latitude = 46.51)
    val second = room("bc01", latitude = 46.53)
    assertEquals(first, SessionPlaceResolver(listOf(first, second)).resolve("BC 01"))
  }

  @Test
  fun resolve_noRooms_returnsNull() {
    assertNull(SessionPlaceResolver(emptyList()).resolve("AAB 0 26"))
  }

  @Test
  fun resolve_roomWithBlankName_isIgnored() {
    val blank = room("   ")
    assertNull(SessionPlaceResolver(listOf(blank)).resolve("  "))
  }
}
