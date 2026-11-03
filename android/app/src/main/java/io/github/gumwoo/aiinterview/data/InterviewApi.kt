package io.github.gumwoo.aiinterview.data

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** 서버의 면접 API. 형식은 계획서 7.1을 따른다. */
interface InterviewApi {
    /** 서버 연결과 토큰을 함께 확인한다. */
    suspend fun checkConnection()

    suspend fun startSession(request: StartSessionRequest): SessionResponse

    suspend fun submitAnswer(sessionId: Long, request: AnswerRequest): AnswerResponse

    suspend fun requestHint(sessionId: Long, turnId: Long): HintResponse

    suspend fun endSession(sessionId: Long): SummaryResponse
}

/** 지난 면접 기록 API. */
interface HistoryApi {
    suspend fun history(from: LocalDate, to: LocalDate): List<HistoryItem>

    suspend fun session(sessionId: Long): SessionDetail

    suspend fun deleteSession(sessionId: Long)
}

@Serializable
data class HistoryItem(
    val sessionId: Long,
    val source: String,
    val startedAt: String,
    val endedAt: String? = null,
    val answeredCount: Long,
    val verdictCounts: Map<String, Long> = emptyMap(),
)

@Serializable
data class SessionDetail(
    val sessionId: Long,
    val turns: List<SessionTurnDetail> = emptyList(),
)

@Serializable
data class SessionTurnDetail(
    val turnId: Long,
    val questionText: String,
    val answerText: String? = null,
    val processingStatus: String,
    val verdict: String? = null,
    val feedbackToSpeak: String? = null,
    val review: Boolean = false,
)

@Serializable
data class StartSessionRequest(
    val source: String,
    val scheduledDate: String,
    val topics: List<String>,
    val difficulty: String,
    val durationMinutes: Int,
    val reviewRatio: Double,
    val clientRequestId: String,
)

@Serializable
data class SessionResponse(
    val sessionId: Long,
    val status: String,
    val turnId: Long? = null,
    val questionText: String? = null,
    val durationLimitSeconds: Long,
)

@Serializable
data class AnswerRequest(
    val turnId: Long,
    val recognizedText: String,
    val clientRequestId: String,
)

@Serializable
data class AnswerResponse(
    val sessionId: Long,
    val sessionStatus: String,
    val turnId: Long,
    val verdict: String,
    val feedbackToSpeak: String,
    val nextAction: String,
    val correctPoints: List<String> = emptyList(),
    val missingPoints: List<String> = emptyList(),
    val incorrectPoints: List<String> = emptyList(),
    val nextTurnId: Long? = null,
    val nextQuestionText: String? = null,
)

@Serializable
data class HintRequest(val turnId: Long)

@Serializable
data class HintResponse(val turnId: Long, val hintText: String)

@Serializable
data class SummaryResponse(
    val sessionId: Long,
    val status: String,
    val answeredCount: Int,
    val verdictCounts: Map<String, Long> = emptyMap(),
    val missingPoints: List<String> = emptyList(),
    val incorrectPoints: List<String> = emptyList(),
)

/** 서버 호출 실패. [retryable]이면 같은 요청을 다시 보내도 된다. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    abstract val retryable: Boolean

    class Network(cause: Throwable) : ApiException("network", cause) {
        override val retryable = true
    }

    class Unauthorized : ApiException("unauthorized") {
        override val retryable = false
    }

    /** 서버가 돌려준 오류. 메시지는 서버가 사용자에게 보여도 된다고 정한 내용이다. */
    class Server(val status: Int, val code: String, message: String) : ApiException(message) {
        override val retryable = status >= 500 || code == GRADING_IN_PROGRESS
    }

    companion object {
        const val GRADING_IN_PROGRESS = "GRADING_IN_PROGRESS"
    }
}
