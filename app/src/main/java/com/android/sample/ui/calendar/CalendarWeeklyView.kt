// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val HOURS_PER_DAY = 24

private const val DAYS_PER_WEEK = 7
private const val FIRST_VISIBLE_HOUR = 8
private const val DEFAULT_VISIBLE_HOURS = 10
private val TIME_AXIS_WIDTH = 48.dp
private val WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val HOUR_FORMAT = DateTimeFormatter.ofPattern("h a", Locale.US)

/** Test tags for the weekly calendar controls and date headings. */
object CalendarTestTags {
  const val WEEK_RANGE = "calendar_weekRange"
  const val PREVIOUS_WEEK = "calendar_previousWeek"
  const val NEXT_WEEK = "calendar_nextWeek"
  const val TIME_GRID = "calendar_timeGrid"
  const val ADD_BUTTON = "calendar_addButton"

  /** Creates a stable test tag for the date number in a day header. */
  fun dayNumber(date: LocalDate): String = "calendar_dayNumber_${date}"

  /** Creates a stable test tag for the day name in a day header. */
  fun dayName(date: LocalDate): String = "calendar_dayName_${date}"

  /** Creates a stable test tag for an hour label on the time axis. */
  fun hourLabel(hour: Int): String = "calendar_hourLabel_$hour"
}

/** Displays a navigable week header and the empty weekly time grid. */
@Composable
internal fun CalendarWeeklyView(
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
  val dateHelper = remember { CalendarDateHelper() }
  var weekStartEpochDay by
      rememberSaveable(today.toEpochDay()) {
        mutableLongStateOf(dateHelper.weekDates(today).first().toEpochDay())
      }
  val weekStart = LocalDate.ofEpochDay(weekStartEpochDay)
  val weekDates = remember(weekStartEpochDay) { dateHelper.weekDates(weekStart) }

  Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    CalendarWeekRangeHeader(
        weekDates = weekDates,
        onPreviousWeek = {
          weekStartEpochDay = dateHelper.previousWeek(weekStart).first().toEpochDay()
        },
        onNextWeek = { weekStartEpochDay = dateHelper.nextWeek(weekStart).first().toEpochDay() },
    )
    CalendarDayHeader(weekDates = weekDates, today = today)
    CalendarTimeGrid(modifier = Modifier.weight(1f))
  }
}

/** Shows the visible week range and controls for moving one week at a time. */
@Composable
fun CalendarWeekRangeHeader(
    weekDates: List<LocalDate>,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Place the previous control, week label, and next control in one row.
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(Dimens.calendarWeekSwitchHeaderHeight)
              .background(MaterialTheme.colorScheme.surface)
              .padding(horizontal = Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    // This square surface invokes the callback to show the previous week.
    Surface(
        onClick = onPreviousWeek,
        modifier = Modifier.size(36.dp).testTag(CalendarTestTags.PREVIOUS_WEEK),
        shape = RoundedCornerShape(Dimens.cornerMedium),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      // Center the arrow within its colored button.
      Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.calendar_previous_week_description),
        )
      }
    }
    // Display the date range for the currently visible week.
    Text(
        text = formatWeekRange(weekDates),
        modifier = Modifier.testTag(CalendarTestTags.WEEK_RANGE),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    // This square surface invokes the callback to show the next week.
    Surface(
        onClick = onNextWeek,
        modifier = Modifier.size(36.dp).testTag(CalendarTestTags.NEXT_WEEK),
        shape = RoundedCornerShape(Dimens.cornerMedium),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      // Center the arrow within its colored button.
      Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = stringResource(R.string.calendar_next_week_description),
        )
      }
    }
  }
}

/** Shows seven weekday initials and date numbers, highlighting [today]. */
@Composable
fun CalendarDayHeader(
    weekDates: List<LocalDate>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
  // Lay out the date headings in one row above the weekly grid.
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(54.dp)
              .background(MaterialTheme.colorScheme.surface)
              .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Leave room for the time labels so the dates align with the day columns below.
    Spacer(modifier = Modifier.width(TIME_AXIS_WIDTH))

    // Build one equally sized heading for each date in the week.
    weekDates.forEach { date ->
      val isToday = date == today
      val dateTopSpacing = if (isToday) Spacing.extraSmall else Dp.Hairline

      // Center the weekday name and date number within this day's column.
      Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        // Show the weekday as a single English initial, matching the design.
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
            modifier = Modifier.testTag(CalendarTestTags.dayName(date)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )

        // Highlight today's date with a filled circle; leave other dates unfilled.
        Box(
            modifier =
                Modifier.padding(top = dateTopSpacing)
                    .size(20.dp)
                    .then(
                        if (isToday) {
                          Modifier
                              .background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                          Modifier
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = date.dayOfMonth.toString(),
              modifier = Modifier.testTag(CalendarTestTags.dayNumber(date)).semantics { selected = isToday },
              color =
                  if (isToday) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
          )
        }
      }
    }
  }
}

