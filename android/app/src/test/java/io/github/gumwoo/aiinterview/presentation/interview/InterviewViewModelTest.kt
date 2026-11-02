package io.github.gumwoo.aiinterview.presentation.interview

import io.github.gumwoo.aiinterview.audio.ListenResult
import io.github.gumwoo.aiinterview.audio.SpeechInput
import io.github.gumwoo.aiinterview.audio.SpeechOutput
import io.github.gumwoo.aiinterview.data.AnswerRequest
import io.github.gumwoo.aiinterview.data.AnswerResponse
import io.github.gumwoo.aiinterview.data.ApiException
import io.github.gumwoo.aiinterview.data.HintResponse
import io.github.gumwoo.aiinterview.data.InterviewApi
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.data.SessionResponse
import io.github.gumwoo.aiinterview.data.StartSessionRequest
import io.github.gumwoo.aiinterview.data.SummaryResponse
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeApi()
    private val output = FakeOutput()
    private val input = FakeInput()
    private var nextId = 0
    private var clock = 0L

    private fun viewModel() = InterviewViewModel(
        api = api,
        output = output,
        input = input,
        settings = { InterviewSettings(serverUrl = "https://server", topics = listOf("Kubernetes")) },
        today = { LocalDate.parse("2026-11-02") },
        newRequestId = { "request-${++nextId}" },
        elapsedMillis = { clock },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reads question then listens and waits for confirmation`() = runTest {
        input.results += ListenResult.Recognized("제 답변입니다")
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(listOf("질문1"), output.spoken)
        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
        assertEquals("제 답변입니다", vm.state.value.transcript)
        assertEquals("2026-11-02", api.started.single().scheduledDate)
        assertEquals(listOf("Kubernetes"), api.started.single().topics)
        assertTrue(api.answers.isEmpty())
    }

    @Test
    fun `sends edited answer then speaks feedback and next question`() = runTest {
        input.results += ListenResult.Recognized("초안")
        input.results += ListenResult.Recognized("둘째 답변")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.onTranscriptChange("고친 답변")
        vm.submit()
        advanceUntilIdle()

        assertEquals("고친 답변", api.answers.single().recognizedText)
        assertEquals(10L, api.answers.single().turnId)
        assertEquals(listOf("질문1", "피드백", "질문2"), output.spoken)
        val state = vm.state.value
        assertEquals("질문2", state.questionText)
        assertEquals(11L, state.turnId)
        assertEquals("둘째 답변", state.transcript)
        assertEquals("PARTIAL", state.verdict)
    }

    @Test
    fun `resends same request id after network failure`() = runTest {
        input.results += ListenResult.Recognized("답변")
        input.results += ListenResult.Recognized("다음")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        api.answerFailure = ApiException.Network(IOException("끊김"))

        vm.submit()
        advanceUntilIdle()

        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
        assertEquals(InterviewError.Network, vm.state.value.error)
        assertEquals(RetryAction.SUBMIT, vm.state.value.retryAction)
        assertEquals("답변", vm.state.value.transcript)

        api.answerFailure = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, api.answerAttempts.size)
        assertEquals(api.answerAttempts[0].clientRequestId, api.answerAttempts[1].clientRequestId)
        assertNull(vm.state.value.retryAction)
        assertEquals("질문2", vm.state.value.questionText)
    }

    @Test
    fun `new answer gets new request id`() = runTest {
        input.results += ListenResult.Recognized("하나")
        input.results += ListenResult.Recognized("둘")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        assertEquals(2, api.answers.map { it.clientRequestId }.distinct().size)
    }

    @Test
    fun `does not offer retry when token is wrong`() = runTest {
        api.startFailure = ApiException.Unauthorized()
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(InterviewError.Unauthorized, vm.state.value.error)
        assertNull(vm.state.value.retryAction)
    }

    @Test
    fun `retries start with same request id`() = runTest {
        input.results += ListenResult.Recognized("답변")
        api.startFailure = ApiException.Server(503, "LLM_UNAVAILABLE", "일시 장애")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        assertEquals(RetryAction.START, vm.state.value.retryAction)

        api.startFailure = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, api.startAttempts.size)
        assertEquals(api.startAttempts[0].clientRequestId, api.startAttempts[1].clientRequestId)
        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
    }

    @Test
    fun `allows typing when speech is not understood`() = runTest {
        input.results += ListenResult.NotUnderstood
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
        assertEquals(InterviewError.SpeechNotUnderstood, vm.state.value.error)

        vm.onTranscriptChange("글자로 쓴 답변")
        input.results += ListenResult.Recognized("다음")
        vm.submit()
        advanceUntilIdle()

        assertEquals("글자로 쓴 답변", api.answers.single().recognizedText)
    }

    @Test
    fun `allows typing when speech is unavailable`() = runTest {
        input.results += ListenResult.Unavailable
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
        assertEquals(InterviewError.SpeechUnavailable, vm.state.value.error)
    }

    @Test
    fun `continues when text to speech fails`() = runTest {
        output.works = false
        input.results += ListenResult.Recognized("답변")
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(InterviewPhase.REVIEW, vm.state.value.phase)
        assertEquals("답변", vm.state.value.transcript)
    }

    @Test
    fun `appends to existing answer when listening again`() = runTest {
        input.results += ListenResult.Recognized("앞부분")
        input.results += ListenResult.Recognized("뒷부분")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.listen(append = true)
        advanceUntilIdle()

        assertEquals("앞부분 뒷부분", vm.state.value.transcript)
    }

    @Test
    fun `keeps existing text when appended speech fails`() = runTest {
        input.results += ListenResult.Recognized("앞부분")
        input.results += ListenResult.NotUnderstood
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.listen(append = true)
        advanceUntilIdle()

        assertEquals("앞부분", vm.state.value.transcript)
    }

    @Test
    fun `shows partial speech while listening`() = runTest {
        input.partials += "중간"
        input.results += ListenResult.Recognized("끝")
        val vm = viewModel()
        var sawPartial = false
        input.onPartialDelivered = { sawPartial = vm.state.value.transcript == "중간" }

        vm.start()
        advanceUntilIdle()

        assertTrue(sawPartial)
        assertEquals("끝", vm.state.value.transcript)
    }

    @Test
    fun `finishes with summary when session completes`() = runTest {
        input.results += ListenResult.Recognized("답변")
        api.nextTurn = null
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertEquals(InterviewPhase.FINISHED, vm.state.value.phase)
        assertEquals(1, vm.state.value.summary?.answeredCount)
        assertEquals(listOf("질문1", "피드백"), output.spoken)
    }

    @Test
    fun `ends interview on request and stops audio`() = runTest {
        input.results += ListenResult.Recognized("답변")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.end()
        advanceUntilIdle()

        assertEquals(InterviewPhase.FINISHED, vm.state.value.phase)
        assertTrue(output.stopped)
        assertTrue(api.ended)
    }

    @Test
    fun `retries ending after failure`() = runTest {
        input.results += ListenResult.Recognized("답변")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        api.endFailure = ApiException.Network(IOException("끊김"))

        vm.end()
        advanceUntilIdle()

        assertEquals(InterviewPhase.ENDING, vm.state.value.phase)
        assertEquals(RetryAction.END, vm.state.value.retryAction)

        api.endFailure = null
        vm.retry()
        advanceUntilIdle()

        assertEquals(InterviewPhase.FINISHED, vm.state.value.phase)
    }

    @Test
    fun `new answer after a non retryable failure gets new request id`() = runTest {
        input.results += ListenResult.Recognized("답변")
        input.results += ListenResult.Recognized("다음")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        api.answerFailure = ApiException.Server(429, "LLM_QUOTA_EXCEEDED", "상한 초과")

        vm.submit()
        advanceUntilIdle()

        assertNull(vm.state.value.retryAction)
        assertEquals("상한 초과", (vm.state.value.error as InterviewError.Server).message)

        api.answerFailure = null
        vm.submit()
        advanceUntilIdle()

        assertEquals(2, api.answerAttempts.map { it.clientRequestId }.distinct().size)
    }

    @Test
    fun `marks spoken sentence while speaking and clears it afterwards`() = runTest {
        input.results += ListenResult.Recognized("답변")
        output.progress = listOf(0)
        val vm = viewModel()
        var seen: Int? = null
        output.onProgressDelivered = { seen = vm.state.value.spokenUpTo }

        vm.start()
        advanceUntilIdle()

        assertEquals("질문1".length, seen)
        assertNull(vm.state.value.spokenUpTo)
    }

    @Test
    fun `keeps conversation history in order`() = runTest {
        input.results += ListenResult.Recognized("내 답변")
        input.results += ListenResult.Recognized("다음")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CallLine(true, "질문1"),
                CallLine(false, "내 답변"),
                CallLine(true, "피드백"),
                CallLine(true, "질문2"),
            ),
            vm.state.value.history,
        )
    }

    @Test
    fun `replays question without adding to history`() = runTest {
        input.results += ListenResult.Recognized("하나")
        input.results += ListenResult.Recognized("둘")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.replay()
        advanceUntilIdle()

        assertEquals(listOf("질문1", "질문1"), output.spoken)
        assertEquals(1, vm.state.value.history.size)
    }

    @Test
    fun `skip stops current speech`() = runTest {
        val vm = viewModel()

        vm.skipSpeech()

        assertTrue(output.stopped)
    }

    @Test
    fun `switches to earpiece and restores speaker on reset`() = runTest {
        val vm = viewModel()

        vm.toggleSpeaker()
        assertEquals(false, vm.state.value.speakerOn)

        vm.reset()

        assertEquals(true, output.speakerOn)
        assertEquals(true, vm.state.value.speakerOn)
    }

    @Test
    fun `stays on speaker when device has no earpiece`() = runTest {
        output.hasEarpiece = false
        val vm = viewModel()

        vm.toggleSpeaker()

        assertEquals(true, vm.state.value.speakerOn)
    }

    @Test
    fun `records call duration when finished`() = runTest {
        input.results += ListenResult.Recognized("답변")
        clock = 1_000
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        clock = 91_000

        vm.end()
        advanceUntilIdle()

        assertEquals(90L, vm.state.value.callDurationSeconds)
    }

    @Test
    fun `reset returns to idle`() = runTest {
        input.results += ListenResult.Recognized("답변")
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.reset()

        assertEquals(InterviewUiState(), vm.state.value)
    }

    private class FakeOutput : SpeechOutput {
        val spoken = mutableListOf<String>()
        var works = true
        var stopped = false
        var progress = emptyList<Int>()
        var onProgressDelivered: () -> Unit = {}
        var hasEarpiece = true
        var speakerOn = true

        override suspend fun speak(text: String, onProgress: (Int) -> Unit): Boolean {
            spoken += text
            progress.forEach {
                onProgress(it)
                onProgressDelivered()
            }
            return works
        }

        override fun setSpeakerOn(on: Boolean): Boolean {
            speakerOn = on || !hasEarpiece
            return speakerOn
        }

        override fun stop() {
            stopped = true
        }
    }

    private class FakeInput : SpeechInput {
        val results = ArrayDeque<ListenResult>()
        val partials = ArrayDeque<String>()
        var onPartialDelivered: () -> Unit = {}

        override suspend fun listen(onPartial: (String) -> Unit): ListenResult {
            partials.removeFirstOrNull()?.let {
                onPartial(it)
                onPartialDelivered()
            }
            return results.removeFirstOrNull() ?: ListenResult.NotUnderstood
        }

        override fun stopListening() = Unit
    }

    private class FakeApi : InterviewApi {
        val started = mutableListOf<StartSessionRequest>()
        val startAttempts = mutableListOf<StartSessionRequest>()
        val answers = mutableListOf<AnswerRequest>()
        val answerAttempts = mutableListOf<AnswerRequest>()
        var startFailure: ApiException? = null
        var answerFailure: ApiException? = null
        var nextTurn: Long? = 11L
        var endFailure: ApiException? = null
        var ended = false

        override suspend fun checkConnection() = Unit

        override suspend fun startSession(request: StartSessionRequest): SessionResponse {
            startAttempts += request
            startFailure?.let { throw it }
            started += request
            return SessionResponse(1L, "ASKING", 10L, "질문1", 900)
        }

        override suspend fun submitAnswer(sessionId: Long, request: AnswerRequest): AnswerResponse {
            answerAttempts += request
            answerFailure?.let { throw it }
            answers += request
            return AnswerResponse(
                sessionId = 1L,
                sessionStatus = if (nextTurn == null) "COMPLETED" else "ASKING",
                turnId = request.turnId,
                verdict = "PARTIAL",
                feedbackToSpeak = "피드백",
                nextAction = "FOLLOW_UP",
                missingPoints = listOf("빠진 내용"),
                nextTurnId = nextTurn,
                nextQuestionText = nextTurn?.let { "질문2" },
            )
        }

        override suspend fun requestHint(sessionId: Long, turnId: Long) = HintResponse(turnId, "힌트")

        override suspend fun endSession(sessionId: Long): SummaryResponse {
            endFailure?.let { throw it }
            ended = true
            return SummaryResponse(1L, "COMPLETED", 1, mapOf("PARTIAL" to 1L))
        }
    }
}
