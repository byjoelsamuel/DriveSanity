package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * The photo behind a share picture. A picture picked from the gallery is copied into the app, shrunk
 * and turned the right way up, so it keeps working when the original moves and nothing outside the app
 * is left open. It stays on the phone; "Remove" deletes the copy.
 */
object ShareBackground {
    private const val MAX_SIDE = 2400
    @Volatile private var cached: Bitmap? = null
    @Volatile private var cachedStamp = 0L

    private fun file(ctx: Context) = File(ctx.filesDir, "share_background.jpg")

    fun exists(ctx: Context) = file(ctx).exists()

    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        if (cached == null || cachedStamp != f.lastModified()) {
            cached = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
            cachedStamp = f.lastModified()
        }
        return cached
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        cached = null
    }

    /** Copies the picked photo in. False if it couldn't be read as an image. */
    fun save(ctx: Context, uri: Uri): Boolean = runCatching {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val turn = resolver.openInputStream(uri)?.use { quarterTurns(it) } ?: 0f
        val upright = if (turn == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(turn) }, true)
        file(ctx).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        cached = null
        true
    }.getOrDefault(false)

    private fun max(a: Int, b: Int) = if (a > b) a else b

    private fun quarterTurns(stream: java.io.InputStream): Float = when (
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
}
