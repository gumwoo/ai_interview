package io.github.gumwoo.aiinterview.presentation.interview

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gumwoo.aiinterview.audio.ListenResult
import io.github.gumwoo.aiinterview.audio.SpeechInput
import io.github.gumwoo.aiinterview.audio.SpeechOutput
import io.github.gumwoo.aiinterview.audio.sentenceEnd
import io.github.gumwoo.aiinterview.data.AnswerRequest
import io.github.gumwoo.aiinterview.data.AnswerResponse
import io.github.gumwoo.aiinterview.data.ApiException
import io.github.gumwoo.aiinterview.data.InterviewApi
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.data.SessionResponse
import io.github.gumwoo.aiinterview.data.StartSessionRequest
import io.github.gumwoo.aiinterview.data.SummaryResponse
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InterviewPhase { IDLE, CONNECTING, SPEAKING, LISTENING, REVIEW, GRADING, FEEDBACK, ENDING, FINISHED }

/** 다시 시도 버튼이 어떤 동작을 되풀이할지 나타낸다. */
enum class RetryAction { START, SUBMIT, END }

sealed interface InterviewError {
    data object Network : InterviewError

    data object Unauthorized : InterviewError

    data class Server(val message: String) : InterviewError

    data object SpeechNotUnderstood : InterviewError

    data object SpeechUnavailable : InterviewError

    data object TtsFailed : InterviewError
}

/** 울리는 전화. [scheduledDate]가 있으면 그날 일정의 전화이고, [ringing]이면 알람으로 온 전화다. */
data class IncomingCall(val scheduledDate: LocalDate?, val ringing: Boolean) {
    companion object {
        val MANUAL = IncomingCall(scheduledDate = null, ringing = false)
    }
}

/** 통화에서 오간 말 한 줄. */
data class CallLine(val fromInterviewer: Boolean, val text: String)

data class InterviewUiState(
    val phase: InterviewPhase = InterviewPhase.IDLE,
    val incoming: IncomingCall? = null,
    val sessionId: Long? = null,
    val turnId: Long? = null,
    val questionText: String = "",
    val transcript: String = "",
    val verdict: String? = null,
    val feedback: String = "",
    val missingPoints: List<String> = emptyList(),
    val hint: String? = null,
    val error: InterviewError? = null,
    val retryAction: RetryAction? = null,
    val summary: SummaryResponse? = null,
    /** 여기까지 읽은 것으로 표시한다. null이면 진행 정보가 없어 글 전체를 밝게 보여 준다. */
    val spokenUpTo: Int? = null,
    val history: List<CallLine> = emptyList(),
    val callStartedAtMillis: Long? = null,
    val callDurationSeconds: Long = 0,
    val speakerOn: Boolean = true,
)

/**
 * 면접 통화 상태를 맡는다. 질문 읽기 → 듣기 → 확인 → 채점 → 피드백 읽기 → 다음 질문 순서로 움직인다.
 * 답변을 보낼 때 만든 요청 ID는 성공할 때까지 유지해, 응답을 못 받고 다시 보내도 서버가 한 번만 채점하게 한다.
 */
