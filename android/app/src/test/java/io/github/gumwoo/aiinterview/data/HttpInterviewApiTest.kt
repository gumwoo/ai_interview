package io.github.gumwoo.aiinterview.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpInterviewApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpInterviewApi

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString()
        api = HttpInterviewApi({ ServerConnection(base, TOKEN) })
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sends token and parses session while ignoring unknown fields`() = runTest {
        respond(200, """{"sessionId":12,"status":"ASKING","turnId":87,"questionText":"질문","durationLimitSeconds":900,"turns":[]}""")

        val session = api.startSession(
            StartSessionRequest("MANUAL", "2026-11-02", listOf("Kubernetes"), "intermediate", 15, 0.4, "id-1"),
        )

        assertEquals(12L, session.sessionId)
        assertEquals("질문", session.questionText)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/sessions", recorded.url.encodedPath)
        assertEquals("Bearer $TOKEN", recorded.headers["Authorization"])
        assertTrue(recorded.body!!.utf8().contains("\"clientRequestId\":\"id-1\""))
    }

    @Test
    fun `reads history with date range`() = runTest {
        respond(
            200,
            """[{"sessionId":3,"source":"MANUAL","startedAt":"2026-11-03T00:00:00Z","endedAt":null,
                "answeredCount":2,"verdictCounts":{"WRONG":1,"CORRECT":1}}]""",
        )

        val items = api.history(java.time.LocalDate.parse("2025-11-03"), java.time.LocalDate.parse("2026-11-03"))

        assertEquals(3L, items.single().sessionId)
        assertEquals(1L, items.single().verdictCounts["WRONG"])
        val recorded = server.takeRequest()
        assertEquals("/api/v1/history", recorded.url.encodedPath)
        assertEquals("2025-11-03", recorded.url.queryParameter("from"))
        assertEquals("2026-11-03", recorded.url.queryParameter("to"))
    }

    @Test
    fun `reads session turns with review flag`() = runTest {
        respond(
            200,
            """{"sessionId":3,"status":"COMPLETED","durationLimitSeconds":900,"turns":[{"turnId":1,"turnNo":1,
                "questionText":"질문","answerText":"답","processingStatus":"GRADED","verdict":"WRONG",
                "feedbackToSpeak":"설명","review":true}]}""",
        )

        val turn = api.session(3L).turns.single()

        assertTrue(turn.review)
        assertEquals("WRONG", turn.verdict)
        assertEquals("/api/v1/sessions/3", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `deletes session`() = runTest {
        respond(204, "")

        api.deleteSession(3L)

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/v1/sessions/3", recorded.url.encodedPath)
    }

    @Test
    fun `posts answer to session path`() = runTest {
        respond(
            200,
            """{"sessionId":1,"sessionStatus":"COMPLETED","turnId":2,"verdict":"CORRECT","feedbackToSpeak":"좋습니다",
               "nextAction":"NEXT_QUESTION","nextTurnId":null,"nextQuestionText":null}""",
        )

        val answer = api.submitAnswer(1L, AnswerRequest(2L, "답변", "id-2"))

        assertEquals("CORRECT", answer.verdict)
        assertEquals(null, answer.nextTurnId)
        assertEquals("/api/v1/sessions/1/answers", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `maps 401 to unauthorized without retry`() = runTest {
        respond(401, "")

        val error = failure { api.checkConnection() }

        assertTrue(error is ApiException.Unauthorized)
        assertFalse(error.retryable)
    }

    @Test
    fun `maps unavailable server error as retryable with server message`() = runTest {
        respond(503, """{"code":"LLM_UNAVAILABLE","message":"잠시 후 다시 시도하세요"}""")

        val error = failure { api.endSession(1L) } as ApiException.Server

        assertEquals("LLM_UNAVAILABLE", error.code)
        assertEquals("잠시 후 다시 시도하세요", error.message)
        assertTrue(error.retryable)
    }

    @Test
    fun `connection check treats missing session as success because token was accepted`() = runTest {
        respond(404, """{"code":"SESSION_NOT_FOUND","message":"면접을 찾을 수 없습니다"}""")

        api.checkConnection()

        val recorded = server.takeRequest()
        assertEquals("/api/v1/sessions/0", recorded.url.encodedPath)
        assertEquals("Bearer $TOKEN", recorded.headers["Authorization"])
    }

    @Test
    fun `quota errors are not retried right away`() = runTest {
        respond(429, """{"code":"LLM_QUOTA_EXCEEDED","message":"오늘 호출 상한을 넘었습니다"}""")

        assertFalse(failure { api.endSession(1L) }.retryable)
    }

    @Test
    fun `treats grading in progress as retryable conflict`() = runTest {
        respond(409, """{"code":"GRADING_IN_PROGRESS","message":"채점이 진행 중입니다"}""")

        assertTrue(failure { api.endSession(1L) }.retryable)
    }

    @Test
    fun `does not retry other conflicts`() = runTest {
        respond(409, """{"code":"SESSION_COMPLETED","message":"이미 끝난 면접입니다"}""")

        assertFalse(failure { api.endSession(1L) }.retryable)
    }

    @Test
    fun `handles error body that is not json`() = runTest {
        respond(502, "<html>bad gateway</html>")

        assertEquals("UNKNOWN", (failure { api.endSession(1L) } as ApiException.Server).code)
    }

    @Test
    fun `maps connection failure to network error`() = runTest {
        server.close()

        assertTrue(failure { api.checkConnection() } is ApiException.Network)
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun failure(block: suspend () -> Unit): ApiException {
        try {
            block()
        } catch (ex: ApiException) {
            return ex
        }
        fail("예외가 나야 합니다")
        throw AssertionError()
    }

    private companion object {
        const val TOKEN = "test-token"
    }
}