/** Shows an empty 24-hour timeline with seven aligned day columns. */
@Composable
fun CalendarTimeGrid(
    modifier: Modifier = Modifier,
) {
  // Keep the grid vertically scrollable
  val scrollState = rememberScrollState()
  val density = LocalDensity.current
  var initialScrollApplied by remember { androidx.compose.runtime.mutableStateOf(false) }

  // Use the available height to size rows so nine hours fit in the viewport.
  BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(CalendarTestTags.TIME_GRID)) {
    val hourHeight = maxHeight / DEFAULT_VISIBLE_HOURS

    // Decide the initial position after the first layout, even if this viewport cannot scroll.
    LaunchedEffect(hourHeight) {
      if (!initialScrollApplied) {
        withFrameNanos {}
        val initialOffset = with(density) { (hourHeight * FIRST_VISIBLE_HOUR).roundToPx() }
        scrollState.scrollTo(initialOffset.coerceAtMost(scrollState.maxValue))
        initialScrollApplied = true
      }
    }

    // Put the time axis and all seven day columns in one row so they scroll together.
    Row(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
        verticalAlignment = Alignment.Top,
    ) {
      // Label each hour from midnight through 11 PM.
      Column(modifier = Modifier.width(TIME_AXIS_WIDTH)) {
        repeat(HOURS_PER_DAY) { hour ->
          Box(
              modifier = Modifier.fillMaxWidth().height(hourHeight).offset(-4.dp),
              contentAlignment = Alignment.TopEnd,
          ) {
            Text(
                text = formatHour(hour),
                modifier =
                    Modifier.padding(start = 6.dp, top = Spacing.extraSmall)
                        .testTag(CalendarTestTags.hourLabel(hour)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                textAlign = TextAlign.End,
            )
          }
        }
      }
      // Divide the remaining width evenly among the seven empty day columns.
      Row(modifier = Modifier.weight(1f)) {
        repeat(DAYS_PER_WEEK) { dayIndex ->
          Column(modifier = Modifier.weight(1f)) {
            repeat(HOURS_PER_DAY) { hour ->
              CalendarEmptyHourCell(
                  hourHeight = hourHeight,
                  isLastHour = hour == HOURS_PER_DAY - 1,
                  isFirstDay = dayIndex == 0,
              )
            }
          }
        }
      }
    }
  }
}

/** Draws the horizontal hour boundaries and vertical day boundary for an empty grid cell. */
@Composable
private fun CalendarEmptyHourCell(
    hourHeight: Dp,
    isLastHour: Boolean,
    isFirstDay: Boolean,
) {
  // Use the theme's divider color for the grid lines.
  val lineColor = MaterialTheme.colorScheme.outlineVariant
  // Draw this hour cell's borders behind its empty content.
  Box(
      modifier =
          Modifier.fillMaxWidth().height(hourHeight).drawBehind {
            val strokeWidth = Dimens.borderThin.toPx()
            // Draw the horizontal line at the start of this hour.
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = strokeWidth,
            )
            // Draw the right edge of this day column.
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = strokeWidth,
            )
            if (isFirstDay) {
              // Draw the left edge only for the first day column.
              drawLine(
                  color = lineColor,
                  start = androidx.compose.ui.geometry.Offset(0f, 0f),
                  end = androidx.compose.ui.geometry.Offset(0f, size.height),
                  strokeWidth = strokeWidth,
              )
            }
            if (isLastHour) {
              // Close the grid at the end of the final hour.
              drawLine(
                  color = lineColor,
                  start = androidx.compose.ui.geometry.Offset(0f, size.height),
                  end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                  strokeWidth = strokeWidth,
              )
            }
          },
  )
}

/** Formats the week range in the English style shown in the calendar design. */
internal fun formatWeekRange(weekDates: List<LocalDate>): String {
  require(weekDates.size == DAYS_PER_WEEK) { "A calendar week must contain seven dates" }
  val first = weekDates.first()
  val last = weekDates.last()
  require(first.dayOfWeek == DayOfWeek.MONDAY) { "A calendar week must start on Monday" }
  require(weekDates.zipWithNext().all { (current, next) -> next == current.plusDays(1) }) {
    "Calendar week dates must be consecutive"
  }

  return when {
    first.year != last.year ->
        "${first.format(WEEK_RANGE_FORMAT)}, ${first.year}–${last.format(WEEK_RANGE_FORMAT)}, ${last.year}"
    first.month != last.month ->
        "${first.format(WEEK_RANGE_FORMAT)}–${last.format(WEEK_RANGE_FORMAT)}, ${last.year}"
    else -> "${first.format(WEEK_RANGE_FORMAT)}–${last.dayOfMonth}, ${last.year}"
  }
}

/** Formats an hour on the 12-hour clock used by the design. */
private fun formatHour(hour: Int): String = LocalTime.of(hour, 0).format(HOUR_FORMAT)
