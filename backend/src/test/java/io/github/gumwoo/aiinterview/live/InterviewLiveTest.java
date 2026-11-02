package io.github.gumwoo.aiinterview.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.AnswerView;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SessionView;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.StartSession;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SubmitAnswer;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SummaryView;
import io.github.gumwoo.aiinterview.application.interview.InterviewOrchestrator;
import io.github.gumwoo.aiinterview.application.interview.InterviewStore;
import io.github.gumwoo.aiinterview.domain.session.InterviewSession.SessionSource;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import io.github.gumwoo.aiinterview.infrastructure.persistence.ApiUsageLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 Claude API로 텍스트 면접을 끝까지 진행한다. 크레딧을 쓰며 {@code ./gradlew liveTest}로만 실행된다.
 */
@Tag("live")
@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.interview.max-follow-up-depth=1" })
@Import(TestcontainersConfiguration.class)
class InterviewLiveTest {

	private static final int TARGET_QUESTIONS = 5;
	private static final int MAX_TURNS = 12;

	@Autowired
	private InterviewOrchestrator orchestrator;

	@Autowired
	private InterviewStore store;

	@Autowired
	private ApiUsageLogRepository usageRepository;

	@Test
	void should_finish_text_interview_with_at_least_five_questions() {
		usageRepository.deleteAll();
		SessionView session = timed("start", () -> orchestrator.start(new StartSession(SessionSource.MANUAL,
				LocalDate.now(), List.of("Kubernetes", "Concurrency"), "intermediate", 60, new BigDecimal("0.40"),
				UUID.randomUUID())));
		Long sessionId = session.sessionId();
		Long turnId = session.turnId();
		System.out.println("[live] Q: " + session.questionText());

		int turnCount = 0;
		long questions = 0;
		UUID lastRequestId = null;
		Long lastTurnId = null;
		AnswerView lastAnswer = null;
		while (turnCount < MAX_TURNS && turnId != null) {
			Long currentTurn = turnId;
			SessionTurn turn = store.getTurn(sessionId, currentTurn);
			String answerText = answerFor(turnCount, turn);
			UUID requestId = UUID.randomUUID();
			AnswerView answered = timed("answer", () -> orchestrator.submitAnswer(sessionId,
					new SubmitAnswer(currentTurn, answerText, requestId)));
			System.out.printf("[live] A: %s%n[live]   -> %s %s | %s%n", answerText, answered.verdict(),
					answered.nextAction(), answered.feedbackToSpeak());
			System.out.println("[live] Q: " + answered.nextQuestionText());
			lastRequestId = requestId;
			lastTurnId = currentTurn;
			lastAnswer = answered;
			turnId = answered.nextTurnId();
			turnCount++;
			questions = store.getTurns(sessionId).stream().filter((t) -> t.getQuestionId() != null).count();
			if (questions > TARGET_QUESTIONS) {
				break;
			}
		}
		assertThat(questions).isGreaterThan(TARGET_QUESTIONS);

		long usageBeforeResend = usageRepository.count();
		AnswerView resent = orchestrator.submitAnswer(sessionId,
				new SubmitAnswer(lastTurnId, "재전송이므로 이 내용은 저장되지 않습니다", lastRequestId));
		assertThat(resent).isEqualTo(lastAnswer);
		assertThat(usageRepository.count()).isEqualTo(usageBeforeResend);
		System.out.println("[live] resend: 추가 호출 없음");

		SummaryView summary = orchestrator.end(sessionId);
		System.out.println("[live] summary: " + summary);
		assertThat(summary.answeredCount()).isGreaterThanOrEqualTo(TARGET_QUESTIONS);

		SessionView reread = orchestrator.get(sessionId);
		assertThat(reread.turns()).hasSizeGreaterThanOrEqualTo(TARGET_QUESTIONS);
		assertThat(reread.turns().stream().filter((t) -> t.verdict() != null).count())
			.isEqualTo(summary.answeredCount());

		long inputTokens = usageRepository.findAll().stream().mapToLong((u) -> u.getInputTokens()).sum();
		long outputTokens = usageRepository.findAll().stream().mapToLong((u) -> u.getOutputTokens()).sum();
		BigDecimal cost = usageRepository.findAll()
			.stream()
			.map((u) -> u.getEstimatedCostUsd())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		System.out.printf("[live] usage: calls=%d input=%d output=%d cost=%s%n", usageRepository.count(), inputTokens,
				outputTokens, cost);
	}

	/** 정답, 일부 누락, 모름을 번갈아 답한다. */
	private static String answerFor(int index, SessionTurn turn) {
		List<String> points = turn.getExpectedPoints();
		return switch (index % 3) {
			case 0 -> String.join(" ", points);
			case 1 -> points.get(0);
			default -> "잘 모르겠어요.";
		};
	}

	private static <T> T timed(String label, java.util.function.Supplier<T> call) {
		long start = System.nanoTime();
		T result = call.get();
		System.out.printf("[live] latency: %s %d ms%n", label, (System.nanoTime() - start) / 1_000_000);
		return result;
	}

}
