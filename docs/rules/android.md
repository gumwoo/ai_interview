# Android 규칙

Kotlin, Jetpack Compose, 갤럭시 Z 폴드 8(Android 17) 기준이다. 단일 앱 모듈로 시작하고, 모듈 분리나 UseCase 계층을 미리 만들지 않는다.

## 코루틴과 상태

- `GlobalScope`를 쓰지 않는다. `viewModelScope`와 구조화된 스코프를 쓴다.
- `CancellationException`을 삼키지 않는다. `catch (e: Exception)` 앞에서 다시 던진다.
- IO는 메인 스레드에서 하지 않는다.
- 화면 상태는 불변 data class 하나를 `StateFlow`로 내보낸다. 변경은 `_state.update { it.copy(...) }`로 한다.
- 콜드 Flow를 상태로 바꿀 때는 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 초기값)`을 쓴다. 폴드 전환 같은 구성 변경 때 상류가 재시작되지 않는다.
- Flow는 생명주기에 맞춰 수집한다(`repeatOnLifecycle`, `collectAsStateWithLifecycle`).

## Compose

- 네트워크와 저장 호출은 ViewModel에서 하고, 부수 효과는 `LaunchedEffect` 안에 둔다.
- `NavController`를 깊이 넘기지 않고 람다를 넘긴다.
- `LazyColumn` 항목에 안정적인 `key`를 준다. `remember`에는 의존 키를 빠뜨리지 않는다.
- 사용자에게 보이는 문자열은 리소스에 둔다.
- 접은 화면과 펼친 화면 모두에서 동작하게 만들고, 방향이나 크기를 고정하지 않는다.

## Kotlin과 Android

- `!!`를 쓰지 않는다. `?.`, `?:`, `requireNotNull`을 쓴다.
- sealed 타입의 `when`은 모든 경우를 처리한다.
- `Activity` 등 `Context`를 ViewModel이나 싱글턴에 보관하지 않는다.
- 면접 진행 상태와 음성 세션을 Composable에 두지 않는다. 화면 전환으로 끊기면 안 된다.
- 직렬화 모델은 R8 keep 규칙을 두고, 릴리스 빌드에서 동작을 확인한다.

## 보안

- 서버 주소와 API 토큰은 사용자가 입력하고, `BuildConfig`나 코드에 넣지 않는다.
- 토큰은 Android Keystore로 보호한다. `androidx.security:security-crypto`의 API는 폐기됐으므로 `EncryptedSharedPreferences`를 쓰지 않는다.
- `network_security_config`에서 cleartext를 막는다. `usesCleartextTraffic`은 폐기 예정이므로 쓰지 않는다.
- 모든 HTTP 클라이언트에 타임아웃을 지정한다. 값은 계획서 7.2의 서버 최대 대기 시간보다 길게 잡는다.
- 토큰과 답변 원문을 로그에 남기지 않는다.
- 필요한 컴포넌트만 외부에 노출한다.

## Android 17 대응

자세한 내용은 계획서 6.2를 따른다.

- 백그라운드에서는 재생·오디오 포커스·볼륨 변경이 막히고 재생과 볼륨 호출은 오류 없이 실패한다. 받기 후 화면이 보일 때 Foreground Service를 시작한다.
- 수신 알림음은 알림 채널 소리로 내거나, 정확 알람 권한과 `USAGE_ALARM`을 함께 쓴다.
- `ACCESS_LOCAL_NETWORK`가 Tailscale 연결에 필요한지 실기기에서 확인한다.

## 테스트

- ViewModel은 JUnit, `kotlinx-coroutines-test`의 `runTest`, Turbine, 가짜 리포지토리로 테스트한다.
- 알람, 마이크, TTS, STT, 폴드 전환은 단위 테스트로 보장되지 않는다. 계획서 11.3의 조건으로 실기기에서 확인한다.

---

일부 내용은 [Everything Claude Code](https://github.com/affaan-m/everything-claude-code)(MIT, Copyright (c) 2026 Affaan Mustafa, 커밋 `b38be6a`)의 `kotlin-coroutines-flows`, `kotlin-reviewer`, `rules/kotlin`을 참고해 프로젝트에 맞게 다시 썼다.
