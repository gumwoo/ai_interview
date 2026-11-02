package io.github.gumwoo.aiinterview.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android SpeechRecognizer로 한 번씩 듣는다. 기기 내 인식을 먼저 쓰고, 한국어를 처리하지 못하면 시스템 음성 서비스로 넘어간다.
 * 시스템 서비스를 쓰면 음성이 네트워크로 전송될 수 있어 [usedCloudRecognition]으로 알린다.
 */
class SpeechRecognitionController(context: Context) : SpeechInput {
    private val appContext = context.applicationContext
    private var active: SpeechRecognizer? = null
    private var onDeviceUnusable = false

    var usedCloudRecognition: Boolean = false
        private set

    /**
     * 기기 내 한국어 음성 팩이 있는지 보고, 없으면 내려받기를 요청한다. 받는 동안에는 시스템 음성 서비스로 인식한다.
     * 시스템이 내려받기 확인 창을 띄우므로 통화 중이 아니라 앱을 켤 때 부른다.
     */
    fun prepareKoreanPack() {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            Log.i(TAG, "on-device recognition is not available")
            return
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        recognizer.checkRecognitionSupport(
            recognitionIntent(),
            appContext.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val installed = LANGUAGE in support.installedOnDeviceLanguages
                    val available = LANGUAGE in support.supportedOnDeviceLanguages ||
                        LANGUAGE in support.pendingOnDeviceLanguages
                    Log.i(TAG, "korean pack installed=$installed available=$available")
                    if (installed) {
                        onDeviceUnusable = false
                    } else if (available) {
                        recognizer.triggerModelDownload(recognitionIntent())
                    }
                    recognizer.destroy()
                }

                override fun onError(error: Int) {
                    Log.i(TAG, "recognition support check failed: $error")
                    recognizer.destroy()
                }
            },
        )
    }

    override suspend fun listen(onPartial: (String) -> Unit): ListenResult = withContext(Dispatchers.Main) {
        if (!hasMicrophonePermission()) {
            return@withContext ListenResult.Unavailable
        }
        if (!onDeviceUnusable && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            when (val outcome = listenWith(SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext), onPartial)) {
                is Outcome.Result -> {
                    usedCloudRecognition = false
                    return@withContext outcome.result
                }

                Outcome.EngineUnusable -> onDeviceUnusable = true
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return@withContext ListenResult.Unavailable
        }
        usedCloudRecognition = true
        when (val outcome = listenWith(SpeechRecognizer.createSpeechRecognizer(appContext), onPartial)) {
            is Outcome.Result -> outcome.result
            Outcome.EngineUnusable -> ListenResult.Unavailable
        }
    }

    private suspend fun listenWith(recognizer: SpeechRecognizer, onPartial: (String) -> Unit): Outcome =
        suspendCancellableCoroutine { continuation ->
            active = recognizer
            var finished = false

            fun finish(outcome: Outcome) {
                if (finished) return
                finished = true
                active = null
                recognizer.destroy()
                if (continuation.isActive) continuation.resume(outcome)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onPartialResults(partialResults: Bundle?) {
                    firstResult(partialResults)?.let(onPartial)
                }

                override fun onResults(results: Bundle?) {
                    val text = firstResult(results)
                    finish(Outcome.Result(if (text.isNullOrBlank()) ListenResult.NotUnderstood else ListenResult.Recognized(text)))
                }

                override fun onError(error: Int) {
                    finish(outcomeOf(error))
                }
            })
            continuation.invokeOnCancellation { recognizer.destroy() }
            recognizer.startListening(recognitionIntent())
        }

    override fun stopListening() {
        active?.stopListening()
    }

    private fun outcomeOf(error: Int): Outcome = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            Outcome.Result(ListenResult.NotUnderstood)

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Outcome.Result(ListenResult.Unavailable)

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
        -> Outcome.EngineUnusable

        else -> Outcome.Result(ListenResult.NotUnderstood)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // 설명 중 잠깐 멈춰도 끊기지 않게 한다. 인식 서비스가 무시할 수 있어 실기기에서 확인한다.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MILLIS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MILLIS)
    }

    private fun hasMicrophonePermission() =
        appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private sealed interface Outcome {
        data class Result(val result: ListenResult) : Outcome

        data object EngineUnusable : Outcome
    }

    private companion object {
        const val TAG = "SpeechRecognition"
        const val LANGUAGE = "ko-KR"
        const val SILENCE_MILLIS = 4000L
    }
}
