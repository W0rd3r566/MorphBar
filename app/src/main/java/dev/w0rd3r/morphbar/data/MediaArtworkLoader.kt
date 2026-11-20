package dev.w0rd3r.morphbar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.net.Uri
import android.util.Log

/** Local cover extraction; remote HTTP artwork intentionally unsupported. */
internal object MediaArtworkLoader {
    fun load(context: Context, metadata: MediaMetadata, fallback: Bitmap?): Bitmap? {
        val embedded = runCatching {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }.getOrNull()
        if (embedded != null) return embedded.toIslandArtwork()
        val keys = arrayOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
        for (key in keys) {
            val uriStr = runCatching { metadata.getString(key) }.getOrNull() ?: continue
            if (!ArtworkRequestIdentity.isTrustedLocalUri(uriStr)) continue
            val uri = Uri.parse(uriStr)
            val image = runCatching { decodeSampled(context, uri) }
                .onFailure { Log.d("MorphBarArtwork", "Local URI artwork not available", it) }
                .getOrNull()
            if (image != null) return image.toIslandArtwork()
        }
        return fallback?.toIslandArtwork()
    }

    private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }
}