class InterviewViewModel(
    private val api: InterviewApi,
    private val output: SpeechOutput,
    private val input: SpeechInput,
    private val settings: () -> InterviewSettings,
    private val today: () -> LocalDate = LocalDate::now,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val _state = MutableStateFlow(InterviewUiState())
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private var startRequestId: String? = null
    private var pendingAnswer: AnswerRequest? = null
    private var lastAnswer: AnswerResponse? = null
    private var currentJob: Job? = null
    private var call: IncomingCall = IncomingCall.MANUAL

    fun showIncoming(call: IncomingCall) {
        _state.update { it.copy(incoming = call) }
    }

    fun dismissIncoming() {
        _state.update { it.copy(incoming = null) }
    }

    /** 울리던 전화를 받는다. 이때 처음으로 서버에 면접을 만든다. */
    fun answer() {
        call = _state.value.incoming ?: IncomingCall.MANUAL
        start()
    }

    fun start() {
        startRequestId = startRequestId ?: newRequestId()
        run {
            _state.value = InterviewUiState(phase = InterviewPhase.CONNECTING)
            val config = settings()
            val session = call(RetryAction.START) {
                startSession(config, requireNotNull(startRequestId))
            } ?: return@run
            startRequestId = null
            _state.update { it.copy(sessionId = session.sessionId, callStartedAtMillis = elapsedMillis()) }
            askQuestion(session.turnId, session.questionText.orEmpty())
        }
    }

    /** 그날 일정 면접이 이미 있으면(일정을 바꿔 또 울린 경우) 수동 면접으로 시작한다. */
    private suspend fun startSession(config: InterviewSettings, requestId: String): SessionResponse {
        val request = StartSessionRequest(
            source = SOURCE_MANUAL,
            scheduledDate = today().toString(),
            topics = config.topics,
            difficulty = config.difficulty,
            durationMinutes = config.durationMinutes,
            reviewRatio = InterviewSettings.REVIEW_RATIO,
            clientRequestId = requestId,
        )
        val date = call.scheduledDate ?: return api.startSession(request)
        return try {
            api.startSession(request.copy(source = SOURCE_SCHEDULED, scheduledDate = date.toString()))
        } catch (ex: ApiException.Server) {
            if (ex.code != SCHEDULED_SESSION_EXISTS) throw ex
            api.startSession(request.copy(clientRequestId = newRequestId()))
        }
    }

    /** 피드백을 읽는 중이면 피드백을, 아니면 질문을 다시 읽는다. */
    fun replay() {
        val answer = lastAnswer
        run {
            input.stopListening()
            if (_state.value.phase == InterviewPhase.FEEDBACK && answer != null) {
                handleAnswer(answer)
            } else {
                speakAndListen(_state.value.questionText)
            }
        }
    }

    /** 읽던 말을 멈추고 다음 순서로 넘어간다. */
    fun skipSpeech() {
        output.stop()
    }

    fun toggleSpeaker() {
        val speakerOn = output.setSpeakerOn(!_state.value.speakerOn)
        _state.update { it.copy(speakerOn = speakerOn) }
    }

    fun listen(append: Boolean = false) {
        run { listenOnce(append) }
    }

    fun stopListening() {
        input.stopListening()
    }

    fun onTranscriptChange(text: String) {
        _state.update { it.copy(transcript = text, error = null) }
    }

    fun submit() {
        val current = _state.value
        val turnId = current.turnId ?: return
        val text = current.transcript.trim()
        if (text.isEmpty()) return
        pendingAnswer = AnswerRequest(turnId, text, newRequestId())
        sendPendingAnswer()
    }

    fun retry() {
        when (_state.value.retryAction) {
            RetryAction.START -> start()
            RetryAction.SUBMIT -> sendPendingAnswer()
            RetryAction.END -> end()
            null -> Unit
        }
    }

    fun requestHint() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        val turnId = current.turnId ?: return
        viewModelScope.launch {
            try {
                val hint = api.requestHint(sessionId, turnId)
                _state.update { it.copy(hint = hint.hintText) }
            } catch (ex: ApiException) {
                _state.update { it.copy(error = errorOf(ex)) }
            }
        }
    }

    fun end() {
        val sessionId = _state.value.sessionId ?: return
        run {
            output.stop()
            input.stopListening()
            finish(sessionId)
        }
    }

    fun reset() {
        currentJob?.cancel()
        output.stop()
        if (!_state.value.speakerOn) {
            output.setSpeakerOn(true)
        }
        pendingAnswer = null
        lastAnswer = null
        startRequestId = null
        call = IncomingCall.MANUAL
        _state.value = InterviewUiState()
    }

    private fun sendPendingAnswer() {
        val request = pendingAnswer ?: return
        val sessionId = _state.value.sessionId ?: return
        run {
            _state.update { it.copy(phase = InterviewPhase.GRADING, error = null, retryAction = null, hint = null) }
            val answer = call(RetryAction.SUBMIT) { api.submitAnswer(sessionId, request) } ?: return@run
            pendingAnswer = null
            lastAnswer = answer
            _state.update {
                it.copy(
                    history = it.history + CallLine(false, request.recognizedText) + CallLine(true, answer.feedbackToSpeak),
                )
            }
            handleAnswer(answer)
        }
    }

    private suspend fun handleAnswer(answer: AnswerResponse) {
        _state.update {
            it.copy(
                phase = InterviewPhase.FEEDBACK,
                verdict = answer.verdict,
                feedback = answer.feedbackToSpeak,
                missingPoints = answer.missingPoints,
            )
        }
        speakOrWarn(answer.feedbackToSpeak)
        val nextTurn = answer.nextTurnId
        if (nextTurn != null) {
            askQuestion(nextTurn, answer.nextQuestionText.orEmpty())
        } else {
            _state.value.sessionId?.let { finish(it) }
        }
    }

    private suspend fun finish(sessionId: Long) {
        _state.update { it.copy(phase = InterviewPhase.ENDING, error = null, retryAction = null) }
        val summary = call(RetryAction.END) { api.endSession(sessionId) } ?: return
        _state.update { current ->
            val startedAt = current.callStartedAtMillis
            current.copy(
                phase = InterviewPhase.FINISHED,
                summary = summary,
                callDurationSeconds = if (startedAt == null) 0 else (elapsedMillis() - startedAt) / 1000,
            )
        }
    }

    private suspend fun askQuestion(turnId: Long?, question: String) {
        _state.update {
            it.copy(
                turnId = turnId,
                questionText = question,
                transcript = "",
                hint = null,
                error = null,
                history = it.history + CallLine(true, question),
            )
        }
        speakAndListen(question)
    }

    private suspend fun speakAndListen(text: String) {
        _state.update { it.copy(phase = InterviewPhase.SPEAKING) }
        speakOrWarn(text)
        listenOnce(append = false)
    }

    private suspend fun speakOrWarn(text: String) {
        _state.update { it.copy(spokenUpTo = sentenceEnd(text, 0)) }
        val spoken = output.speak(text) { position ->
            _state.update { it.copy(spokenUpTo = sentenceEnd(text, position)) }
        }
        _state.update { it.copy(spokenUpTo = null, error = if (spoken) it.error else InterviewError.TtsFailed) }
    }

    private suspend fun listenOnce(append: Boolean) {
        val before = if (append) _state.value.transcript.trim() else ""
        _state.update { it.copy(phase = InterviewPhase.LISTENING, transcript = before, error = keepTtsWarning(it.error)) }
        val result = input.listen { partial ->
            _state.update { it.copy(transcript = join(before, partial)) }
        }
        _state.update {
            when (result) {
                is ListenResult.Recognized ->
                    it.copy(phase = InterviewPhase.REVIEW, transcript = join(before, result.text), error = null)

                ListenResult.NotUnderstood ->
                    it.copy(phase = InterviewPhase.REVIEW, transcript = before, error = InterviewError.SpeechNotUnderstood)

                ListenResult.Unavailable ->
                    it.copy(phase = InterviewPhase.REVIEW, transcript = before, error = InterviewError.SpeechUnavailable)
            }
        }
    }

    private fun keepTtsWarning(error: InterviewError?) = error.takeIf { it == InterviewError.TtsFailed }

    private fun join(before: String, text: String) = if (before.isEmpty()) text else "$before $text"

    /** 이전 동작을 취소하고 새 동작을 시작한다. 한 번에 한 흐름만 돌게 한다. */
    private fun run(block: suspend () -> Unit) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch { block() }
    }

    private suspend fun <T> call(retryAction: RetryAction, request: suspend () -> T): T? = try {
        request()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ApiException) {
        val previousPhase = if (retryAction == RetryAction.SUBMIT) InterviewPhase.REVIEW else _state.value.phase
        _state.update {
            it.copy(
                phase = previousPhase,
                error = errorOf(ex),
                retryAction = retryAction.takeIf { ex.retryable },
            )
        }
        null
    }

    private fun errorOf(ex: ApiException): InterviewError = when (ex) {
        is ApiException.Network -> InterviewError.Network
        is ApiException.Unauthorized -> InterviewError.Unauthorized
        is ApiException.Server -> InterviewError.Server(ex.message.orEmpty())
    }

    override fun onCleared() {
        output.stop()
        if (!_state.value.speakerOn) {
            output.setSpeakerOn(true)
        }
    }

    private companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_SCHEDULED = "SCHEDULED"
        const val SCHEDULED_SESSION_EXISTS = "SCHEDULED_SESSION_EXISTS"
    }
}
