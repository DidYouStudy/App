// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.model.event.Event
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.calendarDateLabelStyle
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val FIRST_VISIBLE_HOUR = 8
private const val DEFAULT_VISIBLE_HOURS = 10
private val WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val HOUR_FORMAT = DateTimeFormatter.ofPattern("h a", Locale.US)

/** Test tags for the weekly calendar controls and date headings. */
object CalendarTestTags {
  const val WEEK_RANGE = "calendar_weekRange"
  const val WEEK_HEADER = "calendar_weekHeader"
  const val DAY_HEADER = "calendar_dayHeader"
  const val PREVIOUS_WEEK = "calendar_previousWeek"
  const val NEXT_WEEK = "calendar_nextWeek"
  const val TIME_GRID = "calendar_timeGrid"
  const val ADD_BUTTON = "calendar_addButton"
  const val TODAY_BUTTON = "calendar_todayButton"

  /** Creates a stable tag for an event card in a specific day column. */
  fun eventCard(eventId: String, dayIndex: Int): String =
      "calendar_eventCard_${eventId}_day_$dayIndex"

  /**
   * Creates the test tag used to identify a date number in a weekly day header.
   *
   * @param date The calendar date represented by the date number.
   * @return A stable tag containing the supplied date.
   */
  fun dayNumber(date: LocalDate): String = "calendar_dayNumber_${date}"

  /**
   * Creates the test tag used to identify a weekday name in a weekly day header.
   *
   * @param date The calendar date whose weekday name is displayed.
   * @return A stable tag containing the supplied date.
   */
  fun dayName(date: LocalDate): String = "calendar_dayName_${date}"

  /**
   * Creates the test tag used to identify an hour label on the time axis.
   *
   * @param hour The hour of day represented by the label, from 0 through 23.
   * @return A stable tag containing the supplied hour.
   */
  fun hourLabel(hour: Int): String = "calendar_hourLabel_$hour"
}

/**
 * Displays the weekly calendar from [uiState] and forwards week navigation actions to its owner.
 * Date selection and event loading are owned by [CalendarViewModel].
 *
 * @param uiState The ViewModel state containing today's date and the displayed week's dates.
 * @param onPreviousWeek Callback invoked when the previous-week control is clicked.
 * @param onNextWeek Callback invoked when the next-week control is clicked.
 * @param modifier The modifier applied to the weekly calendar's root column.
 */
@Composable
internal fun CalendarWeeklyView(
    uiState: CalendarUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    // Checks the dimensions of the screen and decide whether to make the headers compact
    val compactHeaders = maxWidth > maxHeight
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
      CalendarWeekRangeHeader(
          weekDates = uiState.weekDates,
          onPreviousWeek = onPreviousWeek,
          onNextWeek = onNextWeek,
          compact = compactHeaders,
      )
      CalendarDayHeader(
          weekDates = uiState.weekDates,
          today = uiState.today,
          compact = compactHeaders,
      )
      CalendarTimeGrid(
          events = uiState.events,
          weekDates = uiState.weekDates,
          modifier = Modifier.weight(1f),
      )
    }
  }
}

/**
 * Shows a formatted date range with controls that request the preceding or following week. The
 * caller owns the displayed week and supplies callbacks that update it.
 *
 * @param weekDates The seven consecutive Monday-through-Sunday dates currently displayed.
 * @param onPreviousWeek Callback invoked when the previous-week control is clicked.
 * @param onNextWeek Callback invoked when the next-week control is clicked.
 * @param compact Whether to use the shorter landscape header height.
 * @param modifier The modifier applied to the header row.
 */
@Composable
fun CalendarWeekRangeHeader(
    weekDates: List<LocalDate>,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
  // Place the previous control, week label, and next control in one row.
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(
                  if (compact) Dimens.calendarWeekSwitchHeaderHeightLandscape
                  else Dimens.calendarWeekSwitchHeaderHeight
              )
              .background(MaterialTheme.colorScheme.surface)
              .testTag(CalendarTestTags.WEEK_HEADER)
              .padding(horizontal = Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    // This square surface invokes the callback to show the previous week.
    Surface(
        onClick = onPreviousWeek,
        modifier =
            Modifier.size(Dimens.calendarWeekNavigationButtonSize)
                .testTag(CalendarTestTags.PREVIOUS_WEEK),
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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
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
        modifier =
            Modifier.size(Dimens.calendarWeekNavigationButtonSize)
                .testTag(CalendarTestTags.NEXT_WEEK),
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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.calendar_next_week_description),
        )
      }
    }
  }
}

