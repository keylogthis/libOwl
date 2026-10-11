package org.thebottle.libOwl.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max

/** Stores downscaled cover images in the app's private storage. Books only keep the file name. */
class CoverStore(context: Context) {
    private val appContext = context.applicationContext
    val dir: File = File(appContext.filesDir, "covers").apply { mkdirs() }

    fun file(name: String) = File(dir, name)
    fun delete(name: String) { file(name).delete() }

    /** Copies, rotates (per EXIF) and downsizes a picked image. Returns the new file name, or null on failure. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        process(minSide = 0) { appContext.contentResolver.openInputStream(uri) }
    }

    /** Tries each URL in order until one downloads as a usable image. Returns the new file name, or null. */
    suspend fun importUrl(urls: List<String>): String? = withContext(Dispatchers.IO) {
        for (url in urls) {
            val bytes = runCatching { download(url) }.getOrNull() ?: continue
            val name = process(minSide = MIN_DOWNLOADED_SIDE) { ByteArrayInputStream(bytes) }
            if (name != null) return@withContext name
        }
        null
    }

    private fun process(minSide: Int, open: () -> InputStream?): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0 || longest < minSide) return null

            var sample = 1
            while (longest / sample > MAX_SIDE * 2) sample *= 2

            var bmp = open()?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null

            val orientation = runCatching {
                open()?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            }
            val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
            if (scale < 1f) m.postScale(scale, scale)
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

            val name = UUID.randomUUID().toString() + ".jpg"
            file(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun download(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "libOwl/0.9.1 (Android personal book catalog app)")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) throw IOException("Image too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_SIDE = 1000
        /** Rejects the tiny placeholder images some services return when they have no cover. */
        const val MIN_DOWNLOADED_SIDE = 80
        const val MAX_DOWNLOAD_BYTES = 6 * 1024 * 1024
    }
}
