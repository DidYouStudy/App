// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.location.Location
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the parts of the map view that do not need the real Google map, which cannot load in a
 * unit test: the preview stand-in, the pin outline and the resource and coordinate helpers.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapViewTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val markers =
      listOf(
          MapMarkerUi("AAB 0 26", Location(46.5186, 6.5619), count = 3),
          MapMarkerUi("CM 1 121", Location(46.5202, 6.5652), count = 1),
      )

  @Composable
  private fun PreviewMode(content: @Composable () -> Unit) =
      CompositionLocalProvider(LocalInspectionMode provides true, content = content)

  @Test
  fun inInspectionMode_listsEachMarkerWithItsSessionCount() {
    composeTestRule.setContent {
      PreviewMode { GroupSessionMapView(markers, cameraTarget = null, onMarkerClick = {}) }
    }

    composeTestRule.onNodeWithText("AAB 0 26: 3 sessions").assertIsDisplayed()
    composeTestRule.onNodeWithText("CM 1 121: 1 sessions").assertIsDisplayed()
  }

  @Test
  fun inInspectionMode_tappingAMarkerLine_reportsItsRoomName() {
    var tapped: String? = null
    composeTestRule.setContent {
      PreviewMode {
        GroupSessionMapView(markers, cameraTarget = null, onMarkerClick = { tapped = it })
      }
    }

    composeTestRule.onNodeWithText("CM 1 121: 1 sessions").performClick()

    assertEquals("CM 1 121", tapped)
  }

  @Test
  fun inInspectionMode_tappingTheBackground_reportsAMapClickOnly() {
    var mapClicks = 0
    var tapped: String? = null
    composeTestRule.setContent {
      PreviewMode {
        GroupSessionMapView(
            markers,
            cameraTarget = null,
            onMarkerClick = { tapped = it },
            onMapClick = { mapClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithText("Map (shown on a device only)").performClick()

    assertEquals(1, mapClicks)
    assertEquals(null, tapped)
  }
}