/**
 * Renders one heading for each supplied date, with a weekday initial and day-of-month number. The
 * date matching [today] receives the current-day visual treatment.
 *
 * @param weekDates The dates to display, normally the seven dates of the visible calendar week.
 * @param today The date to highlight when it is included in [weekDates].
 * @param compact Whether to use the shorter landscape header height.
 * @param modifier The modifier applied to the row of day headings.
 */
@Composable
fun CalendarDayHeader(
    weekDates: List<LocalDate>,
    today: LocalDate,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
  // Lay out the date headings in one row above the weekly grid.
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(
                  if (compact) Dimens.calendarDayHeaderHeightLandscape
                  else Dimens.calendarDayHeaderHeight
              )
              .background(MaterialTheme.colorScheme.surface)
              .testTag(CalendarTestTags.DAY_HEADER)
              .border(
                  width = Dimens.calendarDayHeaderBorderWidth,
                  color = MaterialTheme.colorScheme.outlineVariant,
              ),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Leave room for the time labels so the dates align with the day columns below.
    Spacer(modifier = Modifier.width(Dimens.calendarTimeAxisWidth))

    // Build one equally sized heading for each date in the week.
    weekDates.forEach { date ->
      val isToday = date == today
      val dateTopSpacing = if (isToday) Spacing.extraSmall else Spacing.small

      // Center the weekday name and date number within this day's column.
      Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        // Show the weekday as a single English initial, matching the design.
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
            modifier = Modifier.testTag(CalendarTestTags.dayName(date)).offset(y = dateTopSpacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = calendarDateLabelStyle.fontSize,
        )
        Spacer(modifier = Modifier.height(Spacing.extraExtraSmall))

        // Highlight today's date with a filled circle; leave other dates unfilled.
        Box(modifier = Modifier.size(Dimens.calendarDayNumberSlotSize)) {
          if (isToday) {
            CalendarTodayCircle(
                modifier =
                    Modifier.align(Alignment.Center)
                        .offset(y = Dimens.calendarDayLabelVerticalOffset)
            )
          }
          Text(
              text = date.dayOfMonth.toString(),
              modifier =
                  Modifier.align(Alignment.Center)
                      .testTag(CalendarTestTags.dayNumber(date))
                      .offset(y = Dimens.calendarDayLabelVerticalOffset),
              color =
                  if (isToday) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              fontSize = calendarDateLabelStyle.fontSize,
          )
        }
      }
    }
  }
}

/**
 * Draws the circular highlight behind today's date number.
 *
 * @param modifier The modifier used to align and position the highlight.
 */
@Composable
private fun CalendarTodayCircle(modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .size(Dimens.calendarTodayCircleSize)
              .background(MaterialTheme.colorScheme.primary, CircleShape)
  )
}

/**
 * Renders the empty weekly timeline with hour labels and seven equally sized day columns. The
 * timeline scrolls vertically as one unit and initially positions the view around 8 AM when the
 * measured viewport has enough scroll range.
 *
 * @param modifier The modifier applied to the timeline container.
 */
@Composable
fun CalendarTimeGrid(
    modifier: Modifier = Modifier,
    events: List<Event> = emptyList(),
    weekDates: List<LocalDate> = emptyList(),
) {
  // Keep the grid vertically scrollable
  val scrollState = remember { ScrollState(0) }
  val density = LocalDensity.current
  var initialScrollApplied by remember { androidx.compose.runtime.mutableStateOf(false) }

  // Keep rows readable in short landscape viewports, even when fewer than ten hours fit.
  BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(CalendarTestTags.TIME_GRID)) {
    val hourHeight = maxOf(maxHeight / DEFAULT_VISIBLE_HOURS, Dimens.calendarMinHourRowHeight)

    // Decide the initial position after the first layout, even if this viewport cannot scroll.
    LaunchedEffect(maxHeight) {
      if (!initialScrollApplied) {
        withFrameNanos {}
        if (maxHeight > Dimens.noPadding) {
          val initialOffset =
              with(density) {
                (hourHeight * FIRST_VISIBLE_HOUR - Dimens.calendarInitialLabelClearance).roundToPx()
              }
          scrollState.scrollTo(initialOffset.coerceIn(0, scrollState.maxValue))
        }
        initialScrollApplied = true
      }
    }

    // Put the time axis and all seven day columns in one row so they scroll together.
    Row(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
        verticalAlignment = Alignment.Top,
    ) {
      // Label each hour from midnight through 11 PM.
      Column(modifier = Modifier.width(Dimens.calendarTimeAxisWidth)) {
        repeat(HOURS_PER_DAY) { hour ->
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(hourHeight)
                      .offset(y = Dimens.calendarHourLabelVerticalOffset),
              contentAlignment = Alignment.TopCenter,
          ) {
            Text(
                text = formatHour(hour),
                modifier = Modifier.testTag(CalendarTestTags.hourLabel(hour)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
            )
          }
        }
      }
      // Divide the remaining width evenly among the seven empty day columns.
      val placementsByDay =
          remember(events, weekDates, hourHeight) {
            events
                .flatMap { event -> calculateEventPlacements(event, weekDates, hourHeight) }
                .groupBy { placement -> placement.dayIndex }
          }
      Row(modifier = Modifier.weight(1f)) {
        repeat(DAYS_PER_WEEK) { dayIndex ->
          val placements = placementsByDay[dayIndex].orEmpty()

          Box(modifier = Modifier.weight(1f)) {
            Column {
              repeat(HOURS_PER_DAY) { hour ->
                CalendarEmptyHourCell(
                    hourHeight = hourHeight,
                    isLastHour = hour == HOURS_PER_DAY - 1,
                    isFirstDay = dayIndex == 0,
                )
              }
            }
            placements.forEach { placement ->
              CalendarEventCard(
                  event = placement.event,
                  modifier =
                      Modifier.fillMaxWidth()
                          .offset(y = placement.top)
                          .height(maxOf(Dimens.calendarEventCardMinHeight, placement.height))
                          .testTag(CalendarTestTags.eventCard(placement.event.id, dayIndex)),
              )
            }
          }
        }
      }
    }
  }
}

