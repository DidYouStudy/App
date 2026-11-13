// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.event

/** Character limits shared by event text fields and their ViewModel. */
object EventTextLimits {
  const val DESCRIPTION = 500
  const val SUBJECT = 100
  const val TASK = 200
}

/**
 * Applies [limit] to [newValue] without discarding text already present in [current].
 *
 * Existing over-limit text can shrink but cannot grow. A newly truncated value never ends with half
 * of a surrogate pair.
 */
fun applyTextLimit(current: String, newValue: String, limit: Int): String {
  require(limit >= 0)

  val effectiveLimit = maxOf(limit, current.length)
  if (newValue.length <= effectiveLimit) return newValue
  if (current.length >= limit) return current

  val truncated = newValue.take(limit)
  return if (truncated.isNotEmpty() && Character.isHighSurrogate(truncated.last())) {
    truncated.dropLast(1)
  } else {
    truncated
  }
}
