// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

import org.junit.Assert
import org.junit.Test

/** Unit tests for [CampusLocationType]. */
class CampusLocationTypeTest {

  @Test
  fun fromString_knownLabels_returnsExpectedType() {
    Assert.assertEquals(
        CampusLocationType.SALLE_DE_COURS,
        CampusLocationType.fromString("SALLE DE COURS"),
    )
    Assert.assertEquals(CampusLocationType.SALLE_TP, CampusLocationType.fromString("SALLE TP"))
    Assert.assertEquals(CampusLocationType.AUDITOIRE, CampusLocationType.fromString("AUDITOIRE"))
    Assert.assertEquals(
        CampusLocationType.CONFERENCES,
        CampusLocationType.fromString("CONFERENCES"),
    )
    Assert.assertEquals(CampusLocationType.TUTORAT, CampusLocationType.fromString("TUTORAT"))
    Assert.assertEquals(
        CampusLocationType.CONF_MULTIMEDIA,
        CampusLocationType.fromString("CONF MULTIMEDIA"),
    )
    Assert.assertEquals(
        CampusLocationType.BIBLIOTHEQUE,
        CampusLocationType.fromString("BIBLIOTHEQUE"),
    )
    Assert.assertEquals(CampusLocationType.REUNION, CampusLocationType.fromString("REUNION"))
    Assert.assertEquals(CampusLocationType.LABO, CampusLocationType.fromString("LABO"))
  }

  @Test
  fun fromString_caseInsensitiveAndWhitespace_returnsExpectedType() {
    Assert.assertEquals(
        CampusLocationType.SALLE_DE_COURS,
        CampusLocationType.fromString("  salle de cours "),
    )
    Assert.assertEquals(CampusLocationType.LABO, CampusLocationType.fromString("labo"))
    Assert.assertEquals(
        CampusLocationType.BIBLIOTHEQUE,
        CampusLocationType.fromString("Bibliotheque"),
    )
    Assert.assertEquals(
        CampusLocationType.SALLE_DE_COURS,
        CampusLocationType.fromString("SALLE_DE_COURS"),
    )
  }

  @Test
  fun fromString_unknownOrBlankLabels_returnsUnknown() {
    Assert.assertEquals(
        CampusLocationType.UNKNOWN,
        CampusLocationType.fromString("NON_EXISTENT_TYPE"),
    )
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString(""))
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString("   "))
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString(null))
  }
}
