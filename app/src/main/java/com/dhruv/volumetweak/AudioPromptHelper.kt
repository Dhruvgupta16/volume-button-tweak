package com.dhruv.volumetweak

import android.content.Context
import android.os.BatteryManager
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioPromptHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            LogBuffer.log("[TTS] Init error: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsReady = true
            LogBuffer.log("[TTS] Audio prompts engine ready")
        } else {
            LogBuffer.log("[TTS] Init failed with code $status")
        }
    }

    fun speakTime() {
        try {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeStr = timeFormat.format(Date())
            speak("It's $timeStr")
        } catch (e: Exception) {
            LogBuffer.log("[TTS] Time speak error: ${e.message}")
        }
    }

    fun speakBattery() {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level >= 0) {
                speak("Battery is at $level percent")
            } else {
                speak("Battery level unavailable")
            }
        } catch (e: Exception) {
            LogBuffer.log("[TTS] Battery speak error: ${e.message}")
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VolumeTweakAudioPrompt")
            LogBuffer.log("[TTS] Spoke: \"$text\"")
        } else {
            LogBuffer.log("[TTS] Not ready, retrying init...")
            try {
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.getDefault()
                        isTtsReady = true
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VolumeTweakAudioPrompt")
                    }
                }
            } catch (e: Exception) {
                LogBuffer.log("[TTS] Speak fallback error: ${e.message}")
            }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (e: Exception) {
            // Ignore
        }
    }
}
