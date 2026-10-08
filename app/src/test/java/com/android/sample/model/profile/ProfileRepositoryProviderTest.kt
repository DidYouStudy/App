// Co-authored-by: Copilot
package com.android.sample.model.profile

import android.content.Context
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

class ProfileRepositoryProviderTest {
  @Before
  fun clearBefore() {
    setInstance(null)
  }

  @After
  fun clearAfter() {
    setInstance(null)
  }

  // Returns the same cached repository regardless of which context is supplied.
  @Test
  fun getRepository_cachesRepositoryAcrossCallsAndContexts() {
    val firestore = mock(FirebaseFirestore::class.java)
    val collection = mock(CollectionReference::class.java)
    val firstContext = mock(Context::class.java)
    val secondContext = mock(Context::class.java)
    `when`(firestore.collection("profiles")).thenReturn(collection)

    mockStatic(FirebaseFirestore::class.java).use { firebase ->
      firebase.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)

      val first = ProfileRepositoryProvider.getRepository(firstContext)
      val second = ProfileRepositoryProvider.getRepository(secondContext)

      assertSame(first, second)
      firebase.verify({ FirebaseFirestore.getInstance() }, times(1))
    }
  }

  private fun setInstance(repository: ProfileRepository?) {
    ProfileRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(null, repository)
    }
  }
}
