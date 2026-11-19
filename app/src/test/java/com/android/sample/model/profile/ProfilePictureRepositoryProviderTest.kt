// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import com.android.sample.R
import com.google.firebase.storage.FirebaseStorage
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

class ProfilePictureRepositoryProviderTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    ProfilePictureRepositoryProvider.setRepository(null)
    context = mock(Context::class.java)
    `when`(context.getString(R.string.profile_picture_storage_folder)).thenReturn("profilePictures")
    `when`(context.getString(R.string.profile_picture_content_type)).thenReturn("image/jpeg")
  }

  @After
  fun clearAfter() {
    ProfilePictureRepositoryProvider.setRepository(null)
  }

  // Without an override, the default is a Storage repository, created once and then reused.
  @Test
  fun getRepository_createsStorageRepositoryOnceAndCachesIt() {
    mockStatic(FirebaseStorage::class.java).use { firebase ->
      firebase
          .`when`<FirebaseStorage> { FirebaseStorage.getInstance() }
          .thenReturn(mock(FirebaseStorage::class.java))

      val first = ProfilePictureRepositoryProvider.getRepository(context)
      val second = ProfilePictureRepositoryProvider.getRepository(context)

      assertTrue(first is ProfilePictureRepositoryStorage)
      assertSame(first, second)
      firebase.verify({ FirebaseStorage.getInstance() }, times(1))
    }
  }

  // An injected repository is returned without touching Storage, and null restores the default.
  @Test
  fun setRepository_overridesAndNullRestoresDefault() {
    mockStatic(FirebaseStorage::class.java).use { firebase ->
      firebase
          .`when`<FirebaseStorage> { FirebaseStorage.getInstance() }
          .thenReturn(mock(FirebaseStorage::class.java))
      val injected = mock(ProfilePictureRepository::class.java)

      ProfilePictureRepositoryProvider.setRepository(injected)
      assertSame(injected, ProfilePictureRepositoryProvider.getRepository(context))
      firebase.verifyNoInteractions()

      ProfilePictureRepositoryProvider.setRepository(null)
      val restored = ProfilePictureRepositoryProvider.getRepository(context)
      assertTrue(restored is ProfilePictureRepositoryStorage)
      assertNotSame(injected, restored)
    }
  }
}
