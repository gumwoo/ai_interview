package io.github.gumwoo.aiinterview

/** 알람으로 온 전화를 보여 주는 화면. 잠금 화면 위로 뜨는 설정은 매니페스트에 있다. */
class CallActivity : MainActivity() {
    override val isCallSurface = true
}
