// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

/**
 * Activity kinds shown in the Figma calendar and add-event form.
 *
 * A type describes the activity, while [Event.subject] identifies its course or subject. Labels and
 * event colors belong to the UI rather than the persistence-independent domain model.
 */
enum class EventType {
  STUDY_SESSION,
  COURSE,
  EXERCISE_SESSION,
  LAB,
  PROJECT,
  EXAM,
  OTHER,
}
