package com.android.sample.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.calendarEventSubjectStyle
import com.android.sample.ui.theme.calendarEventTimeStyle
import com.android.sample.ui.theme.calendarEventTitleStyle
import com.android.sample.ui.theme.eventColor
import com.android.sample.ui.theme.label
import com.android.sample.ui.theme.onEventColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault())
private const val EVENT_TITLE_MAX_LINES = 2
private const val EVENT_TIME_MAX_LINES = 1

/** Test tags for the event card's independently rendered content. */
object CalendarEventCardTestTags {
  const val SUBJECT = "calendar_eventCard_subject"
}

/** Displays an event with a type label, subject, and space-aware start-time label. */
@Composable
fun CalendarEventCard(
    event: Event,
    modifier: Modifier = Modifier,
) {
  val color = eventColor(event.details.type)
  val colorDark = eventColor(event.details.type, true)
  val contentColor = onEventColor()
  val title = event.details.type.label()
  val subject = event.details.subject
  val startingTime = EVENT_TIME_FORMAT.format(event.details.startsAt)
  val cardShape = RoundedCornerShape(Dimens.calendarEventCardCorner)

  // The surface provides one clickable, rounded container for the complete event card.
  Surface(
      modifier = modifier,
      onClick = {}, // shows the event details screen
      shape = cardShape,
      color = color,
  ) {
    // Keep the dark event-type accent separate from the text content.
    Box(modifier = Modifier.fillMaxSize()) {
      Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.width(Spacing.extraSmall)
                    .fillMaxHeight()
                    .background(colorDark)
                    .padding(Spacing.extraSmall)
        )

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        start = Spacing.extraSmall,
                        end = Spacing.extraSmall,
                        top = Spacing.extraSmall,
                    ),
            horizontalAlignment = Alignment.Start,
        ) {
          // The title is always displayed.
          Text(
              text = title,
              style = calendarEventTitleStyle,
              color = contentColor,
              maxLines = EVENT_TITLE_MAX_LINES,
              overflow = TextOverflow.Ellipsis,
          )
          if (subject != null) {
            FittingSubject(text = subject, style = calendarEventSubjectStyle, color = contentColor)
          }
          // The time is always displayed.
          Text(
              text = startingTime,
              style = calendarEventTimeStyle,
              color = contentColor,
              maxLines = EVENT_TIME_MAX_LINES,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Shows [text] using as many whole lines as fit in the height allocated by the parent.
 *
 * The line count comes from Compose's text measurement at the actual width and style, rather than
 * from an estimate based on the style's nominal line height.
 */
@Composable
private fun ColumnScope.FittingSubject(text: String, style: TextStyle, color: Color) {
  val textMeasurer = rememberTextMeasurer()
  val resolvedStyle = LocalTextStyle.current.merge(style).copy(color = color)

  // Do not fill unused space so the time follows a short subject.
  BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
    val fullLayout =
        textMeasurer.measure(
            text = text,
            style = resolvedStyle,
            constraints = Constraints(maxWidth = constraints.maxWidth),
        )
    val fittingLines =
        if (constraints.hasBoundedHeight) {
          (0 until fullLayout.lineCount).count {
            fullLayout.getLineBottom(it) <= constraints.maxHeight
          }
        } else {
          fullLayout.lineCount
        }
    if (fittingLines > 0) {
      Text(
          text = text,
          modifier = Modifier.testTag(CalendarEventCardTestTags.SUBJECT),
          style = style.copy(color = color),
          maxLines = fittingLines,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CalendarEventCardPreview() {
  DidYouStudyTheme { CalendarEventCard(previewEvent) }
}

@Preview(showBackground = true)
@Composable
fun CalendarEventCardDarkThemePreview() {
  DidYouStudyTheme(darkTheme = true) { CalendarEventCard(previewEvent) }
}

private val previewEvent =
    PersonalEvent(
        id = "preview-event",
        ownerId = "preview-owner",
        details =
            EventDetails(
                type = EventType.STUDY_SESSION,
                subject = "Analysis",
                startsAt = Instant.parse("2026-11-04T09:00:00Z"),
                endsAt = Instant.parse("2026-11-04T10:00:00Z"),
            ),
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
    )
