# 백엔드 규칙

Java 17, Spring Boot, Gradle, Spring Data JPA, Flyway 기준이다.

## 구조

- 주입은 생성자로 한다. 필드 `@Autowired`를 쓰지 않는다.
- 컨트롤러는 입출력과 검증만 맡고, 로직은 서비스에 둔다. 엔티티를 응답으로 직접 반환하지 않고 DTO를 쓴다.
- 예외는 `@RestControllerAdvice`에서 처리한다. 빈 `catch`를 두지 않고, 응답에 스택트레이스나 SQL을 노출하지 않는다.
- HTTP 상태를 정확히 쓴다. 없는 자원은 `404`, 생성은 `201`이다.
- `Optional.get()` 대신 `orElseThrow()`를 쓴다.
- `@Service`, `@Component`에 변경 가능한 인스턴스 필드를 두지 않는다.

## 트랜잭션과 멱등성

- `@Transactional`은 서비스 계층에 두고, 조회는 `readOnly = true`로 한다.
- Claude 호출 중에는 트랜잭션을 열어 두지 않는다. 답변 저장 → 커밋 → Claude 호출 → 결과 저장 순서다.
- 같은 빈 안의 메서드 호출에는 `@Transactional`이 적용되지 않는다. 저장 단계는 별도 빈으로 나눈다.
- 멱등성은 상태를 바꾸기 전에 확인한다. `clientRequestId`로 기존 요청을 찾고, 처리 중이면 Claude를 다시 호출하지 않는다.
- 세션 상태는 계획서 4.2의 전이만 허용하고, 그 밖의 전이는 거부한다.
- 재시도는 SDK 설정을 쓴다. 그 위에 재시도를 또 얹지 않는다.

## JPA

- `@Enumerated`는 `EnumType.STRING`으로 한다.
- 컬렉션에 `EAGER`를 쓰지 않는다. 필요한 곳만 `JOIN FETCH`나 `@EntityGraph`로 가져온다.
- 스키마는 Flyway로만 바꾼다. Hibernate 자동 DDL을 쓰지 않는다.
- 쿼리는 바인드 파라미터를 쓰고, 문자열을 이어 붙이지 않는다. 변경 `@Query`에는 `@Modifying`을 붙인다.
- 목록 API는 페이지나 날짜 범위로 크기를 제한한다.
- 커넥션 풀은 기본값으로 시작하고, 측정 없이 키우지 않는다.

## 보안

- `/health`를 제외한 모든 경로는 기본 거부한다. 토큰이 없거나 틀리면 `401`이다.
- API 토큰 비교는 고정 시간 비교(`MessageDigest.isEqual`)로 한다.
- 무상태 토큰 인증이므로 CSRF는 끄고, 설정 위치에 이유를 주석으로 남긴다.
- 요청 DTO에 `@Valid`와 크기 제한을 둔다. 답변 텍스트 길이도 제한한다.
- 비밀값은 환경 변수로만 받고, 없으면 기동 시 실패시킨다.
- API 토큰, Claude API 키, 답변 원문을 로그에 남기지 않는다.

## 테스트

- 범위에 맞는 테스트를 쓴다. 서비스는 Mockito 단위 테스트, 컨트롤러는 `@WebMvcTest`, 리포지토리는 `@DataJpaTest`다. 단위 테스트에 `@SpringBootTest`를 쓰지 않는다.
- DB 테스트는 Testcontainers의 실제 PostgreSQL로 하고, `@ServiceConnection`으로 연결한다. H2를 쓰지 않는다.
- 이 프로젝트는 Spring Boot 4.1이므로 `@MockBean` 대신 `@MockitoBean`을 쓴다.
- Claude는 인터페이스 뒤로 분리해 가짜 구현으로 테스트한다.
- `Thread.sleep()` 대신 Awaitility를 쓴다.
- 테스트 이름은 동작을 설명한다. 예: `should_return_401_when_token_missing`
- 커버리지 수치나 테스트 선행 작성을 강제하지 않는다. 멱등성, 상태 전이, 인증, 복습 선택은 반드시 테스트한다.

---

일부 내용은 [Everything Claude Code](https://github.com/affaan-m/everything-claude-code)(MIT, Copyright (c) 2026 Affaan Mustafa, 커밋 `b38be6a`)의 `springboot-tdd`, `jpa-patterns`, `springboot-security`, `java-reviewer`를 참고해 프로젝트에 맞게 다시 썼다.
