package com.veloapp

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListener : NotificationListenerService() {
    companion object {
        var songTitle: String = ""
        var artistName: String = ""
        var albumName: String = ""
        var albumArt: Bitmap? = null
        var isPlaying: Boolean = false
        var currentController: MediaController? = null
        var onMusicUpdate: (() -> Unit)? = null

        fun refreshFromSessions(manager: MediaSessionManager, listenerComponent: android.content.ComponentName) {
            try {
                val sessions = manager.getActiveSessions(listenerComponent)
                if (sessions.isNotEmpty()) {
                    val controller = sessions[0]
                    currentController = controller
                    val meta = controller.metadata
                    songTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    artistName = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    albumName = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                    albumArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    val state = controller.playbackState
                    isPlaying = state?.state == android.media.session.PlaybackState.STATE_PLAYING
                } else {
                    currentController = null
                    songTitle = ""
                    artistName = ""
                    albumName = ""
                    albumArt = null
                    isPlaying = false
                }
                onMusicUpdate?.invoke()
            } catch (_: Exception) {}
        }
    }
}
