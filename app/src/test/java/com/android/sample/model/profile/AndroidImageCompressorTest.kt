// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.android.sample.R
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Unit tests for [AndroidImageCompressor]. Native graphics mode is needed so that Robolectric
 * really decodes and encodes images instead of using empty stand-ins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageCompressorTest {
  private lateinit var context: Context
  private var maxEdge = 0

  /** Reads the configured limit so the test sizes follow it if the resource changes. */
  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    maxEdge = context.resources.getInteger(R.integer.profile_picture_max_edge_px)
  }

  /** A large picture is shrunk to the limit on its longest side, keeping its proportions. */
  @Test
  fun compress_shrinksLargeImageKeepingAspectRatio() = runTest {
    val uri = writePng(width = maxEdge * 2, height = maxEdge)

    val options = decodeBounds(compressor().compress(uri).getOrThrow())

    assertEquals("image/jpeg", options.outMimeType)
    assertEquals(maxEdge, options.outWidth)
    assertEquals(maxEdge / 2, options.outHeight)
  }

  /** A picture already below the limit is re-encoded without being enlarged. */
  @Test
  fun compress_keepsSmallImageSize() = runTest {
    val width = maxEdge / 2
    val height = maxEdge / 4
    val uri = writePng(width, height)

    val options = decodeBounds(compressor().compress(uri).getOrThrow())

    assertEquals(width, options.outWidth)
    assertEquals(height, options.outHeight)
  }

  /** A location that cannot be read fails with the invalid image category. */
  @Test
  fun compress_returnsInvalidImageFailure_whenUriCannotBeRead() = runTest {
    val missing = Uri.fromFile(File(context.cacheDir, "missing.png")).toString()

    val result = compressor().compress(missing)

    assertEquals(
        ProfilePictureError.INVALID_IMAGE,
        (result.exceptionOrNull() as ProfilePictureException).error,
    )
  }

  /** A file that opens but is not a valid image fails with the invalid image category. */
  @Test
  fun compress_returnsInvalidImageFailure_whenFileIsNotADecodableImage() = runTest {
    val uri = writeFile("This is text, not an image.".toByteArray())

    val result = compressor().compress(uri)

    val exception = result.exceptionOrNull() as ProfilePictureException
    assertEquals(ProfilePictureError.INVALID_IMAGE, exception.error)
    // The decoder's own failure is kept as the cause, for logging.
    assertNotNull(exception.cause)
  }

  /** Cancelling the caller stops the compression instead of returning a failed result. */
  @Test
  fun compress_propagatesCancellation() = runTest {
    val uri = writePng(width = maxEdge, height = maxEdge)
    // The work is queued on a test dispatcher that never runs, so the caller stays suspended.
    val compressor = AndroidImageCompressor(context, StandardTestDispatcher(testScheduler))
    var result: Result<ByteArray>? = null

    val job =
        launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
          result = compressor.compress(uri)
        }
    job.cancel()
    job.join()

    assertTrue(job.isCancelled)
    assertNull(result)
  }

  private fun compressor() = AndroidImageCompressor(context, UnconfinedTestDispatcher())

  /** Writes [bytes] as they are to a file in the cache directory and returns its URI string. */
  private fun writeFile(bytes: ByteArray): String {
    val file = File.createTempFile("picked", ".png", context.cacheDir)
    file.writeBytes(bytes)
    return Uri.fromFile(file).toString()
  }

  /** Writes a blank PNG of the given size to the cache directory and returns its URI string. */
  private fun writePng(width: Int, height: Int): String {
    val file = File.createTempFile("picked", ".png", context.cacheDir)
    file.outputStream().use { output ->
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    return Uri.fromFile(file).toString()
  }

  /** Reads the size and type of encoded bytes without decoding the pixels. */
  private fun decodeBounds(bytes: ByteArray): BitmapFactory.Options {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return options
  }
}
