// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.calendar

/**
 * Activity kinds shown in the Figma calendar and add-event form.
 *
 * A type describes the activity, while [CalendarEvent.subject] identifies its course or subject.
 * Labels and event colors belong to the UI rather than the persistence-independent domain model.
 */
enum class EventType {
  STUDY_SESSION,
  COURSE,
  EXERCISE_SESSION,
  LAB,
  EXAM,
  OTHER,
}
