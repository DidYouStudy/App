// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Combines a student's personal events and joined or created group sessions into a unified
 * schedule.
 *
 * This use case queries both [EventsRepository] and [GroupSessionsRepository], filtering group
 * sessions to those where the student is a member (creator or joined member), and sorts the
 * combined results by start time, end time, and ID.
 *
 * @property eventsRepository repository storing personal events.
 * @property groupSessionsRepository repository storing group sessions.
 * @property clock provider for current time and time zone calculations.
 */
class GetUserScheduleUseCase(
    private val eventsRepository: EventsRepository = EventsRepositoryProvider.getRepository(),
    private val groupSessionsRepository: GroupSessionsRepository =
        GroupSessionsRepositoryProvider.getRepository(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /**
     * Observes personal events and group sessions created or joined by [studentId] overlapping
     * `[from, to)`.
     *
     * @param studentId unique ID of the signed-in student; null or blank returns an empty list.
     * @param from start of the time range (inclusive).
     * @param to end of the time range (exclusive).
     * @return flow emitting [Result.success] with sorted events, or [Result.failure] if either source
     *   fails.
     */
    operator fun invoke(
        studentId: String?,
        from: Instant,
        to: Instant,
    ): Flow<Result<List<Event>>> {
        if (studentId.isNullOrBlank()) {
            return flowOf(Result.success(emptyList()))
        }
        if (!to.isAfter(from)) {
            return flowOf(Result.failure(EventsException(EventsError.INVALID_ARGUMENT)))
        }

        val personalEventsFlow = eventsRepository.observeEvents(studentId, from, to)
        val groupSessionsFlow = groupSessionsRepository.observeSessions(endingAfter = from)

        return combine(personalEventsFlow, groupSessionsFlow) { personalResult, groupResult ->
            val personalEvents = personalResult.getOrElse {
                return@combine Result.failure(it)
            }
            val groupSessions = groupResult.getOrElse {
                return@combine Result.failure(it)
            }

            val joinedSessions = groupSessions.filter { session ->
                studentId in session.memberIds &&
                        session.details.startsAt.isBefore(to) &&
                        session.details.endsAt.isAfter(from)
            }

            val combined: List<Event> = (personalEvents + joinedSessions).sortedWith(EVENT_COMPARATOR)

            Result.success(combined)
        }
    }

    /**
     * Convenience overload to observe events for a single day using the injected [clock]'s time zone.
     *
     * @param studentId unique ID of the signed-in student.
     * @param date calendar date to observe.
     * @return flow emitting [Result.success] with sorted events for that day.
     */
    operator fun invoke(
        studentId: String?,
        date: LocalDate,
    ): Flow<Result<List<Event>>> {
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return invoke(studentId, from, to)
    }

    companion object {
        /** Sorting rule: start time, then end time, then ID. */
        private val EVENT_COMPARATOR =
            compareBy<Event>(
                { it.details.startsAt },
                { it.details.endsAt },
                { it.id },
            )
    }
}