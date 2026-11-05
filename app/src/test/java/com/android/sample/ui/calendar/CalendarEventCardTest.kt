package com.android.sample.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the event card's displayed labels, formatting, and interaction semantics. */
@RunWith(AndroidJUnit4::class)
class CalendarEventCardTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun displaysFormattedTitleSubjectAndStartTime() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(
            event =
                previewEvent(
                    type = EventType.STUDY_SESSION,
                    subject = "Linear Algebra",
                )
        )
      }
    }

    composeTestRule.onNodeWithText("Study session").assertIsDisplayed()
    composeTestRule.onNodeWithText("Linear Algebra").assertIsDisplayed()
    val expectedTime =
        DateTimeFormatter.ofPattern("H:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse("2026-11-04T09:00:00Z"))
    composeTestRule.onNodeWithText(expectedTime).assertIsDisplayed()
  }

  @Test
  fun alwaysDisplaysTitleAndTimeWhenRequestedHeightIsBelowMinimum() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(
            modifier = Modifier.width(120.dp).height(16.dp),
            event = previewEvent(type = EventType.STUDY_SESSION, subject = "Linear Algebra"),
        )
      }
    }

    composeTestRule.onNodeWithText("Study session").assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedStartTime()).assertIsDisplayed()
  }

  @Test
  fun alwaysDisplaysTitleAndTimeAtMinimumHeight() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(
            modifier = Modifier.width(120.dp).height(Dimens.calendarEventCardMinHeight),
            event = previewEvent(type = EventType.STUDY_SESSION, subject = "Linear Algebra"),
        )
      }
    }
    composeTestRule.onNodeWithText("Study session").assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedStartTime()).assertIsDisplayed()
  }

  @Test
  fun displaysSubjectWhenSpaceRemainsAfterTitleAndTime() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(
            modifier = Modifier.width(120.dp).height(64.dp),
            event = previewEvent(type = EventType.STUDY_SESSION, subject = "Linear Algebra"),
        )
      }
    }
    composeTestRule
        .onNodeWithTag(CalendarEventCardTestTags.SUBJECT, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedStartTime(), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun formatsEveryEventTypeWithSpacesAndOnlyFirstLetterCapitalized() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        Column {
          EventType.entries.forEach { type ->
            CalendarEventCard(event = previewEvent(type = type, subject = "Subject"))
          }
        }
      }
    }

    EventType.entries.forEach { type ->
      val expectedTitle =
          type.name.lowercase().replace('_', ' ').replaceFirstChar { character ->
            character.titlecase()
          }
      composeTestRule.onNodeWithText(expectedTitle).assertExists()
    }
  }

  @Test
  fun omitsOptionalSubjectWhenOtherEventHasNoSubject() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(
            event =
                previewEvent(
                    type = EventType.OTHER,
                    subject = null,
                )
        )
      }
    }

    composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedStartTime()).assertIsDisplayed()
    assertTrue(composeTestRule.onAllNodesWithText("null").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun cardExposesClickAction() {
    composeTestRule.setContent {
      DidYouStudyTheme {
        CalendarEventCard(event = previewEvent(type = EventType.EXAM, subject = "History"))
      }
    }

    composeTestRule.onNodeWithText("Exam").assertHasClickAction()
  }

  private fun previewEvent(type: EventType, subject: String?): PersonalEvent {
    return PersonalEvent(
        id = "event-${type.name}",
        ownerId = "test-owner",
        type = type,
        subject = subject,
        startsAt = Instant.parse("2026-11-04T09:00:00Z"),
        endsAt = Instant.parse("2026-11-04T10:00:00Z"),
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
    )
  }

  private fun expectedStartTime(): String =
      DateTimeFormatter.ofPattern("H:mm")
          .withZone(ZoneId.systemDefault())
          .format(Instant.parse("2026-11-04T09:00:00Z"))
}
