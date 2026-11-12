package io.github.byjoelsamuel.drivesanity

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Getting a finished card out of the app: the share sheet, straight to Instagram, or into the photo library. */
object ShareImage {
    const val INSTAGRAM = "com.instagram.android"

    /** Writes the card to the app's cache and returns the file (they're cleared by Android when space is short). */
    fun write(ctx: Context, bmp: Bitmap, name: String): File {
        val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
        // Only keep the latest few.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private fun send(ctx: Context, file: File, pkg: String?): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { i -> if (pkg != null) { i.setPackage(pkg); ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    /** The share sheet, so it can go to any app. */
    fun share(ctx: Context, file: File) {
        ctx.startActivity(Intent.createChooser(send(ctx, file, null), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Straight into Instagram, which then asks whether it's for a story, a post or a message. False if it isn't installed. */
    fun toInstagram(ctx: Context, file: File): Boolean = try {
        ctx.startActivity(send(ctx, file, INSTAGRAM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /** Into Pictures/DriveSanity, from Android 10 on (earlier versions would need a storage permission). */
    fun saveToPhotos(ctx: Context, bmp: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DriveSanity")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
    }
}
