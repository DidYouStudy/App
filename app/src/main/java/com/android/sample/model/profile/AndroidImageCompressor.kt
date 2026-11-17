// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import com.android.sample.R
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ImageCompressor] built on Android's [ImageDecoder].
 *
 * The image is decoded directly at its final size, so a large photo is never held in memory at full
 * resolution. [ImageDecoder] also applies the EXIF rotation, so photos taken in portrait are not
 * stored sideways. The result is a JPEG small enough for the Storage size limit.
 *
 * The size limit and the JPEG quality are read once from [R.integer.profile_picture_max_edge_px]
 * and [R.integer.profile_picture_jpeg_quality], so they can be tuned without touching this class.
 *
 * @param context used to read those two resources and to open the picked image.
 * @param ioDispatcher dispatcher on which the image is read, decoded and encoded.
 */
class AndroidImageCompressor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImageCompressor {
  private val contentResolver: ContentResolver = context.contentResolver
  private val maxEdgePx = context.resources.getInteger(R.integer.profile_picture_max_edge_px)
  private val jpegQuality = context.resources.getInteger(R.integer.profile_picture_jpeg_quality)

  /**
   * Decodes the image at [uri], shrinks it so its longest side is at most the configured maximum
   * (smaller images keep their size), and encodes it as JPEG.
   *
   * @param uri location of the picked image.
   * @return [Result.success] containing the JPEG bytes; [Result.failure] containing a
   *   [ProfilePictureException] with [ProfilePictureError.INVALID_IMAGE] when it cannot be read.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun compress(uri: String): Result<ByteArray> {
    return try {
      Result.success(withContext(ioDispatcher) { decodeAndEncode(uri.toUri()) })
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Result.failure(ProfilePictureException(ProfilePictureError.INVALID_IMAGE, exception))
    }
  }

  /**
   * Reads, shrinks and encodes the image. Blocking: call it off the main thread.
   *
   * @param uri location of the picked image.
   * @return the JPEG bytes.
   * @throws Exception when the image cannot be opened or decoded.
   */
  private fun decodeAndEncode(uri: Uri): ByteArray {
    val source = ImageDecoder.createSource(contentResolver, uri)
    val bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          val longestEdge = max(info.size.width, info.size.height)
          if (longestEdge > maxEdgePx) {
            val scale = maxEdgePx.toFloat() / longestEdge
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
          }
          // A software bitmap can be compressed; a hardware one cannot.
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    return ByteArrayOutputStream().use { output ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
      bitmap.recycle()
      output.toByteArray()
    }
  }
}
