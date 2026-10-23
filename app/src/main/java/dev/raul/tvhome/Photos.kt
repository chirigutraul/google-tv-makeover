package dev.raul.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Where push-screensavers.sh copies photos on the TV. */
private val PHOTO_DIR = File(Environment.getExternalStorageDirectory(), "Pictures/Screensavers")
private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** A background photo, decoded only when shown so the TV holds one or two in memory at a time. */
sealed interface PhotoSource {
    val caption: String

    data class OnDisk(val file: File) : PhotoSource {
        override val caption = captionFromName(file.nameWithoutExtension)
    }

    /** No photos pushed yet: the home screen shows a dark gradient instead. */
    data object Default : PhotoSource {
        override val caption = ""
    }
}

fun listPhotos(): List<PhotoSource> {
    val files = runCatching {
        PHOTO_DIR.listFiles { f -> f.isFile && f.extension.lowercase() in PHOTO_EXTENSIONS }
    }.getOrNull().orEmpty().sortedBy { it.name }
    return if (files.isEmpty()) listOf(PhotoSource.Default) else files.map { PhotoSource.OnDisk(it) }
}

/** Decodes at most 1920 px wide, in RGB_565 to halve memory (photos have no transparency). */
fun decodePhoto(context: Context, source: PhotoSource): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
    when (source) {
        is PhotoSource.OnDisk -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
            opts.inSampleSize = sample
            BitmapFactory.decodeFile(source.file.path, opts)
        }
        PhotoSource.Default -> null
    }?.asImageBitmap()
}.getOrNull()

/** "antelope_canyon-arizona" -> "Antelope canyon arizona" */
private fun captionFromName(name: String): String =
    name.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }
