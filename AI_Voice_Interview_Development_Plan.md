# AI 음성 기술면접관 앱 개발 계획서

> **프로젝트명(가칭): AI Voice Interview**  
> **문서 버전:** v1.2 (사용 환경·모델·시드 출처 확정, M0~M6 구현 단계 반영)  
> **작성일:** 2026-11-02  
> **대상 환경:** 갤럭시 Z 폴드 8(Android 17) + 24시간 켜두는 Windows 11 PC(Docker Desktop) + Claude API (Ultra 월간 API 크레딧 활용, 자동 충전 금지)  
> **목표:** 매일 지정한 시간에 보이스톡처럼 학습 알림이 울리고, 수락하면 AI가 기술 질문을 출제·평가·설명·꼬리질문하며 오답을 누적 관리하는 개인용 앱

---

## 0. 한눈에 보는 결론

- **실제 이동통신 전화가 아니라 앱 내부의 ‘전화 수신형 인터페이스’**를 구현한다. Twilio, 전화번호 임대, 통화 발신료는 필요 없다.
- **갤럭시에서 로컬 알람을 예약**한다. 알람을 받으면 앱에서 ‘받기/거절/5분 뒤 다시’ 선택이 가능하다. 화면이 꺼진 상태의 전체 화면 표시는 Android 권한·제조사 정책에 따라 제한될 수 있어, 기본 기능은 고우선순위 알림과 사용자 탭을 통한 진입으로 보장한다.
- **PC는 AI 백엔드**다. Claude API 키는 PC에만 보관하며, 앱은 PC의 서버 API를 호출한다.
- **음성 입출력은 우선 갤럭시의 STT/TTS**를 사용한다. Claude는 텍스트 기반 문제 출제, 답변 채점, 해설, 꼬리질문을 담당한다.
- 첫 버전은 **사용자가 질문을 듣고 답한 다음 AI가 반응하는 턴 기반 대화**다. 사람끼리 통화하듯 자유롭게 끼어드는 풀 듀플렉스 음성 대화는 후속 개발로 미룬다.
- **외부 모델 비용이 무조건 0원은 아니다.** Claude Ultra의 월간 API 크레딧 한도 내에서는 추가 결제 없이 쓸 수 있지만, 크레딧은 소진·만료된다. 기타 네트워크/전력/서버 운영비는 별도다.

## 1. 프로젝트 배경 및 목표

### 1.1 해결하려는 문제

- 개발·클라우드·인프라 지식을 눈으로만 학습하면 면접에서 입으로 설명하기 어렵다.
- 스스로 매일 정해진 시각에 학습을 시작하기 어렵다.
- 질문에 답해도 어떤 부분이 정확하고 어떤 부분이 빠졌는지 피드백을 받기 어렵다.
- 틀린 문제를 기록하더라도 다음에 다시 구술로 확인하지 않는 경우가 있다.

### 1.2 사용자 경험 목표

1. 매일 **오전 07:30**(초깃값, 앱에서 수정 가능)에 ‘AI 기술 면접관’ 알림이 울린다.
2. 사용자가 **받기**를 누르면 “오늘은 Kubernetes 관련 면접을 시작하겠습니다”라는 음성이 나온다.
3. AI가 질문을 **한 번에 하나씩** 제시한다.
4. 사용자는 말을 통해 답한다. STT 결과가 화면에 표시된다.
5. AI는 답변을 `정답 / 부분정답 / 오답 / 판단보류(STT 또는 정보 부족)`로 구분하고 근거를 설명한다.
6. 정답이면 심화 꼬리질문, 부분정답이면 누락된 핵심을 묻는 질문, 오답이면 짧은 해설과 재확인 질문으로 진행한다.
7. 통화 종료 시 학습 요약·오답·추가 복습일을 기록한다.
8. 다음 일정에서 과거 오답을 적절한 비중으로 다시 출제한다.

### 1.3 MVP 범위와 제외 범위

| 구분 | MVP(v1) 포함 | 후속(v2+) |
|---|---|---|
| 기상/수신 | 요일·시각 설정, 로컬 알람, 받기/거절/미루기 | 원격 푸시 연동 |
| 음성 | Android STT/TTS, 한 턴씩 말하기, 무음 종료 감지 | 자연스러운 끼어들기·동시 송수신·고급 VAD |
| 학습 | 질문/답변/채점/해설/꼬리질문/복습 | RAG 기반 실시간 문서 참조·적응형 커리큘럼 |
| 저장 | PostgreSQL에 대화 텍스트·평가 저장 | Notion 자동 동기화·학습 대시보드 |
| 배포 | 개인 PC에서 Docker Compose 실행, 갤럭시에 APK 직접 설치 | 외부 서버 이전 |
| 통화망 | 필요 없음(데이터 통신) | 필요 시 실제 전화망 연동 검토 |

**핵심 원칙:** ‘진짜 전화처럼 반드시 잠금 화면에 전체 화면으로 등장’까지를 v1의 성공 조건으로 잡지 않는다. Android의 권한 정책을 지키면서 **매일 인지할 수 있는 알림 + 수락 후 정상적인 음성 면접**을 우선 검증한다.

---

## 2. 권장 기술 스택

| 영역 | 선택 | 선택 이유 |
|---|---|---|
| Android 앱 | **Kotlin, Jetpack Compose, ViewModel, Room(설정/임시 캐시)** | 갤럭시 네이티브 알람·마이크·알림 제어 |
| 알람/일정 | **AlarmManager**, BootReceiver | 휴대폰 단독으로 예약 알림 실행. 권한 거부 시 비정확 알람 대안 |
| 음성 인식(STT) | **Android SpeechRecognizer**, 가능하면 기기 내 인식 | 별도 유료 STT 제공업체 없이 우선 검증 |
| 음성 합성(TTS) | **Android TextToSpeech** | 별도 유료 TTS 제공업체 없이 시작 |
| 개인 PC 백엔드 | **Java 17 + Spring Boot + Gradle** | 기존 Java/Spring 경험 활용, 상태관리와 REST 개발 용이 |
| LLM | **Anthropic Claude Messages API + 공식 Java SDK**. 테스트는 `claude-haiku-5-5`, 최종은 `claude-sonnet-5-5` | 구조화된 질문/판정/해설 생성, 테스트 비용 절감 |
| 데이터 저장 | **PostgreSQL + Flyway** | 세션·답변·평가·복습 계획 관리 |
| 연결 | **Tailscale**(갤럭시와 PC에 모두 설치) | 집 밖에서도 개인 PC에 사설 네트워크로 연결 |
| 실행 | **Docker Desktop + Compose**(DB, 백엔드), `restart: always` | 프로세스가 죽거나 Docker가 다시 뜨면 자동 재기동 |
| 관측성 | Spring Actuator, 구조화 로그, 지표 | 실패 원인·지연시간 파악 |
| 외부 노트 연동 | Notion API(후순위) | 오답 노트와 주제별 진도 동기화 |

**설계 결정:** 처음에는 FCM과 WebRTC를 사용하지 않는다. 알림은 Android 기기에서 생성하고, 음성은 스마트폰에서 STT→텍스트 전송→응답 수신→TTS로 처리하면 충분하다. 이 방식은 **앱 내 전화처럼 보이는 학습 세션**이지 별도의 전화 연결이나 실시간 VoIP 통화가 아니다.

### 2.1 Claude SDK와 API의 관계

- Java SDK는 Claude API를 **호출하는 라이브러리**이므로, SDK를 사용해도 API 토큰 비용은 계산된다.
- **Ultra의 월간 API 크레딧**을 사용하려면 Claude Console 조직을 Ultra 구독과 연결하고 해당 조직의 API 키를 발급받아야 한다(14절 1번 문서 기준, 직접 검증하지 않음). 잔액은 별도 관리 대상으로 두지 않는다.
- API 키는 발급 완료 상태이며, M0에서 만드는 `infra/.env`의 `ANTHROPIC_API_KEY`에 직접 넣는다.
- 별도 구매 크레딧 또는 자동 충전이 설정되어 있으면 월간 제공 크레딧 소진 후에도 사용이 계속될 수 있으므로 **자동 충전은 절대 켜지 않고, 애플리케이션 자체 사용 상한**도 유지한다. 상한은 버그로 호출이 반복될 때를 막는 장치다.
- **모델 전략:** 테스트 단계는 Haiku, 어느 정도 완성되면 Sonnet으로 전환하며 최종 모델은 Sonnet이다. 2026-10-06 기준 문서의 1M 토큰당 단가는 Haiku 5.5가 입력 $0.10·출력 $0.50, Sonnet 5.5가 입력 $2·출력 $10이다. 실제 단가는 전환 시점에 다시 확인한다.

---

## 3. 전체 시스템 구조

```text
┌────────────────────────── 갤럭시 Android 앱 ───────────────────────────┐
│  AlarmManager → 학습 알림(받기/거절/미루기) → InterviewActivity       │
│                                              │                      │
│  TTS ◀── AI 텍스트 수신 ◀──────┐               │                      │
│  STT ── 음성→텍스트 변환 ─────┼── REST/HTTPS ─┘                      │
│  스케줄 설정/현재 질문/마이크 상태/통화 종료 UI                        │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                   Tailscale 사설 네트워크
                                │
┌───────────────────────────────▼──────────────────────────────────────┐
│                    상시 켜져 있는 PC 서버                             │
│  Spring Boot                                                        │
│   ├─ Session API                                                    │
│   ├─ Question Planner (복습 + 신규 주제 혼합)                         │
│   ├─ Interview Orchestrator (대화 상태 머신)                         │
│   ├─ Answer Grader (평가 기준 비교)                                  │
│   ├─ Claude Adapter ──▶ Claude Messages API                         │
│   ├─ Transcript & Review Service                                    │
│   └─ PostgreSQL (질문·대답·점수·복습 일정)                            │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.1 ‘아침 7:30’ 실제 실행 흐름

1. 앱이 ‘월~금 07:30’ 같은 학습 스케줄을 휴대폰에 저장하고 다음 알람을 예약한다.
2. 예약 시각이 되면 Android가 알람을 전달한다. 알림은 **PC가 꺼져 있어도 휴대폰에서 울릴 수 있다**.
3. 사용자가 **받기**를 누르면 앱이 실행되고 PC 서버의 `health`를 확인한다.
4. 서버가 정상이라면 세션을 생성하고 첫 질문을 앱에 전달한다. 질문은 `question_bank`에서 먼저 고르고, 후보가 없을 때만 Claude로 생성한다.
5. Android TTS가 질문을 읽는다. TTS 재생이 끝난 뒤 STT를 시작한다.
6. STT 결과를 사용자가 확인하거나 자동 확정하면 서버로 전송한다.
7. 서버는 답변을 채점하고 해설/꼬리질문을 생성해 응답한다.
8. 설정한 학습 시간이 지나거나 사용자가 종료하면 요약을 저장하고 다음 알람을 유지한다.

### 3.2 전화 수신처럼 보이게 만드는 방법과 한계

- **최소 보장 UI:** 고우선순위 알림 + 알림 탭으로 앱 열기 + 앱 안에서 받기/거절/미루기.
- **확장 UI:** OS가 허용하면 잠금화면/전체 화면 인텐트 기반 수신 인터페이스 지원.
- Android 14 이상은 `USE_FULL_SCREEN_INTENT`가 **통화·알람 중심 앱**에 제한된다. 이 앱은 Play 배포 없이 APK로 직접 설치하지만, 설치 경로와 사용자 설정에 따라 허용되지 않을 수 있으므로 실기기에서 확인한다. 권한을 우회하지 않는다.
- **정확 알람**도 최신 Android에서는 특별 권한이 기본 거부될 수 있다. `canScheduleExactAlarms()`로 확인하고, 허용하지 않으면 일반 알림/비정확 알람으로 기능을 낮춰 제공한다.
- 마이크는 사용자가 ‘받기’를 선택한 **후**에 활성화한다. Android 14 이상에서 마이크 포그라운드 서비스는 시작 위치·권한에 제한이 있다.
- Android 17의 변경 문서에는 전체 화면 알림과 `AlarmManager` 자체의 변경이 따로 나오지 않는다. 다만 동작이 그대로라고 단정하지 않고 폴드 실기기에서 확인한다. 오디오·화면 관련 변경은 6.2에 정리했다.
- 갤럭시 제조사의 절전 설정·방해금지·알림 권한 때문에 실제 울림 방식은 기기에서 검증해야 한다.

---

## 4. 핵심 기능 명세 (FR)

| ID | 기능 | 동작/예외 | 우선순위 |
|---|---|---|---|
| FR-01 | 학습 스케줄 | 요일·시각·세션 길이(기본 15분)·주제 선택 | P0 |
| FR-02 | 예약 알림 | 시각 도달 시 알림; 기기 재부팅 후 재예약 | P0 |
| FR-03 | 수신 동작 | 받기·거절·5분 뒤 다시; 중복 알림 방지 | P0 |
| FR-04 | 세션 시작 | 서버 상태 확인, 당일 세션 생성, 첫 질문 | P0 |
| FR-05 | 음성 질문 | TTS 재생; 다시 듣기·화면 텍스트 확인 | P0 |
| FR-06 | 음성 답변 | STT, 인식 결과 확인, 재시도/수정 | P0 |
| FR-07 | 답변 평가 | 정답/부분정답/오답/판단보류 + 핵심 근거 | P0 |
| FR-08 | 꼬리질문 | 기존 답변과 연결된 추가 질문, 1개씩 | P0 |
| FR-09 | 모름 처리 | 힌트 또는 정답 설명 후 유사 질문으로 재확인 | P0 |
| FR-10 | 세션 종료 | 수동 종료, 제한시간 도달, 중간 종료 저장 | P0 |
| FR-11 | 이력 화면 | 날짜별 주제, 질문, 답변, 평가, 부족한 개념 | P1 |
| FR-12 | 반복 복습 | 오답/부분정답을 다음 회차 출제 후보로 등록 | P0 |
| FR-13 | Notion 동기화 | 요약 및 오답 노트 생성·갱신 | P2 |
| FR-14 | 점진 스트리밍 | 응답을 문장 단위로 TTS 읽기 | P2 |
| FR-15 | 고급 음성 UX | 대화 중 끼어들기, 저지연 VAD | P3 |

### 4.1 사용자 설정 기본값

설정의 원본은 **앱 로컬 저장소 하나**다. PC가 꺼져 있어도 알람이 울려야 하므로 스케줄은 서버에 두지 않고, 학습 설정(주제·난이도·세션 길이·복습 비율)만 세션을 시작할 때 `POST /sessions`로 함께 보낸다.

```yaml
timezone: Asia/Seoul
scheduled_at: "07:30"      # 사용자가 변경 가능
days_of_week: [MON, TUE, WED, THU, FRI]
session_duration_minutes: 15
ring_duration_seconds: 45
snooze_minutes: 5
review_ratio: 0.40          # 복습 40% / 신규 60% (초기 가설)
question_difficulty: intermediate
question_topics:            # 5.6 참고. 개발·인프라 모두 포함
  - Concurrency
  - Transaction_Payment
  - Redis_Queue
  - Event_Outbox
  - Spring_JPA
  - Auth_Security
  - Kubernetes
  - Deployment_GitOps
  - AWS
  - Docker_CI
  - LoadTest_Observability
record_raw_audio: false
```

### 4.2 진행 상태 머신

```text
SCHEDULED
   └─(알람 발생)─▶ RINGING
                       ├─ 거절/시간 초과 ─▶ DECLINED/MISSED
                       ├─ 미루기 ────────▶ SNOOZED → SCHEDULED
                       └─ 받기 ─────────▶ CONNECTING
                                             ├─ 서버 오류 ─▶ FAILED
                                             │                ├─ 재시도 ─▶ CONNECTING
                                             │                └─ 포기 ───▶ 세션 없이 종료
                                             └─ 연결 ───▶ ASKING
                                                              ↓
                                                         LISTENING
                                                              ↓
                                                         GRADING
                                                              ↓
                                                    EXPLAINING/FOLLOW_UP
                                                              └──▶ ASKING
                                                       종료 ─▶ COMPLETED
```

- `CONNECTING`까지는 앱 로컬 상태이고, 서버의 `interview_session`은 연결에 성공해 `ASKING`에 들어갈 때 처음 만들어진다.
- 사용자 응답은 `turn_id`로 묶어 재전송/타임아웃 시 중복 채점·중복 저장을 막는다.
- `MISSED`, `DECLINED`일 때는 **AI API를 호출하지 않는다**.
- 연결이 끊겨도 이미 저장된 답변 및 진행 상태는 유지한다.

---

## 5. AI 면접 엔진 설계

### 5.1 AI 역할 분리(논리적 분리)

1. **QuestionPlanner:** 당일 주제, 오답 복습 대상, 난이도, 기존 출제 이력을 바탕으로 `question_bank`에서 질문 선택.
2. **QuestionGenerator:** 선택할 후보가 없을 때만 질문 하나와 참고 답안·채점 루브릭을 생성해 `question_bank`에 저장.
3. **AnswerGrader:** 답변에서 기술적 사실·핵심 개념을 확인하여 판정하고, 꼬리질문과 그 채점 기준까지 **한 번의 호출로** 받는다.
4. **FollowUpGenerator:** 별도 API 호출 없이 채점 결과의 `next_action`과 꼬리질문을 해석해 다음 턴을 만들고, 꼬리질문 깊이를 제한한다.
5. **ReviewScheduler:** 오답 우선순위, 다음 복습일 갱신.

초기에는 같은 Claude 모델을 쓰되 **출제와 채점은 프롬프트·데이터 구조를 분리**한다. 향후 비용 최적화가 필요하면 출제와 채점 모델을 다르게 설정할 수 있게 한다.

**Claude 호출 규칙:**

- 호출은 도구 없는 단발 요청으로 하고 대화 전체를 보내지 않는다. 턴당 입력은 질문, 채점 기준, 답변과 짧은 세션 메모로 제한한다.
- 응답 형식은 구조화 출력(`output_config.format`)으로 고정한다. Sonnet 5.5는 강제 `tool_choice`를 400으로 거부하므로 쓰지 않는다.
- 모델은 `CLAUDE_MODEL` 설정값으로 두고, effort는 모델마다 기본값이 달라 직접 지정한다.
- `stop_reason`이 `refusal`이면 판단보류로 처리한다. Sonnet 전환 때 refusal 대체 모델 설정도 함께 검토한다.
- 모델을 바꾸면 M3의 평가셋을 다시 돌려 확인한다. Haiku로 맞춘 프롬프트가 Sonnet에서도 같은 품질이라는 보장은 없다.

### 5.2 질문 데이터 구조(예시)

```json
{
  "topic": "Kubernetes",
  "concept_key": "k8s-cpu-request-limit",
  "source_ref": null,
  "difficulty": "intermediate",
  "question": "CPU Limit과 CPU Request의 차이를 설명해 보세요.",
  "expected_points": [
    "request는 스케줄링 및 자원 할당의 기준이다",
    "limit는 CPU 사용 상한을 설정한다",
    "limit를 넘기려는 시도는 CPU throttling으로 이어질 수 있다"
  ],
  "common_mistakes": [
    "CPU Request가 최대 사용량을 강제로 제한한다고 설명한다"
  ],
  "rubric_version": 1
}
```

### 5.3 채점 결과(예시)

```json
{
  "verdict": "PARTIAL",
  "confidence": 0.85,
  "correct_points": ["CPU throttling 설명은 정확함"],
  "missing_points": ["Request의 스케줄링 역할을 언급하지 않음"],
  "incorrect_points": [],
  "feedback_to_speak": "CPU Limit 설명은 맞았습니다. Request가 스케줄링에서 어떤 역할을 하는지도 말씀해 주실 수 있나요?",
  "next_action": "FOLLOW_UP",
  "follow_up_question": "그렇다면 스케줄러는 CPU Request를 어떻게 활용할까요?",
  "follow_up_expected_points": ["스케줄러는 노드의 할당 가능량과 Pod의 request 합을 비교해 배치한다"]
}
```

`confidence`는 **모델의 자기평가값**이므로 검증된 확률로 간주하지 않는다. 중요한 사실 판단에는 참고 답안, 문서 근거 또는 별도 검증을 결합한다.

### 5.4 면접 규칙(시스템 프롬프트 요구사항)

- 한국어, 자연스러운 실제 면접관의 말투 사용.
- 한 번에 질문 1개, 설명은 기본 2~4문장으로 제한.
- 사용자가 “모르겠어요”라고 하면 질책 없이 핵심 개념·사례를 설명하고 이해 확인 질문을 낸다.
- 정답 일부만 말했으면 바로 오답으로 처리하지 않고 누락 개념을 후속 질문으로 확인한다.
- 답변이 애매하거나 STT 인식이 불확실하면 재확인 후 평가한다.
- 잘못된 기술 주장을 정확한 내용으로 고치되, 조건·환경에 따라 달라지는 주제는 절대적 정답처럼 단정하지 않는다.
- 같은 질문을 불필요하게 반복하지 않고, 복습 문제는 시차를 두고 재출제한다.
- 면접 중에는 답안을 먼저 보여주지 않으며, **해설 단계에서만** 모범 답변을 제공한다.
- 사용자의 발화가 API 비밀정보 노출, 시스템 프롬프트 공개 등을 요청해도 응하지 않도록 서버에서 도구 권한을 분리한다.

### 5.5 복습 로직 v1

- `WRONG` → 다음 학습일에 우선 재출제.
- `PARTIAL` → 다음 2~3회 학습 중 복습 후보.
- `CORRECT` → 약 7일 후 변형 질문으로 점검.
- **3회 연속 정확한 설명** → 해당 개념 출제 가중치 낮춤.
- 실제 학습효과를 보고 간격과 가중치를 조절한다(위 간격은 초기 설계값이며 검증된 최적값이 아님).

### 5.6 질문 카테고리와 출처

시드 질문은 사용자의 프로젝트 [FlowTicket](https://github.com/gumwoo/first_come-first_served)의 문서를 근거로 만들고, 개발과 인프라를 모두 다룬다. 면접에서 **자기 프로젝트의 선택과 근거를 말로 설명하는 연습**이 목적이다.

| 분야 | 주제 | 질문 유형 | 근거 문서(예) |
|---|---|---|---|
| 개발 | Concurrency | 조건부 UPDATE, 초과판매, 멱등성 | ADR-003, ADR-006, TS-013, TS-014, IMP-003 |
| 개발 | Transaction_Payment | 트랜잭션 경계, 보상, 정산 | ADR-011, ADR-020, ADR-021, TS-030 |
| 개발 | Redis_Queue | Lua 원자화, ZSet 대기열, 장애 시 동작 | ADR-002, ADR-016, TS-043~045, IMP-028 |
| 개발 | Event_Outbox | Outbox, Pub/Sub, Kafka 재평가, SSE | ADR-010, ADR-022, ADR-028, TS-012, TS-032 |
| 개발 | Spring_JPA | 모듈러 모놀리스, 프록시, 시계 주입, 커넥션 풀 | ADR-014, ADR-018, ADR-019, TS-021, IMP-042 |
| 개발 | Auth_Security | Refresh rotation, 관리자 인증, SSE 티켓 | ADR-007, ADR-017, IMP-001, TS-003, TS-041 |
| 인프라 | Kubernetes | HPA, requests, probes, PDB, 배치 | ADR-025, ADR-026, ADR-030, ADR-031, TS-019, IMP-017 |
| 인프라 | Deployment_GitOps | 롤링 배포, `preStop`, Argo CD | ADR-009, TS-022, TS-035, IMP-015 |
| 인프라 | AWS | 3AZ, NAT, ALB, RDS·Redis 페일오버, 비용 | ADR-012, TS-018, TS-037, IMP-040 |
| 인프라 | Docker_CI | 이미지 캐시, CI 재현성, 배포 경합 | IMP-014, TS-027, TS-029, TS-048 |
| 인프라 | LoadTest_Observability | k6, 판정 기준, 측정 오류, 지표 수집 | IMP-029~031, TS-036, TS-054, ADR-029 |
| 공통 | 일반 CS(Linux, Networking, Database) | 프로젝트 답변에서 이어지는 개념 확인 | 꼬리질문 또는 생성 질문 |

**질문 출처(v1):** 사용자가 검토한 FlowTicket 시드 질문으로 시작하고, 시드가 없거나 소진된 주제는 Claude가 생성한 질문을 `question_bank`에 저장해 재사용한다. 생성 질문의 참고 답안은 검증되지 않은 값이므로 `origin=GENERATED`로 구분한다.

**시드 작성 규칙:**

- 초안은 FlowTicket의 ADR 31개, TS 54개, IMP 54개와 README를 읽고 40개 정도(제안값)를 개발·인프라 반반으로 만든다. 내용은 사용자가 검토한다.
- 질문마다 근거 문서(`source_ref`)와 기준 커밋(`source_commit`)을 남긴다. 첫 기준 커밋은 `5947c5c`다. 저장소가 바뀌면 시드를 수동으로 다시 맞춘다.
- 런타임에는 저장소를 읽지 않는다. 문서 내용은 시드 JSON의 `expected_points`로 옮겨 둔다.
- 대체된 문서는 현재 상태 기준으로 출제한다. ADR-008·010의 Kafka 부분은 ADR-028로 대체됐고, ADR-013은 Proposed이며, ADR-011은 실제 PG로 검증하지 않았다.
- 수치는 문서에 적힌 조건과 함께 기준 답안에 넣는다. 측정값과 계산값을 구분한다.

노션 학습 자료는 추후 **명시적으로 연결·가져오기**한 후 맞춤형 질문 출제에 사용할 수 있다.

---

## 6. Android 앱 설계

### 6.1 화면

- **Home:** 다음 학습 시각, 오늘 학습 여부, ‘지금 연습하기’ 버튼.
- **Schedule:** 요일/시간/분량/주제/알림음 설정.
- **Incoming Session:** AI 면접관 이름, 받기/거절/미루기, 알림음 재생.
- **Interview:** 현재 주제/질문, 말하기 상태, 음성 인식 텍스트, 다시 듣기, 힌트, 종료.
- **Summary:** 정답/부분정답/오답 수, 누락 개념, 다음 복습.
- **History:** 지난 세션과 질문별 해설, 세션 삭제.
- **Settings:** 서버 주소·API 토큰 입력, 알림 권한 안내, Tailscale 연결 확인.

### 6.2 알림·오디오 처리

- `POST_NOTIFICATIONS`, `RECORD_AUDIO` 등 필요한 권한을 **해당 기능을 처음 사용할 때** 요청.
- Android 12+의 정확 알람 특별 접근과 Android 14+ 전체 화면 알림 제한을 기능 단위로 확인.
- 재부팅 후 `BOOT_COMPLETED`에서 다음 알람 재등록(사용자가 설치 후 앱을 한 번 실행했고 OS 제한을 충족한다는 전제).
- Android `SpeechRecognizer`는 **항상 켜 놓는 연속 음성 스트리밍용이 아니다**. 질문 TTS 종료 후 단발 인식 세션을 시작하는 턴 기반 구조를 사용한다.
- `SpeechRecognizer.isOnDeviceRecognitionAvailable()` 등으로 로컬 인식 가능 여부를 확인하며, 미지원 기기에서는 이용 가능한 시스템 음성 서비스로 폴백하되 네트워크 전송 가능성을 명시한다.
- TTS 중 마이크 중지/에코 억제. 블루투스 이어폰·스피커 전환과 통화 볼륨 별도 테스트.
- 면접 도중 화면이 잠겨도 오디오를 계속 유지하려면 적절한 Foreground Service 유형/권한 및 OS 제한을 준수한다. 시작은 **사용자가 수락한 다음**으로 제한한다.

**Android 17 대응(Z 폴드 8 기준):**

- **백그라운드 오디오 제한:** 앱이 백그라운드면 재생·오디오 포커스·볼륨 변경이 막히고, 재생과 볼륨 호출은 오류 없이 실패한다. 받기 후 화면이 보일 때 Foreground Service를 시작한다. 수신 알림음은 알림 채널 소리로 내거나, 정확 알람 권한과 `USAGE_ALARM`을 함께 쓴다.
- **큰 화면 방향 고정 해제:** API 37을 타깃으로 하면 폴드 안쪽 화면에서 방향·크기 고정을 끌 수 없다. 접은 화면과 펼친 화면 모두에 맞게 레이아웃을 만든다.
- **`ACCESS_LOCAL_NETWORK`:** API 37 타깃 앱의 LAN 통신에 필요한 새 권한이다. Tailscale 주소가 해당하는지는 문서에 없으므로 M4에서 실기기로 확인한다.
- **`usesCleartextTraffic` 폐지 예정:** 쓰지 않는다. 9.1의 HTTPS 방식을 기본으로 한다.

### 6.3 주요 모듈(예정)

```text
android/app/src/main/java/.../
  presentation/home/
  presentation/schedule/
  presentation/incoming/
  presentation/interview/
  presentation/history/
  alarm/AlarmScheduler.kt
  alarm/AlarmReceiver.kt
  alarm/BootReceiver.kt
  audio/SpeechRecognitionController.kt
  audio/TextToSpeechController.kt
  data/InterviewApi.kt
  data/LocalScheduleRepository.kt
  security/ApiTokenStore.kt
```

---

## 7. PC 서버 설계

### 7.1 REST API 초안

> 모든 경로는 `/api/v1` 기준. v1은 요청/응답 JSON 방식이며 향후 서버 이벤트 스트리밍을 별도 추가한다.  
> `/health`를 제외한 모든 경로는 `Authorization: Bearer <API 토큰>`을 요구한다. 토큰은 하나이며 PC 서버의 환경 변수와 앱에만 보관한다.

| 메서드/경로 | 설명 |
|---|---|
| `GET /health` | 서버 상태 점검(민감 정보 없음) |
| `POST /sessions` | 면접 시작. 요청: `source`(`SCHEDULED`/`MANUAL`), `scheduledDate`, `topics`, `difficulty`, `durationMinutes`, `reviewRatio`, `clientRequestId` |
| `GET /sessions/{id}` | 현재 세션 및 질문 조회 |
| `DELETE /sessions/{id}` | 세션 삭제. 연결된 턴과 복습 항목도 함께 삭제 |
| `POST /sessions/{id}/answers` | `turnId`, `recognizedText`, `clientRequestId` 전송 |
| `POST /sessions/{id}/hint` | 힌트 요청 |
| `POST /sessions/{id}/end` | 면접 종료 및 요약 저장. 요약은 Claude 호출 없이 DB 집계로 만든다 |
| `GET /sessions/{id}/summary` | 세션 요약 조회 |
| `GET /reviews/due` | 복습 대상 조회 |
| `GET /history?from=&to=` | 날짜별 이력 목록 |

예시 응답:

```json
{
  "sessionId": 12,
  "turnId": 87,
  "status": "ASKING",
  "questionText": "Pod의 Readiness Probe와 Liveness Probe 차이를 설명해 보세요.",
  "durationLimitSeconds": 900
}
```

### 7.2 실패 처리

- **서버 오프라인:** 휴대폰 알림은 울림 → 받기 후 “서버 연결 실패” 표시 → 재시도. Claude API 호출 없음.
- **Tailscale 미연결:** 연결 상태 안내 후 VPN 수동 활성화 유도. 민감한 API를 인터넷에 직접 공개하지 않음.
- **Claude API 429/5xx/크레딧 소진:** 제한된 횟수의 지수 백오프, 실패 안내, 이미 저장한 진행 내용 유지. 크레딧 부족 시 추가 자동 결제를 시도하지 않음.
- **STT 오류:** 짧은 재시도, 텍스트 직접 입력 대안 제공.
- **TTS 실패:** 텍스트로 질문/해설 표시하고 수동 재생 가능.
- **세션 중 중복 제출:** `clientRequestId`와 `turnId` 기반 멱등 처리. 답변을 먼저 저장해 커밋하고 트랜잭션 밖에서 Claude를 호출한다. 처리 중에 같은 요청이 다시 오면 Claude를 또 호출하지 않는다.
- **응답 대기:** SDK는 타임아웃도 재시도하므로 최대 대기는 `timeout × (재시도 + 1)`이다. 앱 타임아웃을 이보다 길게 잡는다.
- **PC 재시작:** PC는 24시간 켜두지만 Windows Update가 재시작할 수 있다. 자동 로그인은 쓰지 않으며, 재시작되면 앱에 “서버 연결 실패”가 표시되고 사용자가 PC에 로그인하면 Docker Desktop 자동 시작과 `restart: always`로 서버가 복구된다. DB 기반으로 세션을 복구하고, 원치 않는 자동 면접 시작은 막는다.
- **Windows Update 재시작 시각:** 07:30 알람과 겹치지 않도록 활성 시간을 06:00~24:00으로 둔다(현재 08:00~02:00, 사용자가 직접 변경).
- **시간대/날짜 변경:** `Asia/Seoul`을 저장하고 다음 알람 재계산. DST가 있는 타 지역에 확장하는 경우 별도 처리.

### 7.3 백엔드 디렉터리 제안

```text
backend/src/main/java/.../
  api/InterviewController.java
  application/InterviewOrchestrator.java
  application/QuestionPlanner.java
  application/QuestionGenerator.java
  application/AnswerGrader.java
  application/FollowUpGenerator.java
  application/ReviewScheduler.java
  infrastructure/claude/ClaudeClientAdapter.java
  infrastructure/persistence/
  domain/session/
  domain/question/
  domain/review/
  security/ApiTokenFilter.java
```

---

## 8. 데이터베이스 설계 (v1)

| 테이블 | 핵심 컬럼 | 설명 |
|---|---|---|
| `interview_session` | `id`, `scheduled_date`, `source`, `status`, `started_at`, `ended_at`, `topic` | 세션 진행/결과 |
| `question_bank` | `id`, `topic`, `concept_key`, `difficulty`, `question_text`, `reference_points_json`, `rubric_version`, `origin`, `source_ref`, `source_commit` | 질문 및 채점 기준. `origin`은 `SEED`/`GENERATED` |
| `session_turn` | `id`, `session_id`, `turn_no`, `question_id`(꼬리질문은 null), `concept_key`, `question_text`, `expected_points_json`, `answer_text`, `client_request_id`, `processing_status`, `verdict`, `feedback_json`, `response_ms` | 질문·답변·판정 |
| `review_item` | `id`, `concept_key`, `source_turn_id`, `next_review_at`, `streak`, `priority` | 복습 상태 |
| `api_usage_log` | `id`, `session_id`, `model_id`, `input_tokens`, `output_tokens`, `estimated_cost_usd`, `created_at` | 크레딧 사용량 추적 |

스케줄과 사용자 설정 테이블은 두지 않는다(4.1 참고). 사용자가 한 명이므로 `user_id`도 두지 않는다.

**ID 타입:** 모든 테이블의 PK는 `bigint`(`GENERATED ALWAYS AS IDENTITY`)다. 앱이 재전송 식별을 위해 직접 만드는 `client_request_id`만 `uuid`다. DB가 하나이고 API가 Tailscale과 토큰 뒤에 있어, 분산 ID 생성이나 공개용 ID가 필요 없기 때문이다.

**주요 제약:**

- `source = 'SCHEDULED'`인 행에 한해 `interview_session(scheduled_date)` 유니크를 적용한다. 사용자가 직접 시작한 **수동 세션**(`MANUAL`)은 `MAX_DAILY_SESSIONS` 안에서 같은 날 여러 번 허용.
- `session_turn(session_id, turn_no)`와 `session_turn(session_id, client_request_id)` 유니크로 답변 중복 방지.
- 꼬리질문은 `question_bank`에 없으므로 채점 기준을 `session_turn.expected_points_json`에 저장한다.
- 복습 대상은 `concept_key`로 찾는다. 같은 개념의 변형 질문을 고르기 위해 `question_bank`와 `session_turn`에 모두 둔다.
- `question_bank`는 동일 질문을 재사용 가능. `session_turn`에는 질문 원문과 평가 결과를 스냅샷으로 남겨 사후 재현 가능하게 한다.
- 답변 텍스트는 세션 단위로 삭제한다(`DELETE /sessions/{id}`). `session_turn`과 `review_item`은 함께 삭제되도록 FK를 `ON DELETE CASCADE`로 두고, 크레딧 집계가 유지되도록 `api_usage_log.session_id`는 `ON DELETE SET NULL`로 둔다. 원시 녹음 파일은 기본적으로 저장하지 않는다.

---

## 9. 보안·개인정보·비용

### 9.1 반드시 지킬 보안 원칙

1. Claude API 키는 **PC 서버의 `infra/.env`**(Git 제외)에만 보관하고 Compose가 백엔드 컨테이너에만 전달한다. APK, Git, 공개 로그, 채팅에 포함 금지.
2. 앱과 PC는 Tailscale을 통해서만 접근하고, 서버 포트를 인터넷에 공개하지 않는다. Android는 기본적으로 평문 HTTP를 막으므로 `tailscale serve`로 HTTPS를 붙이는 것을 기본으로 하고, 불가피하면 network security config로 해당 호스트만 cleartext를 허용한다.
3. 앱 인증은 고정 API 토큰 하나로 한다. 폰 분실이나 유출 시 PC의 환경 변수를 바꾸고 앱에 다시 입력하며, Tailscale에서도 해당 기기를 제거한다.
4. 대화 텍스트와 학습 기록은 기본 비공개. **사용자 명시 동의 없이 Notion 등에 전송하지 않음.**
5. 마이크 권한은 사용자가 수락할 때만 사용. 녹음 중임을 UI/알림으로 표시.
6. 서버에서 요청 크기 제한, 세션당 호출 상한, 호출 타임아웃, 중복 제출 방지를 적용.
7. 개인 프로젝트라도 운영자 기능이나 디버그 API를 외부에 노출하지 않는다.

### 9.2 비용 계획

- **통신사 발신 비용:** 0원 (실제 이동전화 호출을 하지 않기 때문).
- **알람/기본 UI:** 별도 유료 서비스 비용 없음.
- **Android STT/TTS:** 기본 내장 서비스 사용 시 별도 외부 음성 API 계약 없이 시작 가능. 일부 인식 기능은 기기·네트워크·서비스 제공자 정책에 의존하며 데이터 사용량/오프라인 지원은 확인 필요.
- **PC 서버:** 보유 PC 활용, 별도 임대료 없음. 전기 및 인터넷 비용은 기존 운영에 포함되거나 추가될 수 있음.
- **Claude:** Ultra의 월간 API 크레딧 내에서 사용한다. 테스트는 Haiku로 비용을 줄이고 최종은 Sonnet으로 전환한다. 자동 충전은 쓰지 않으므로 크레딧이 없으면 오류만 안내한다. **크레딧이 곧 무제한 무료 호출을 의미하지는 않는다.**
- **안전장치:** `MAX_DAILY_SESSIONS=2`, `MAX_SESSION_TURNS=30`, `MAX_DAILY_INPUT_TOKENS`, `MAX_DAILY_OUTPUT_TOKENS` 등 설정 기반 상한. 사용량은 모델별 실제 가격과 토큰 소비를 기준으로 추산.

---

## 10. 개발 로드맵 (M0~M6)

> 기간은 작업량 추정을 위한 **계획용 구분**이며, 완료일이나 속도를 보장하지 않는다. 각 단계 완료 기준을 충족해야 다음 단계로 넘어간다.

### 10.1 구현 단계

| 단계 | 만드는 것 | 완료 확인 | 사용자 준비물 |
|---|---|---|---|
| **M0 기반** | 저장소, 단계별 이슈, CI, 브랜치 보호, Spring Boot, Compose DB, Flyway 스키마, 토큰 필터, `/health` | 토큰 없는 호출이 401, 재기동 후 데이터 유지, CI 통과 후 PR 머지 | `infra/.env`에 API 키 입력 |
| **M1 Claude 연결** | `ClaudeClientAdapter`, 구조화 출력, 타임아웃과 재시도, `api_usage_log`, 일일 상한 | 질문 생성과 채점 JSON이 스키마대로 오고 토큰이 기록됨 | 자동 충전 OFF 확인 |
| **M2 텍스트 면접** | `InterviewOrchestrator`, 세션과 턴 API, FlowTicket 시드 질문, dev 전용 콘솔 | 5문제 이상 진행 후 재조회 가능, 같은 답을 다시 보내도 채점 1회 | 시드 질문 검토 |
| **M3 품질 게이트** | 정답·부분정답·오답 예시 30건 이상의 평가셋, 프롬프트 조정 | Haiku 채점을 수동 판정과 비교, 통과 전에는 앱 개발 안 함 | 평가셋 수동 판정 |
| **M4 음성 앱** | Compose 앱, TTS와 STT, 수정과 재시도, 요약 화면, Tailscale 연결 | Wi-Fi와 모바일 데이터에서 각각 10분 완주, 접은 화면·펼친 화면 모두 동작 | Tailscale 설치, USB 디버깅 켜기 |
| **M5 알람** | `AlarmManager`, `BootReceiver`, 받기·거절·미루기, 권한 안내 | 화면이 꺼지고 앱이 종료된 상태에서 울림, 거절 시 Claude 호출 없음 | 폴드 실기기 테스트 |
| **M6 복습·운영** | `ReviewScheduler`, 복습 출제, 사용량 상한 점검, Sonnet 전환, DB 백업 | 3회 이상 진행 시 이전 오답이 재출제됨, Sonnet으로 평가셋 재확인. 여기까지가 MVP | Docker Desktop 자동 시작, 활성 시간 변경 |

Phase와의 관계: Phase 1 = M0~M3, Phase 2 = M4, Phase 3 = M5, Phase 4 = M6.

### 10.2 구현 원칙

- Claude 호출은 인터페이스 뒤로 분리한다. 자동 테스트는 Claude를 호출하지 않고 DB는 Testcontainers로 실행한다. 실제 API 비용은 M3의 평가와 수동 확인에만 쓴다.
- 영속성은 Spring Data JPA, 스키마는 Flyway로 관리한다. 조건부 유니크 인덱스는 Flyway SQL로 작성한다.
- dev 전용 콘솔은 `dev` 프로필에서만 켠다.
- `backend`와 `android`는 한 저장소의 독립된 Gradle 빌드로 둔다. Gradle은 wrapper를 쓴다.
- 작업 폴더는 `C:\Users\GUNWOO\Downloads\ai_interview`이고, 공개 저장소 [gumwoo/ai_interview](https://github.com/gumwoo/ai_interview)로 관리한다.
- 공개 저장소이므로 첫 커밋부터 `infra/.env`, `local.properties`, 앱 서명 키, `infra/backup/`, 로그를 `.gitignore`에 넣고, API 토큰과 Tailscale 호스트 이름은 커밋하지 않는다. 비밀값이 한 번이라도 올라가면 폐기하고 새로 발급한다.

### Phase 1. 서버·Claude·텍스트 면접 (최우선, M0~M3)

- [ ] Git 저장소 구조 생성(`backend`, `android` 독립 빌드)
- [ ] M0~M6 이슈 생성, GitHub Actions(빌드·테스트·gitleaks), `main` 브랜치 보호 설정
- [ ] Spring Boot + PostgreSQL + Flyway + Docker Compose 구성
- [ ] Claude API 키 관리 및 메시지 호출 검증(`claude-haiku-5-5`)
- [ ] `/health` 외 API에 고정 토큰 인증 적용
- [ ] `QuestionPlanner` / `QuestionGenerator` / `AnswerGrader` / `FollowUpGenerator` 구현
- [ ] JSON 형식 응답 검증, 재시도, 타임아웃 처리
- [ ] 세션/턴/채점 결과 DB 저장
- [ ] FlowTicket 문서 기반 시드 질문 초안 작성 및 사용자 검토
- [ ] 평가셋 30건 이상 작성 및 수동 판정
- [ ] dev 전용 콘솔로 **텍스트 면접 전체 흐름** 검증

**완료 조건:** 5문제 이상을 풀면서 정답·부분정답·오답에 맞는 피드백 및 꼬리질문이 생성되고 결과가 재조회되며, 평가셋 비교를 통과한다.

### Phase 2. 갤럭시 음성 면접 (M4)

- [ ] Tailscale 설치(PC·폴드, 같은 계정), MagicDNS와 HTTPS Certificates 활성화
- [ ] 폴드 USB 디버깅 활성화, `adb` PATH 설정
- [ ] Kotlin/Compose 앱 생성 및 서버 연결(주소·토큰 입력)
- [ ] 접은 화면·펼친 화면 레이아웃
- [ ] 질문 TTS 재생, STT 응답 입력/수정
- [ ] 오디오 포커스, 무음 처리, 반복 재생 및 오류 처리
- [ ] Android 17 백그라운드 오디오 제한 대응, `ACCESS_LOCAL_NETWORK` 필요 여부 확인
- [ ] 세션 종료/요약 화면 구현
- [ ] Tailscale로 집 밖 모바일 데이터 연결 검증

**완료 조건:** 동일 Wi-Fi/모바일 데이터 환경에서 각각 10분간 음성 면접을 끝까지 진행할 수 있다.

### Phase 3. 자동 기상 알림/통화형 UX (M5)

- [ ] 반복 스케줄 저장, AlarmManager 예약
- [ ] 알림 수신 화면, 받기·거절·5분 미루기
- [ ] 재부팅/배터리 절전/화면 잠금/방해금지 상황 테스트
- [ ] 정확 알람/전체 화면 권한 및 제한 안내
- [ ] 알람 중복 실행 방지와 미응답 상태 처리

**완료 조건:** 휴대폰 화면이 꺼져 있고 앱을 열어두지 않은 상태에서도 지정한 시각에 사용자가 인지 가능한 알림이 발생하며, 받기를 누르면 세션이 시작된다. OS가 전체 화면을 제한하면 알림 방식으로 정상 작동한다.

### Phase 4. 복습·개인화·운영 안정성 (M6)

- [ ] 주제별 난이도·신규/복습 비율 선택
- [ ] 오답 복습 스케줄 및 출제 중복 회피
- [ ] 면접 이력 화면, 세션 삭제
- [ ] (선택) 월별 정답률·취약 주제 화면
- [ ] Claude API 사용량/일별 상한/실패 로그
- [ ] `claude-sonnet-5-5`로 전환하고 평가셋 재확인
- [ ] Docker Desktop 자동 시작, Windows Update 활성 시간 변경(재시작 시 로그인 후 복구), DB 백업 및 복원
- [ ] (선택) Notion 오답 노트 자동 동기화

**완료 조건:** 3회 이상의 세션을 진행했을 때 이전 세션의 오답·부분정답이 후속 세션에 복습 출제되고, 점수 및 이력이 유지된다.

---

## 11. 테스트 전략 및 수용 기준

### 11.1 기능 테스트

| 테스트 | 절차 | 기대 결과 |
|---|---|---|
| 정답 평가 | 정확한 답변 발화 | 정답 + 합리적 심화 질문 |
| 부분정답 | 핵심 개념 한 가지 누락 | 누락 설명 또는 보충 질문 |
| 오답 평가 | 명백히 잘못된 답변 | 정확한 정정과 쉬운 재확인 질문 |
| 모름 | “모르겠어요” 발화 | 개념 설명 후 쉬운 이해 확인 |
| STT 오인식 | 일부 단어가 잘못 인식된 상태 | 제출 전 수정 또는 재인식 가능 |
| 서버 오프라인 | PC 네트워크 차단 | 알림은 울림, 앱은 재연결 안내 |
| API 사용 불가 | 크레딧 소진/호출 제한 모의 | 명확한 오류 안내, 추가 과금 방지 |
| 거절/미응답 | 알림 거절 또는 제한시간 경과 | 세션 생성/Claude 호출 없음 |
| 앱/PC 재시작 | 세션 중 프로세스 재실행 | 중복 답변 저장 없이 복구 또는 종료 안내 |
| 재부팅 후 알림 | 갤럭시 재부팅 | 재예약 및 예약 시각의 알림 작동 확인 |
| 장시간 음성 | 10~15분 연속 면접 | 마이크/TTS 리소스 누수·앱 종료 없음 |
| 데이터 삭제 | 세션 1건 삭제 | 관련 답변/복습 데이터 삭제 확인 |
| API 인증 | 토큰 없이 또는 틀린 토큰으로 호출 | `/health` 외 경로는 401, Claude 호출 없음 |
| 모델 전환 | `CLAUDE_MODEL`을 Sonnet으로 변경 후 평가셋 재실행 | 스키마 오류 없음, 수동 판정과 비교해 품질 확인 |
| 폴드 전환 | 면접 중 화면 접기/펼치기 | 진행 상태와 음성 세션 유지, 화면 깨짐 없음 |

### 11.2 측정해야 할 품질 지표(초기 목표값)

- 질문 생성 완료부터 음성 재생 시작까지 **p95 7초 이내**를 목표로 측정. 이는 목표일 뿐, 네트워크/모델/STT에 따라 조정 가능.
- 알림은 정확 알람이 허용된 실제 기기에서 **설정 시각 대비 오차 측정**; 배터리 정책에 따른 예외 기록.
- 세션당 STT 실패율, 서버 API 오류율, 턴별 응답 지연, 토큰 사용량 수집.
- 채점 품질은 검증된 정답/오답/부분정답 예시 **최소 30건**을 수동 평가한 기준과 비교. Haiku 단계(M3)와 Sonnet 전환 시점(M6)에 각각 측정한다.
- ‘무리한 단정’, ‘모범답변 부정확’, ‘무관한 꼬리질문’은 우선순위 높은 품질 결함으로 분류.

### 11.3 실제 갤럭시 테스트 조건 (Z 폴드 8, Android 17)

- Wi-Fi / LTE·5G 각각
- 접은 화면 / 펼친 화면 / 면접 중 전환
- 화면 켜짐 / 잠금 / 앱 백그라운드 / 앱 프로세스 종료 후
- 방해금지 / 무음 / 이어폰 연결 / 블루투스 전환
- PC 정상 / 절전 / 꺼짐 / 인터넷 끊김
- 알람 허용 / 알람 권한 거부 / 알림 권한 거부
- Tailscale 연결 / 미연결

---

## 12. 저장소 구조 제안

```text
ai-voice-interview/
├─ README.md
├─ CLAUDE.md            # 프로젝트 작업 규칙
├─ docs/
│  ├─ rules/            # backend, android, data-and-infra, eval, git
│  ├─ PROJECT_PLAN.md
│  ├─ ADR-001-local-alarm-vs-push.md
│  ├─ ADR-002-android-stt-tts.md
│  ├─ ADR-003-private-network.md
│  ├─ api-contract.md
│  └─ test-scenarios.md
├─ android/
│  └─ app/...
├─ backend/
│  ├─ src/main/...
│  └─ src/test/...
├─ infra/
│  ├─ compose.yaml
│  ├─ .env.example
│  ├─ .env              # API 키 보관, Git 제외
│  └─ backup/
├─ prompts/
│  ├─ interviewer.md
│  ├─ grading.md
│  └─ schemas/
├─ eval/
│  └─ grading-cases.json
└─ seed/
   └─ flowticket-questions.json
