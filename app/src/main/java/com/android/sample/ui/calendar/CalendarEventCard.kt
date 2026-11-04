package com.android.sample.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.EventTimeLabel
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.calendarEventSubjectStyle
import com.android.sample.ui.theme.calendarEventTimeStyle
import com.android.sample.ui.theme.calendarEventTitleStyle
import com.android.sample.ui.theme.eventColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault())

/** Displays an event with a type label, subject, and space-aware start-time label. */
@Composable
fun CalendarEventCard(
    event: Event,
    modifier: Modifier = Modifier,
) {
  val color = eventColor(event.type)
  val colorDark = eventColor(event.type, true)
  val title =
      event.type.name.lowercase().replace('_', ' ').replaceFirstChar { character ->
        character.titlecase()
      }
  val subject = event.subject.toString()
  val startingTime = EVENT_TIME_FORMAT.format(event.startsAt)
  val cardShape = RoundedCornerShape(Dimens.calendarEventCardCorner)

  // The surface provides one clickable, rounded container for the complete event card.
  Surface(
      modifier = modifier,
      onClick = {}, // shows the event details screen
      shape = cardShape,
      color = color,
  ) {
    // Keep the dark event-type accent separate from the text content.
    Row {
      Box(
          modifier =
              Modifier.width(Spacing.extraSmall)
                  .fillMaxHeight()
                  .background(colorDark)
                  .padding(Spacing.extraSmall)
      )

      Column(
          modifier =
              Modifier.padding(
                  start = Spacing.extraSmall,
                  top = Spacing.extraSmall,
                  bottom = 1.dp,
              ),
          horizontalAlignment = Alignment.Start,
      ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val density = LocalDensity.current

          // Calculate the space required for the title and the subject. The time is always one
          // line.
          val titleHeight = with(density) { calendarEventTitleStyle.lineHeight.toDp() }
          val subjectLineHeight = with(density) { calendarEventSubjectStyle.lineHeight.toDp() }

          // The title is mandatory, so only the space below it can be shared by subject and time.
          val remainingHeight = (maxHeight - titleHeight).coerceAtLeast(0.dp)
          val remainingLines = (remainingHeight / subjectLineHeight).toInt()

          // Reserve the final available line for the time whenever at least two lines remain.
          val showTime = remainingLines >= 2
          val subjectLines = if (showTime) remainingLines - 1 else remainingLines

          Column {
            Text(text = title, style = calendarEventTitleStyle)
            if (subjectLines > 0) {
              // The subject receives every remaining line and truncates with an ellipsis if needed.
              Text(
                  text = subject,
                  style = calendarEventSubjectStyle,
                  maxLines = subjectLines,
                  overflow = TextOverflow.Ellipsis,
              )
            }
            if (showTime) {
              // The time is always the last line when the card has room for it.
              Text(text = startingTime, style = calendarEventTimeStyle, color = EventTimeLabel)
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CalendarEventCardPreview() {
  DidYouStudyTheme { CalendarEventCard(previewEvent) }
}

private val previewEvent =
    Event(
        id = "preview-event",
        ownerId = "preview-owner",
        type = EventType.STUDY_SESSION,
        subject = "Analysis",
        startsAt = Instant.parse("2026-11-04T09:00:00Z"),
        endsAt = Instant.parse("2026-11-04T10:00:00Z"),
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
    )
