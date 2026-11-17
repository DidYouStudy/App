// Co-authored-by: Claude Code
package com.android.sample.model.profile

import java.util.Locale

/**
 * Format rules and normalization for profile usernames.
 *
 * A username is 3 to 20 characters, starts with a letter or digit, and then contains only letters,
 * digits, `_`, `.` or `-`. The leading character rule and the restricted alphabet keep the
 * lowercase form usable as a Firestore document ID (no `/`, never `.`, `..` or `__x__`). The same
 * format is enforced by `firestore.rules`; keep both in sync.
 */
object UsernamePolicy {
  /** Minimum accepted username length. */
  const val MIN_LENGTH = 3

  /** Maximum accepted username length. */
  const val MAX_LENGTH = 20

  /** Number of candidates tried when claiming a default username. */
  const val DEFAULT_ATTEMPTS = 5

  private const val FALLBACK_BASE = "user"
  private val format = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{${MIN_LENGTH - 1},${MAX_LENGTH - 1}}")

  /**
   * Validates a user-typed username.
   *
   * @param raw text entered by the user; surrounding whitespace is ignored.
   * @return the trimmed username when it follows the format, or `null` when it is invalid.
   */
  fun validate(raw: String): String? = raw.trim().takeIf { format.matches(it) }

  /**
   * Returns the case-insensitive key under which a username is reserved.
   *
   * @param username a username already accepted by [validate].
   */
  fun key(username: String): String = username.lowercase(Locale.ROOT)

  /**
   * Builds the [attempt]-th default username candidate for a user.
   *
   * Attempt 0 is the sanitized display name. Later attempts append characters of the user ID, so
   * the sequence is deterministic and differs between users sharing the same display name. Every
   * candidate satisfies [validate].
   *
   * @param displayName name from the sign-in provider, possibly null or containing invalid
   *   characters.
   * @param uid authentication user ID used to disambiguate collisions.
   * @param attempt zero-based candidate index, below [DEFAULT_ATTEMPTS].
   */
  fun defaultCandidate(displayName: String?, uid: String, attempt: Int): String {
    val base = sanitize(displayName)
    if (attempt == 0) return base
    val suffix = uid.filter { it.isLetterOrDigit() && it.code < 128 }.take(MIN_LENGTH + attempt)
    val head = base.take(MAX_LENGTH - 1 - suffix.length)
    return validate("${head}_$suffix") ?: "${FALLBACK_BASE}_${suffix.ifEmpty { "0000" }}"
  }

  /** Turns free text into a valid username base: spaces become `_`, invalid characters vanish. */
  private fun sanitize(displayName: String?): String {
    val cleaned =
        displayName
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), "_")
            .filter { it.code < 128 && (it.isLetterOrDigit() || it in "_.-") }
            .dropWhile { !it.isLetterOrDigit() }
            .take(MAX_LENGTH)
    return validate(cleaned) ?: FALLBACK_BASE
  }
}