```

### 12.1 추천 개발 첫 순서

1. 발급된 API 키를 `infra/.env`에 넣고 Console에서 자동 충전이 꺼져 있는지 확인.
2. PC에 백엔드 최소 프로젝트 생성 → Haiku에게 질문 1개를 받아 JSON으로 출력.
3. 출제 결과와 사용자 답변을 입력해 평가 및 후속 질문까지 받기.
4. DB에 세션 1개를 끝까지 저장하기.
5. 이후 갤럭시 앱에서 텍스트→TTS→STT를 연결.
6. 마지막으로 매일 아침 알람과 통화형 수신 UI를 붙이기.

이 순서가 권장되는 이유는 **가장 어려운 학습 품질 문제를 통화 UI보다 먼저 검증**하기 위해서다. 음성으로 들으면 틀린 해설이나 산만한 꼬리질문을 고치기 더 어렵다.

---

## 13. 미결정·검증 항목

**확정:**

- [x] 기기: 갤럭시 Z 폴드 8, Android 17
- [x] 상시 PC: 이 Windows 11 데스크톱, Docker Desktop. 자동 로그인은 쓰지 않음
- [x] 모델: 테스트 `claude-haiku-5-5`, 최종 `claude-sonnet-5-5`
- [x] 요금: Ultra, API 키 발급 완료, 자동 충전 금지
- [x] 시드 질문: FlowTicket 문서 기반, 개발·인프라 모두 포함
- [x] ID 타입: PK는 `bigint`, `client_request_id`만 `uuid`

**미결정·검증:**

- [ ] API 키가 Ultra 구독과 연결된 조직에서 발급된 것인지 확인
- [ ] Z 폴드 8의 정확 알람·전체 화면 알림 동작 실기기 확인
- [ ] `ACCESS_LOCAL_NETWORK`가 Tailscale 연결에 필요한지 확인
- [ ] Galaxy 한국어 on-device STT 지원 여부 및 정확도 테스트
- [ ] 아침 알림 기본 시각·요일·세션 길이 최종 확정
- [ ] Notion 저장 대상 및 저장 범위는 사용자가 연결을 허용한 후 결정

위 항목이 미확정이어도 **Phase 1 개발은 시작할 수 있다.**

---

## 14. 공식 참고 문서 (2026-11-02 확인)

1. [Claude Max·Ultra·Team 월간 API 크레딧](https://support.claude.com/en/articles/17154008-monthly-api-credits-for-max-ultra-and-team-plans) — 대상/월간 크레딧, 청구 및 소진 처리
2. [Claude API 공식 Java SDK](https://platform.claude.com/docs/ko/cli-sdks-libraries/sdks/java) — 클라이언트 설치/스트리밍
3. [Android 14: 전체 화면 인텐트 제한](https://developer.android.com/about/versions/14/behavior-changes-14) — 전화·알람과 전체 화면 알림 정책
4. [Android: 정확 알람 권한](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) — 권한 점검 및 대안 설계
5. [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer) — 음성 인식과 연속 인식 제한
6. [Android Foreground Service 유형](https://developer.android.com/develop/background-work/services/fgs/service-types) — 마이크 사용 및 백그라운드 제약
7. [Tailscale 설치 문서](https://tailscale.com/docs/install) — Windows/Android 사설 연결
8. [Android 17 동작 변경(모든 앱)](https://developer.android.com/about/versions/17/behavior-changes-all) — 백그라운드 오디오, `usesCleartextTraffic` 폐지 예정
9. [Android 17 동작 변경(API 37 타깃)](https://developer.android.com/about/versions/17/behavior-changes-17) — 큰 화면 고정 해제, `ACCESS_LOCAL_NETWORK`

---

## 15. 최종 MVP 완료 정의(Definition of Done)

**범위:** 4절의 P0 기능 전체와 Phase 4의 복습·사용량 상한·Sonnet 전환 항목. 이력 화면(FR-11)과 Phase 4의 나머지 항목은 MVP 이후로 둔다.

- [ ] 갤럭시에서 시간·요일을 설정할 수 있다.
- [ ] 알림에 ‘받기’를 누르면 앱에서 AI 면접을 시작할 수 있다.
- [ ] 갤럭시와 개인 PC가 서로 다른 네트워크에 있어도 안전하게 연결할 수 있다.
- [ ] 최소 10분간 AI가 질문 → 음성 답변 인식 → 정답 평가 → 꼬리질문을 반복한다.
- [ ] 모르면 AI가 정확한 해설을 제공하고 다시 확인한다.
- [ ] 결과가 DB에 저장되고 다음 면접에서 오답을 다시 출제한다.
- [ ] API 키가 휴대폰 APK와 저장소에 포함되지 않는다.
- [ ] Claude API 사용량 상한과 연결 실패 대책이 있고, 자동 충전은 꺼져 있다.
- [ ] 최종 모델 `claude-sonnet-5-5`로 평가셋 비교를 통과한다.
- [ ] Android 권한 거부·서버 오프라인·앱 재시작 상황에서 사용자에게 복구 경로를 안내한다.

**최종 방향:** ‘실제 전화망 없이, 보이스톡 같은 수신 경험을 제공하는 개인용 AI 기술 면접관’. 처음부터 통신 시스템을 만들지 않고 **스마트폰 알람 + 턴 기반 음성 대화 + Claude 지능 + 개인 PC 서버**로 시작해 완성도를 확보한다.
