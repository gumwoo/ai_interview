package io.github.gumwoo.aiinterview

import android.app.Application
import io.github.gumwoo.aiinterview.audio.SpeechRecognitionController
import io.github.gumwoo.aiinterview.audio.TextToSpeechController
import io.github.gumwoo.aiinterview.data.HttpInterviewApi
import io.github.gumwoo.aiinterview.data.InterviewSettingsStore
import io.github.gumwoo.aiinterview.data.ServerConnection
import io.github.gumwoo.aiinterview.security.ApiTokenStore

/** 앱 전체에서 함께 쓰는 객체를 만든다. 객체 수가 적어 DI 라이브러리는 쓰지 않는다. */
class InterviewApplication : Application() {
    val settingsStore by lazy { InterviewSettingsStore(this) }
    val tokenStore by lazy { ApiTokenStore(this) }
    val speechOutput by lazy { TextToSpeechController(this) }
    val speechInput by lazy { SpeechRecognitionController(this) }
    val api by lazy {
        HttpInterviewApi({ ServerConnection(settingsStore.load().serverUrl, tokenStore.load().orEmpty()) })
    }
}
