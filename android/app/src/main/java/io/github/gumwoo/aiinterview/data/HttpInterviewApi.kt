package io.github.gumwoo.aiinterview.data

import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 서버 호출 구현. 서버는 Claude 호출에 최대 90초(30초 x 3회)를 쓸 수 있으므로 읽기 타임아웃을 그보다 길게 둔다.
 */
class HttpInterviewApi(
    private val connection: () -> ServerConnection,
    private val client: OkHttpClient = defaultClient(),
) : InterviewApi, HistoryApi {

    private val json = Json { ignoreUnknownKeys = true }

    /** /health는 토큰을 검사하지 않는다. 없는 면접을 조회해 404가 오면 연결과 토큰이 모두 맞은 것이다. */
    override suspend fun checkConnection() {
        try {
            call(Request.Builder().url(url("/sessions/0")).get())
        } catch (ex: ApiException.Server) {
            if (ex.status != 404) throw ex
        }
    }

    override suspend fun startSession(request: StartSessionRequest): SessionResponse =
        decode(post("/sessions", json.encodeToString(StartSessionRequest.serializer(), request)), SessionResponse.serializer())

    override suspend fun submitAnswer(sessionId: Long, request: AnswerRequest): AnswerResponse =
        decode(
            post("/sessions/$sessionId/answers", json.encodeToString(AnswerRequest.serializer(), request)),
            AnswerResponse.serializer(),
        )

    override suspend fun requestHint(sessionId: Long, turnId: Long): HintResponse =
        decode(
            post("/sessions/$sessionId/hint", json.encodeToString(HintRequest.serializer(), HintRequest(turnId))),
            HintResponse.serializer(),
        )

    override suspend fun endSession(sessionId: Long): SummaryResponse =
        decode(post("/sessions/$sessionId/end", "{}"), SummaryResponse.serializer())

    override suspend fun history(from: LocalDate, to: LocalDate): List<HistoryItem> =
        decode(
            call(Request.Builder().url(url("/history?from=$from&to=$to")).get()),
            ListSerializer(HistoryItem.serializer()),
        )

    override suspend fun session(sessionId: Long): SessionDetail =
        decode(call(Request.Builder().url(url("/sessions/$sessionId")).get()), SessionDetail.serializer())

    override suspend fun deleteSession(sessionId: Long) {
        call(Request.Builder().url(url("/sessions/$sessionId")).delete())
    }

    private suspend fun post(path: String, body: String): String {
        val requestBody: RequestBody = body.toRequestBody(JSON_MEDIA_TYPE)
        return call(Request.Builder().url(url(path)).post(requestBody))
    }

    private fun <T> decode(body: String, serializer: kotlinx.serialization.KSerializer<T>): T =
        json.decodeFromString(serializer, body)

    private fun url(path: String): String = connection().baseUrl.trimEnd('/') + API_PREFIX + path

    private suspend fun call(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        val token = connection().apiToken
        val request = builder.header("Authorization", "Bearer $token").build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> body
                    response.code == 401 -> throw ApiException.Unauthorized()
                    else -> throw serverError(response.code, body)
                }
            }
        } catch (ex: IOException) {
            throw ApiException.Network(ex)
        }
    }

    private fun serverError(status: Int, body: String): ApiException.Server {
        val error = runCatching { json.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        return ApiException.Server(status, error?.code ?: "UNKNOWN", error?.message ?: "서버 오류가 발생했습니다.")
    }

    @Serializable
    private data class ErrorBody(val code: String, val message: String)

    companion object {
        private const val API_PREFIX = "/api/v1"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(130, TimeUnit.SECONDS)
            .build()
    }
}

data class ServerConnection(val baseUrl: String, val apiToken: String)