/** Provides the grid coordinates calculated for an event in the displayed week. */
internal data class CalendarEventPlacement(
    val event: Event,
    val dayIndex: Int,
    val top: Dp,
    val height: Dp,
)

/**
 * Splits an event at local midnight and calculates each segment's grid position.
 *
 * Events outside [weekDates] produce no placements. A placement keeps the original event, so cards
 * created for separate day segments display identical contents.
 */
internal fun calculateEventPlacements(
    event: Event,
    weekDates: List<LocalDate>,
    hourHeight: Dp,
    zone: ZoneId = ZoneId.systemDefault(),
): List<CalendarEventPlacement> {
  val start = event.details.startsAt.atZone(zone)
  val end = event.details.endsAt.atZone(zone)
  val placements = mutableListOf<CalendarEventPlacement>()
  var date = start.toLocalDate()

  // Visit the start date and every following date touched before the event ends.
  while (!date.isAfter(end.toLocalDate())) {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()

    // Clamp this segment to the current day: start at the event time, and end at midnight.
    val segmentStart = maxOf(event.details.startsAt, dayStart)
    val segmentEnd = minOf(event.details.endsAt, nextDayStart)
    val dayIndex = weekDates.indexOf(date)

    if (dayIndex >= 0 && segmentStart.isBefore(segmentEnd)) {
      // Position by wall-clock time because every displayed day has 24 equal hour rows.
      val localDayStart = date.atStartOfDay()
      val localSegmentStart = segmentStart.atZone(zone).toLocalDateTime()
      val localSegmentEnd = segmentEnd.atZone(zone).toLocalDateTime()
      val minutesFromMidnight = Duration.between(localDayStart, localSegmentStart).toMinutes()
      val segmentMinutes =
          Duration.between(localSegmentStart, localSegmentEnd).toMinutes().coerceAtLeast(0)
      placements +=
          CalendarEventPlacement(
              event = event,
              dayIndex = dayIndex,
              top = hourHeight * (minutesFromMidnight / 60f),
              height = hourHeight * (segmentMinutes / 60f),
          )
    }

    // Move to the next local date; the loop stops after the event's ending date.
    date = date.plusDays(1)
  }

  return placements
}

/**
 * Draws the grid lines for one empty day-and-hour cell. Every cell has a top and right boundary;
 * the first day adds the left edge, and the final hour adds the bottom edge.
 *
 * @param hourHeight The measured height shared by every hour row.
 * @param isLastHour Whether this cell belongs to the final hour and should close the grid bottom.
 * @param isFirstDay Whether this cell belongs to the first day and should draw the grid left edge.
 */
@Composable
private fun CalendarEmptyHourCell(
    hourHeight: Dp,
    isLastHour: Boolean,
    isFirstDay: Boolean,
) {
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

/**
 * Formats a validated Monday-through-Sunday week as a compact English date range. It omits repeated
 * month and year text where possible, while showing both years when the week crosses a year
 * boundary. Throws [IllegalArgumentException] if the input is not exactly one ordered week.
 *
 * @param weekDates Seven consecutive dates beginning on Monday.
 * @return The human-readable date range for the week.
 */
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

/**
 * Formats a 24-hour clock value as an English 12-hour label such as `12 AM` or `3 PM`.
 *
 * @param hour The hour of day to format, from 0 through 23.
 * @return The hour label displayed beside the corresponding timeline row.
 */
private fun formatHour(hour: Int): String = LocalTime.of(hour, 0).format(HOUR_FORMAT)
