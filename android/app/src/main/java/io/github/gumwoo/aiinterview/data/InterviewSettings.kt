package io.github.gumwoo.aiinterview.data

import android.content.Context
import androidx.core.content.edit

/** 면접 설정. 원본은 앱이고, 시작할 때 서버로 함께 보낸다(계획서 4.1). */
data class InterviewSettings(
    val serverUrl: String = "",
    val topics: List<String> = DEFAULT_TOPICS,
    val difficulty: String = "intermediate",
    val durationMinutes: Int = 15,
    val reviewPercent: Int = 40,
) {
    val reviewRatio: Double get() = reviewPercent / 100.0

    companion object {
        /** 클라우드 면접 대비 1~7강 순서다. 서버 시드 질문의 topic과 같은 이름을 쓴다. */
        val DEFAULT_TOPICS = listOf(
            "Docker_Container", "Kubernetes_EKS", "Resource_Autoscaling", "AWS_Network",
            "Terraform_IaC", "CICD_GitOps", "Cloud_Security",
        )

        fun parseTopics(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** 예전 주제 이름으로 저장된 설정은 지금 목록에 있는 것만 남기고, 하나도 없으면 기본값으로 돌린다. */
        fun knownTopicsOrDefault(saved: List<String>): List<String> =
            saved.filter { it in DEFAULT_TOPICS }.ifEmpty { DEFAULT_TOPICS }

        /** https만 허용한다. 디버그 빌드의 에뮬레이터 주소만 예외다. */
        fun isValidServerUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("https://") && trimmed.length > "https://".length ||
                trimmed.startsWith("http://10.0.2.2")
        }
    }
}

class InterviewSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): InterviewSettings {
        val defaults = InterviewSettings()
        return InterviewSettings(
            serverUrl = preferences.getString(KEY_SERVER_URL, defaults.serverUrl).orEmpty(),
            topics = preferences.getString(KEY_TOPICS, null)
                ?.let { InterviewSettings.knownTopicsOrDefault(InterviewSettings.parseTopics(it)) }
                ?: defaults.topics,
            difficulty = preferences.getString(KEY_DIFFICULTY, defaults.difficulty).orEmpty(),
            durationMinutes = preferences.getInt(KEY_DURATION, defaults.durationMinutes),
            reviewPercent = preferences.getInt(KEY_REVIEW_PERCENT, defaults.reviewPercent),
        )
    }

    fun save(settings: InterviewSettings) {
        preferences.edit {
            putString(KEY_SERVER_URL, settings.serverUrl.trim())
            putString(KEY_TOPICS, settings.topics.joinToString(","))
            putString(KEY_DIFFICULTY, settings.difficulty)
            putInt(KEY_DURATION, settings.durationMinutes)
            putInt(KEY_REVIEW_PERCENT, settings.reviewPercent)
        }
    }

    private companion object {
        const val PREFERENCES = "interview_settings"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOPICS = "topics"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_DURATION = "duration_minutes"
        const val KEY_REVIEW_PERCENT = "review_percent"
    }
}
