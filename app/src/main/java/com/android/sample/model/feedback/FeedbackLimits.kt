// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

/** Limits applied to session feedback values. */
object FeedbackLimits {
  /** Maximum UTF-16 code-unit length of a comment. */
  const val COMMENT = 500

  /** Maximum number of intervals in one feedback record. */
  const val MAX_INTERVALS = 50
}
