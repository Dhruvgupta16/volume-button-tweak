package com.dhruv.volumetweak

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        var instance: MediaNotificationListener? = null
        private var lastNotificationSummary: String? = null

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val enabledListeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val myComponent = ComponentName(context, MediaNotificationListener::class.java).flattenToString()
                enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        private fun getActiveMediaController(context: Context): MediaController? {
            return try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                    ?: return null
                val component = ComponentName(context, MediaNotificationListener::class.java)
                val controllers = manager.getActiveSessions(component)
                if (controllers.isNullOrEmpty()) return null

                // Prioritize the controller that is actively in PLAYING state
                controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: controllers.firstOrNull()
            } catch (e: Exception) {
                LogBuffer.log("[MEDIA] MediaController query: ${e.message}")
                null
            }
        }

        fun seekActiveSession(context: Context, deltaSeconds: Int): Boolean {
            val controller = getActiveMediaController(context) ?: return false
            val playbackState = controller.playbackState ?: return false
            val currentPos = playbackState.position
            val targetPos = (currentPos + deltaSeconds * 1000L).coerceAtLeast(0L)

            return try {
                controller.transportControls.seekTo(targetPos)
                LogBuffer.log("[MEDIA SEEK] Jumped ${deltaSeconds}s -> ${targetPos / 1000}s on ${controller.packageName}")
                true
            } catch (e: Exception) {
                LogBuffer.log("[MEDIA SEEK ERROR] ${e.message}")
                false
            }
        }

        fun getActivePlayingPackage(context: Context): String? {
            return getActiveMediaController(context)?.packageName
        }

        fun getCurrentTrackInfo(context: Context): Pair<String, String>? {
            val controller = getActiveMediaController(context) ?: return null
            val metadata = controller.metadata ?: return null

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "Unknown Track"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: "Unknown Artist"

            return Pair(title, artist)
        }

        fun likeCurrentTrack(context: Context): Boolean {
            val controller = getActiveMediaController(context) ?: return false
            return try {
                // Standard MediaSession Rating API
                val heartRating = Rating.newHeartRating(true)
                controller.transportControls.setRating(heartRating)
                // Also trigger custom action if app uses custom like hooks
                controller.transportControls.sendCustomAction("ACTION_LIKE", null)
                LogBuffer.log("[MEDIA] Liked track on ${controller.packageName}")
                true
            } catch (e: Exception) {
                LogBuffer.log("[MEDIA LIKE NOTE] ${e.message}")
                false
            }
        }

        fun getLastNotificationText(): String? {
            return lastNotificationSummary
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        LogBuffer.log("[MEDIA] Notification & MediaSession Controller Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        LogBuffer.log("[MEDIA] Notification & MediaSession Controller Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName || sbn.isOngoing) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        if (!title.isNullOrBlank() && !text.isNullOrBlank()) {
            lastNotificationSummary = "$title says: $text"
        }
    }
}
