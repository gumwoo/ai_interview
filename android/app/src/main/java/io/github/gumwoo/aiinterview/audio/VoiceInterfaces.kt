package io.github.gumwoo.aiinterview.audio

private val SENTENCE_END = Regex("[.?!](?=\\s|$)")

/** [from] 이후 첫 문장이 끝나는 위치를 돌려준다. 1.2 같은 숫자의 점은 문장 끝으로 보지 않는다. */
internal fun sentenceEnd(text: String, from: Int): Int {
    if (from >= text.length) return text.length
    return SENTENCE_END.find(text, from.coerceAtLeast(0))?.let { it.range.last + 1 } ?: text.length
}

/** 글을 문장으로 나눠 (시작 위치, 문장) 목록으로 돌려준다. */
internal fun splitSentences(text: String): List<Pair<Int, String>> {
    val sentences = mutableListOf<Pair<Int, String>>()
    var start = 0
    while (start < text.length) {
        val end = sentenceEnd(text, start)
        val sentence = text.substring(start, end)
        if (sentence.isNotBlank()) sentences += start to sentence
        start = end
    }
    return sentences
}

/** 글을 소리로 읽는다. */
interface SpeechOutput {
    /**
     * 끝까지 읽으면 true, 읽지 못하면 false를 돌려준다.
     * [onProgress]는 읽기 시작한 문장의 위치(글자 인덱스)를 받는다.
     */
    suspend fun speak(text: String, onProgress: (Int) -> Unit = {}): Boolean

    fun stop()

    /** 스피커 또는 수화기로 바꾼다. 실제로 스피커로 나가면 true를 돌려준다. */
    fun setSpeakerOn(on: Boolean): Boolean
}

/** 말을 한 번 듣고 글자로 바꾼다. 질문을 다 읽은 뒤 한 번씩 시작하는 턴제 구조다. */
interface SpeechInput {
    suspend fun listen(onPartial: (String) -> Unit): ListenResult

    /** 지금까지 말한 내용으로 인식을 끝낸다. */
    fun stopListening()
}

sealed interface ListenResult {
    data class Recognized(val text: String) : ListenResult

    /** 소리가 없거나 알아듣지 못했다. 다시 시도할 수 있다. */
    data object NotUnderstood : ListenResult

    /** 권한이나 인식 서비스가 없어 음성 입력을 쓸 수 없다. */
    data object Unavailable : ListenResult
}
