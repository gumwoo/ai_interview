package io.github.gumwoo.aiinterview.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitTest {
    @Test
    fun `splits sentences and keeps their positions`() {
        assertEquals(
            listOf(0 to "첫 문장입니다.", 8 to " 둘째인가요?", 15 to " 끝"),
            splitSentences("첫 문장입니다. 둘째인가요? 끝"),
        )
    }

    @Test
    fun `does not split on dots inside numbers`() {
        assertEquals(listOf(0 to "버전 1.2를 썼습니다."), splitSentences("버전 1.2를 썼습니다."))
    }

    @Test
    fun `finds end of sentence from position`() {
        assertEquals(7, sentenceEnd("첫 문장이다. 둘째 문장", 2))
        assertEquals(13, sentenceEnd("첫 문장이다. 둘째 문장", 8))
        assertEquals(0, sentenceEnd("", 3))
    }

    @Test
    fun `returns nothing for empty text`() {
        assertEquals(emptyList<Pair<Int, String>>(), splitSentences("  "))
    }
}
