package io.github.gumwoo.aiinterview.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/** Android TextToSpeech로 한국어 문장을 읽는다. 읽는 동안 다른 소리를 잠시 줄이도록 오디오 포커스를 잡는다. */
class TextToSpeechController(context: Context) : SpeechOutput {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speakerAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val earpieceAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speakerAttributes)
        .build()

    private val ready = CompletableDeferred<Boolean>()
    private val engine: TextToSpeech = TextToSpeech(appContext) { status ->
        val usable = status == TextToSpeech.SUCCESS && configureEngine()
        ready.complete(usable)
    }

    private fun configureEngine(): Boolean {
        engine.setAudioAttributes(speakerAttributes)
        return engine.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
    }

    override suspend fun speak(text: String, onProgress: (Int) -> Unit): Boolean {
        if (!ready.await()) {
            return false
        }
        audioManager.requestAudioFocus(focusRequest)
        return try {
            speakUtterance(text, onProgress)
        } finally {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    /** 음성 엔진마다 읽는 위치를 알려 주는지가 달라, 문장별로 나눠 읽어 자막 위치를 맞춘다. */
    private suspend fun speakUtterance(text: String, onProgress: (Int) -> Unit): Boolean =
        suspendCancellableCoroutine { continuation ->
            val batch = UUID.randomUUID().toString()
            val sentences = splitSentences(text).mapIndexed { index, (offset, sentence) ->
                Triple("$batch-$index", offset, sentence)
            }
            val offsets = sentences.associate { (id, offset, _) -> id to offset }
            val lastId = sentences.lastOrNull()?.first
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    offsets[id]?.let(onProgress)
                }

                override fun onDone(id: String?) {
                    if (id == lastId && continuation.isActive) continuation.resume(true)
                }

                override fun onStop(id: String?, interrupted: Boolean) {
                    if (id in offsets && continuation.isActive) continuation.resume(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id in offsets && continuation.isActive) continuation.resume(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id in offsets && continuation.isActive) continuation.resume(false)
                }
            })
            continuation.invokeOnCancellation { engine.stop() }
            if (sentences.isEmpty()) {
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }
            sentences.forEachIndexed { index, (id, _, sentence) ->
                val queue = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(sentence, queue, null, id) != TextToSpeech.SUCCESS && continuation.isActive) {
                    engine.stop()
                    continuation.resume(false)
                }
            }
        }

    override fun stop() {
        engine.stop()
    }

    /** 수화기로 내보내려면 통화 모드여야 한다. 기본은 스피커이고, 수화기가 없는 기기에서는 스피커로 남는다. */
    override fun setSpeakerOn(on: Boolean): Boolean {
        if (on) {
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
            engine.setAudioAttributes(speakerAttributes)
            return true
        }
        val earpiece = audioManager.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            ?: return true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.setCommunicationDevice(earpiece)
        engine.setAudioAttributes(earpieceAttributes)
        return false
    }
}
