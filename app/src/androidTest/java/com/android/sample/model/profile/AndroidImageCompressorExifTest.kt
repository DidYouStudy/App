// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [AndroidImageCompressor] with the device's real image decoder on a photo carrying an EXIF
 * rotation, as phone cameras write for portrait shots.
 */
@RunWith(AndroidJUnit4::class)
class AndroidImageCompressorExifTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  /**
   * A landscape picture tagged "rotate 90 degrees" comes out upright: shrunk to the limit, taller
   * than wide, with its left side (red) now at the top and its right side (blue) at the bottom.
   */
  @Test
  fun compress_appliesExifRotationAndShrinksToLimit() = runBlocking {
    val maxEdge = context.resources.getInteger(R.integer.profile_picture_max_edge_px)
    val uri = writeLandscapeJpegRotatedBy90(width = maxEdge * 2, height = maxEdge)

    val jpeg = AndroidImageCompressor(context).compress(uri).getOrThrow()

    val output = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    assertNotNull(output)
    assertEquals(maxEdge, output.height)
    assertEquals(maxEdge / 2, output.width)
    val top = output.getPixel(output.width / 2, output.height / 4)
    val bottom = output.getPixel(output.width / 2, output.height * 3 / 4)
    assertTrue(Color.red(top) > STRONG_CHANNEL_MIN && Color.blue(top) < WEAK_CHANNEL_MAX)
    assertTrue(Color.blue(bottom) > STRONG_CHANNEL_MIN && Color.red(bottom) < WEAK_CHANNEL_MAX)
  }

  /** Writes a JPEG, left half red and right half blue, tagged to be displayed rotated by 90°. */
  private fun writeLandscapeJpegRotatedBy90(width: Int, height: Int): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.RED)
    canvas.drawRect(
        width / 2f,
        0f,
        width.toFloat(),
        height.toFloat(),
        Paint().apply { color = Color.BLUE },
    )
    val file = File.createTempFile("picked", ".jpg", context.cacheDir)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, SOURCE_QUALITY, it) }
    ExifInterface(file.absolutePath).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      saveAttributes()
    }
    return Uri.fromFile(file).toString()
  }

  private companion object {
    /** A pure colour channel decodes above this value despite JPEG loss. */
    const val STRONG_CHANNEL_MIN = 200

    /** An absent colour channel decodes below this value despite JPEG loss. */
    const val WEAK_CHANNEL_MAX = 60

    /** Quality of the generated source photo. */
    const val SOURCE_QUALITY = 95
  }
}
