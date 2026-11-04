package com.android.sample.ui.calendar


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Clickable event card which * */
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

  Surface(
      modifier = modifier,
      onClick = {}, //shows the event details screen
      shape = cardShape,
      color = color,
  ) {
    Row {
      Box(modifier = Modifier.width(Spacing.extraSmall).fillMaxHeight().background(colorDark).padding(Spacing.extraSmall))

      Column(
          modifier = Modifier.padding(start = Spacing.extraSmall, end = Spacing.small, top = Spacing.extraSmall, bottom = Spacing.small),
        horizontalAlignment = Alignment.Start,
      ) {
        Text(text = title, style = calendarEventTitleStyle)
        Text(text = subject, style = calendarEventSubjectStyle)
        Text(text = startingTime, style = calendarEventTimeStyle, color = EventTimeLabel)
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
