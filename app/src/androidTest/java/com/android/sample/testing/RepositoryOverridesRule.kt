// Co-authored-by: Claude Opus 5.5
package com.android.sample.testing

import androidx.credentials.CredentialManager
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import org.junit.rules.ExternalResource

/**
 * Installs test repositories in the app's providers for one test, and always restores the
 * production defaults afterwards (even when the test fails), so no override leaks into the next
 * test.
 *
 * Every Firebase-backed repository the app's screens read must be given, so a test can never fall
 * back to the real Firebase project by accident. Pass fakes, or repositories built on a Firebase
 * emulator app. When the activity reads the providers as it starts, run this rule around the
 * activity rule: `RuleChain.outerRule(repositories).around(composeRule)`.
 *
 * @param credentialManager optional fake behind the Google account picker; `null` keeps the
 *   platform one.
 */
class RepositoryOverridesRule(
    private val auth: AuthRepository,
    private val preferences: PreferencesRepository,
    private val profile: ProfileRepository,
    private val events: EventsRepository,
    private val credentialManager: CredentialManager? = null,
) : ExternalResource() {

  /** Installs the repositories before the test, and therefore its activity, starts. */
  override fun before() {
    AuthRepositoryProvider.setCredentialManager(credentialManager)
    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    ProfileRepositoryProvider.setRepository(profile)
    EventsRepositoryProvider.setRepository(events)
  }

  /** Removes every override, so the next test starts from the production defaults again. */
  override fun after() {
    AuthRepositoryProvider.setRepository(null)
    AuthRepositoryProvider.setCredentialManager(null)
    PreferencesRepositoryProvider.setRepository(null)
    ProfileRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
  }
}
