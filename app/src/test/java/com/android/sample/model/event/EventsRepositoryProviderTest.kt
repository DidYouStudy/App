// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

/** Checks application wiring without initializing a real Firebase app. */
class EventsRepositoryProviderTest {
  /** The provider shares one interface-backed instance across callers. */
  @Test
  fun sharesInstance() {
    mockStatic(FirebaseFirestore::class.java).use { db ->
      mockStatic(FirebaseAuth::class.java).use { auth ->
        db.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }
            .thenReturn(mock(FirebaseFirestore::class.java))
        auth
            .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenReturn(mock(FirebaseAuth::class.java))
        assertSame(
            EventsRepositoryProvider.getRepository(),
            EventsRepositoryProvider.getRepository(),
        )
      }
    }
  }
}
