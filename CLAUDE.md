# AI Voice Interview

매일 정해진 시간에 울리는 알림을 받으면 AI가 음성으로 기술 면접을 진행하는 1인용 앱이다.
갤럭시 Z 폴드 8(Android 17) 앱과 24시간 켜두는 Windows PC 서버(Spring Boot, PostgreSQL, Docker Desktop)로 구성한다.

## 문서

- 설계와 단계별 완료 기준의 원본: [AI_Voice_Interview_Development_Plan.md](AI_Voice_Interview_Development_Plan.md)
- 백엔드 규칙: [docs/rules/backend.md](docs/rules/backend.md)
- Android 규칙: [docs/rules/android.md](docs/rules/android.md)
- DB·Docker 규칙: [docs/rules/data-and-infra.md](docs/rules/data-and-infra.md)
- 채점 평가 규칙: [docs/rules/eval.md](docs/rules/eval.md)
- Git·이슈·PR 규칙: [docs/rules/git.md](docs/rules/git.md)

규칙과 계획서가 충돌하면 계획서를 따르고, 충돌 내용을 사용자에게 알린다.

## 작업 방식

- 구현 단계(M0~M6)는 사용자가 시작을 지시한 뒤에 진행한다. 계획서의 완료 기준을 확인해야 다음 단계로 넘어간다.
- 1인 사용 앱이다. 다중 사용자, 역할 권한, 기기 페어링처럼 계획서에 없는 범용 기능을 추가하지 않는다.

## 명령

- 백엔드 테스트(`backend/`에서 실행, Docker 필요): `./gradlew test`
- 실제 Claude 호출 테스트(`backend/`에서, 환경 변수 `ANTHROPIC_API_KEY` 필요, 크레딧 사용): `./gradlew liveTest`
- 서버 실행(저장소 루트에서): `docker compose -f infra/compose.yaml up -d --build`
- 상태 확인: `curl http://localhost:18080/api/v1/health`
- 로컬 설정: `infra/.env.example`을 `infra/.env`로 복사해 값을 채운다.

## 항상 지킬 것

- **공개 저장소다.** `infra/.env`, `local.properties`, 앱 서명 키, `infra/backup/`, 로그, API 토큰, Tailscale 호스트 이름을 커밋하지 않는다. 비밀값이 한 번이라도 올라가면 폐기하고 새로 발급한다.
- **Claude API 키는 PC 서버의 `infra/.env`에만 둔다.** APK와 채팅에 넣지 않는다.
- **자동 충전을 쓰지 않는다.** 앱은 결제나 충전을 시도하지 않고, 호출 상한을 넘으면 오류를 안내한다.
- **Claude 호출은 DB 트랜잭션 밖에서 한다.** 답변을 먼저 저장하고, 같은 요청이 다시 와도 Claude를 또 호출하지 않는다.
- **모델은 `CLAUDE_MODEL` 설정값이다.** 테스트는 `claude-haiku-5-5`, 최종은 `claude-sonnet-5-5`다. 자동 테스트에서는 실제 API를 호출하지 않는다.
- **시드 질문의 근거는 FlowTicket 문서다.** FlowTicket은 질문 출처로만 쓰고, 코드 규칙으로 가져오지 않는다.
