package com.example.audio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.model.PartyTrack
import com.example.model.TrackSourceType

/**
 * Automatically detects audio/music files stored on the user's phone via Android's MediaStore
 * and watches for newly downloaded or copied music files.
 */
class DeviceMusicScanner(
    private val context: Context,
    private val onMediaStoreChanged: () -> Unit
) {
    private var observerRegistered = false

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onMediaStoreChanged()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            onMediaStoreChanged()
        }
    }

    fun hasAudioLibraryPermission(): Boolean {
        val perm = requiredAudioPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun startObserving() {
        if (observerRegistered || !hasAudioLibraryPermission()) return
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
            observerRegistered = true
        }
    }

    fun stopObserving() {
        if (!observerRegistered) return
        runCatching { context.contentResolver.unregisterContentObserver(mediaObserver) }
        observerRegistered = false
    }

    fun scanAllAudioTracks(): List<PartyTrack> {
        if (!hasAudioLibraryPermission()) return emptyList()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') " +
            "AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("5000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val results = mutableListOf<PartyTrack>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val rawTitle = cursor.getString(titleCol)?.trim().orEmpty()
                    val displayName = cursor.getString(displayNameCol)?.trim().orEmpty().substringBeforeLast('.')
                    val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
                    val rawAlbum = cursor.getString(albumCol)?.trim().orEmpty()
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)

                    val title = rawTitle.ifBlank { displayName.ifBlank { "Audio Track #$mediaId" } }
                    val artist = if (rawArtist.isBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }
                    val album = if (rawAlbum.isBlank() || rawAlbum.equals("<unknown>", ignoreCase = true)) {
                        "Phone Music"
                    } else {
                        rawAlbum
                    }

                    results.add(
                        PartyTrack(
                            id = "media_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            bpm = 120,
                            durationMs = durationMs,
                            sourceType = TrackSourceType.LOCAL_FILE,
                            uriString = contentUri.toString(),
                            isAutoScanned = true
                        )
                    )
                }
            }
        }
        return results
    }

    companion object {
        fun requiredAudioPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
    }
}
