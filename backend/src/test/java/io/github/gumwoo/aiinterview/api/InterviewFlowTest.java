package io.github.gumwoo.aiinterview.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jayway.jsonpath.JsonPath;
import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GeneratedQuestion;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GradingResult;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.NextAction;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import io.github.gumwoo.aiinterview.application.llm.LlmException;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.QuestionOrigin;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.ReferencePoints;
import io.github.gumwoo.aiinterview.infrastructure.persistence.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.claude.api-key=test-key", "app.interview.max-session-turns=6",
		"app.interview.max-follow-up-depth=1" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, InterviewFlowTest.ClockConfiguration.class })
class InterviewFlowTest {

	private static final String BEARER = "Bearer " + TestcontainersConfiguration.TEST_API_TOKEN;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private QuestionBankRepository questions;

	@Autowired
	private MutableClock clock;

	@MockitoBean
	private InterviewLlm llm;

	@BeforeEach
	void setUp() {
		jdbc.update("TRUNCATE interview_session, question_bank, session_turn, review_item, api_usage_log");
		clock.set(Instant.parse("2026-11-02T01:00:00Z"));
		reset(llm);
	}

	@Test
	void should_start_with_seed_question_without_calling_claude() throws Exception {
		seed("c1", "질문1");

		Map<String, Object> session = start("MANUAL", UUID.randomUUID());

		assertThat(session.get("questionText")).isEqualTo("질문1");
		assertThat(session.get("status")).isEqualTo("ASKING");
		assertThat(session.get("durationLimitSeconds")).isEqualTo(900);
		verify(llm, never()).generateQuestion(any(), any());
	}

	@Test
	void should_generate_and_store_question_when_bank_is_empty() throws Exception {
		given(llm.generateQuestion(any(), any()))
			.willAnswer((call) -> generated("generated-concept", "생성한 질문"));

		Map<String, Object> session = start("MANUAL", UUID.randomUUID());

		assertThat(session.get("questionText")).isEqualTo("생성한 질문");
		assertThat(questions.findByQuestionText("생성한 질문")).get()
			.extracting(QuestionBankItem::getOrigin)
			.isEqualTo(QuestionOrigin.GENERATED);
	}

	@Test
	void should_return_same_session_when_start_request_is_resent() throws Exception {
		seed("c1", "질문1");
		UUID requestId = UUID.randomUUID();

		Map<String, Object> first = start("SCHEDULED", requestId);
		Map<String, Object> second = start("SCHEDULED", requestId);

		assertThat(second.get("sessionId")).isEqualTo(first.get("sessionId"));
		assertThat(count("interview_session")).isEqualTo(1);
	}

	@Test
	void should_reject_second_scheduled_session_on_same_date() throws Exception {
		seed("c1", "질문1");
		start("SCHEDULED", UUID.randomUUID());

		postStart("SCHEDULED", UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULED_SESSION_EXISTS"));
	}

	@Test
	void should_allow_any_number_of_manual_sessions_in_a_day() throws Exception {
		seed("c1", "질문1");
		start("MANUAL", UUID.randomUUID());
		start("MANUAL", UUID.randomUUID());
		start("MANUAL", UUID.randomUUID());

		postStart("MANUAL", UUID.randomUUID()).andExpect(status().isOk());
	}

	@Test
	void should_create_follow_up_with_its_own_criteria_then_move_to_next_question() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.PARTIAL, NextAction.FOLLOW_UP, "꼬리질문"));

		Map<String, Object> first = answer(session, turnId(session), "답변1", UUID.randomUUID());
		assertThat(first.get("verdict")).isEqualTo("PARTIAL");
		assertThat(first.get("nextQuestionText")).isEqualTo("꼬리질문");
		assertThat(jdbc.queryForObject("""
				SELECT question_id IS NULL AND expected_points_json = '["꼬리 기준"]'::jsonb AND follow_up_depth = 1
				FROM session_turn WHERE turn_no = 2
				""", Boolean.class)).isTrue();

		Map<String, Object> second = answer(session, ((Number) first.get("nextTurnId")).longValue(), "답변2",
				UUID.randomUUID());
		assertThat(second.get("nextQuestionText")).isIn("질문1", "질문2").isNotEqualTo(session.get("questionText"));
	}

	@Test
	void should_repeat_same_question_when_answer_cannot_be_judged() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.UNDETERMINED, NextAction.CLARIFY, ""));

		Map<String, Object> answered = answer(session, turnId(session), "웅얼웅얼", UUID.randomUUID());

		assertThat(answered.get("nextQuestionText")).isEqualTo("질문1");
	}

	@Test
	void should_treat_refusal_as_undetermined_and_continue() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willThrow(new LlmException.Refused("거부"));

		Map<String, Object> answered = answer(session, turnId(session), "답변", UUID.randomUUID());

		assertThat(answered.get("verdict")).isEqualTo("UNDETERMINED");
		assertThat(answered.get("nextQuestionText")).isEqualTo("질문1");
	}

	@Test
	void should_grade_once_when_same_answer_is_resent() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));
		UUID requestId = UUID.randomUUID();

		Map<String, Object> first = answer(session, turnId(session), "답변", requestId);
		Map<String, Object> second = answer(session, turnId(session), "답변", requestId);

		assertThat(second).isEqualTo(first);
		verify(llm, times(1)).gradeAnswer(any(), any());
		assertThat(count("session_turn")).isEqualTo(2);
	}

	@Test
	void should_grade_once_when_same_answer_arrives_concurrently() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		CountDownLatch gradingStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		given(llm.gradeAnswer(any(), any())).willAnswer((call) -> {
			gradingStarted.countDown();
			release.await(5, TimeUnit.SECONDS);
			return result(Verdict.CORRECT, NextAction.NEXT_QUESTION, "");
		});
		UUID requestId = UUID.randomUUID();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<ResultActions> firstCall = executor
				.submit(() -> postAnswer(session, turnId(session), "답변", requestId));
			assertThat(gradingStarted.await(5, TimeUnit.SECONDS)).isTrue();

			postAnswer(session, turnId(session), "답변", requestId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GRADING_IN_PROGRESS"));

			release.countDown();
			firstCall.get(10, TimeUnit.SECONDS).andExpect(status().isOk());
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
		verify(llm, times(1)).gradeAnswer(any(), any());
	}

	@Test
	void should_not_call_claude_inside_transaction() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		AtomicBoolean inTransaction = new AtomicBoolean();
		given(llm.gradeAnswer(any(), any())).willAnswer((call) -> {
			inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
			return result(Verdict.CORRECT, NextAction.NEXT_QUESTION, "");
		});

		answer(session, turnId(session), "답변", UUID.randomUUID());

		assertThat(inTransaction).isFalse();
		assertThat(jdbc.queryForObject("SELECT answer_text FROM session_turn WHERE turn_no = 1", String.class))
			.isEqualTo("답변");
	}

	@Test
	void should_reject_different_request_on_answered_turn() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));
		answer(session, turnId(session), "답변", UUID.randomUUID());

		postAnswer(session, turnId(session), "다른 답변", UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TURN_ALREADY_ANSWERED"));
	}

	@Test
	void should_keep_answer_and_allow_retry_when_claude_fails() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		UUID requestId = UUID.randomUUID();
		given(llm.gradeAnswer(any(), any())).willThrow(new LlmException.Unavailable("일시 장애", null))
			.willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));

		postAnswer(session, turnId(session), "답변", requestId).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
		assertThat(jdbc.queryForObject("SELECT processing_status FROM session_turn WHERE turn_no = 1", String.class))
			.isEqualTo("FAILED");

		Map<String, Object> retried = answer(session, turnId(session), "답변", requestId);

		assertThat(retried.get("verdict")).isEqualTo("CORRECT");
		verify(llm, times(2)).gradeAnswer(any(), any());
	}

	@Test
	void should_return_429_when_claude_quota_exceeded() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willThrow(new LlmException.QuotaExceeded("상한"));

		postAnswer(session, turnId(session), "답변", UUID.randomUUID()).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LLM_QUOTA_EXCEEDED"));
	}

	@Test
	void should_keep_grade_and_create_next_turn_on_resend_when_next_question_fails() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		UUID requestId = UUID.randomUUID();
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));
		given(llm.generateQuestion(any(), any())).willThrow(new LlmException.Unavailable("일시 장애", null))
			.willAnswer((call) -> generated("c9", "생성한 질문"));

		postAnswer(session, turnId(session), "답변", requestId).andExpect(status().isServiceUnavailable());
		assertThat(jdbc.queryForObject("SELECT verdict FROM session_turn WHERE turn_no = 1", String.class))
			.isEqualTo("CORRECT");

		Map<String, Object> retried = answer(session, turnId(session), "답변", requestId);

		assertThat(retried.get("nextQuestionText")).isEqualTo("생성한 질문");
		verify(llm, times(1)).gradeAnswer(any(), any());
	}

	@Test
	void should_complete_session_when_turn_limit_reached() throws Exception {
		for (int i = 1; i <= 8; i++) {
			seed("c" + i, "질문" + i);
		}
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));
		long turn = turnId(session);

		Map<String, Object> answered = null;
		for (int i = 0; i < 6; i++) {
			answered = answer(session, turn, "답변", UUID.randomUUID());
			if (answered.get("nextTurnId") != null) {
				turn = ((Number) answered.get("nextTurnId")).longValue();
			}
		}

		assertThat(answered.get("sessionStatus")).isEqualTo("COMPLETED");
		assertThat(answered.get("nextTurnId")).isNull();
		assertThat(count("session_turn")).isEqualTo(6);
	}

	@Test
	void should_grade_last_answer_then_complete_when_time_is_over() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.CORRECT, NextAction.NEXT_QUESTION, ""));
		clock.advance(Duration.ofMinutes(15));

		Map<String, Object> answered = answer(session, turnId(session), "답변", UUID.randomUUID());

		assertThat(answered.get("verdict")).isEqualTo("CORRECT");
		assertThat(answered.get("sessionStatus")).isEqualTo("COMPLETED");
	}

	@Test
	void should_reject_new_answer_after_session_ended() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		mockMvc.perform(post(path(session) + "/end").header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isOk());

		postAnswer(session, turnId(session), "답변", UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SESSION_COMPLETED"));
	}

	@Test
	void should_summarize_from_stored_results_and_read_back_turns() throws Exception {
		seed("c1", "질문1");
		seed("c2", "질문2");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		given(llm.gradeAnswer(any(), any())).willReturn(result(Verdict.PARTIAL, NextAction.NEXT_QUESTION, ""));
		answer(session, turnId(session), "답변", UUID.randomUUID());
		reset(llm);

		mockMvc.perform(post(path(session) + "/end").header(HttpHeaders.AUTHORIZATION, BEARER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.answeredCount").value(1))
			.andExpect(jsonPath("$.verdictCounts.PARTIAL").value(1))
			.andExpect(jsonPath("$.missingPoints[0]").value("빠진 내용"));
		verify(llm, never()).gradeAnswer(any(), any());

		mockMvc.perform(get(path(session)).header(HttpHeaders.AUTHORIZATION, BEARER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.turns[0].answerText").value("답변"))
			.andExpect(jsonPath("$.turns[0].verdict").value("PARTIAL"))
			.andExpect(jsonPath("$.turns[0].feedbackToSpeak").value("피드백"));
	}

	@Test
	void should_give_hint_without_calling_claude() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());

		mockMvc
			.perform(post(path(session) + "/hint").header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"turnId\":" + turnId(session) + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hintText").value(org.hamcrest.Matchers.containsString("핵심")));
		verify(llm, never()).generateQuestion(any(), any());
	}

	@Test
	void should_delete_session_with_turns() throws Exception {
		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());

		mockMvc.perform(delete(path(session)).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isNoContent());

		assertThat(count("interview_session")).isZero();
		assertThat(count("session_turn")).isZero();
		mockMvc.perform(get(path(session)).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isNotFound());
	}

	@Test
	void should_reject_invalid_requests() throws Exception {
		mockMvc
			.perform(post("/api/v1/sessions").header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"source\":\"MANUAL\",\"topics\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		seed("c1", "질문1");
		Map<String, Object> session = start("MANUAL", UUID.randomUUID());
		postAnswer(session, turnId(session), "a".repeat(5001), UUID.randomUUID()).andExpect(status().isBadRequest());
		postAnswer(session, 999999L, "답변", UUID.randomUUID()).andExpect(status().isNotFound());
	}

	@Test
	void should_require_token_for_session_api() throws Exception {
		mockMvc.perform(get("/api/v1/sessions/1")).andExpect(status().isUnauthorized());
	}

	private void seed(String concept, String question) {
		questions.save(new QuestionBankItem("Kubernetes", concept, "intermediate", question,
				new ReferencePoints(List.of("핵심"), List.of()), QuestionOrigin.SEED, "ADR-001", "abc", clock.instant()));
	}

	private Map<String, Object> start(String source, UUID requestId) throws Exception {
		return parse(postStart(source, requestId).andExpect(status().isOk()));
	}

	private ResultActions postStart(String source, UUID requestId) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions").header(HttpHeaders.AUTHORIZATION, BEARER)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"source":"%s","scheduledDate":"2026-11-02","topics":["Kubernetes"],"difficulty":"intermediate",
					 "durationMinutes":15,"reviewRatio":0.4,"clientRequestId":"%s"}
					""".formatted(source, requestId)));
	}

	private Map<String, Object> answer(Map<String, Object> session, long turnId, String text, UUID requestId)
			throws Exception {
		return parse(postAnswer(session, turnId, text, requestId).andExpect(status().isOk()));
	}

	private ResultActions postAnswer(Map<String, Object> session, long turnId, String text, UUID requestId)
			throws Exception {
		return mockMvc.perform(post(path(session) + "/answers").header(HttpHeaders.AUTHORIZATION, BEARER)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"turnId\":%d,\"recognizedText\":\"%s\",\"clientRequestId\":\"%s\"}".formatted(turnId, text,
					requestId)));
	}

	private static Map<String, Object> parse(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
	}

	private static String path(Map<String, Object> session) {
		return "/api/v1/sessions/" + session.get("sessionId");
	}

	private static long turnId(Map<String, Object> session) {
		return ((Number) session.get("turnId")).longValue();
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private static GeneratedQuestion generated(String concept, String question) {
		return new GeneratedQuestion(concept, question, new ArrayList<>(List.of("핵심")), List.of());
	}

	private static GradingResult result(Verdict verdict, NextAction action, String followUp) {
		return new GradingResult(verdict, 0.9, List.of("맞은 내용"), List.of("빠진 내용"), List.of(), "피드백", action, followUp,
				followUp.isEmpty() ? List.of() : List.of("꼬리 기준"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

	static class MutableClock extends Clock {

		private volatile Instant now = Instant.parse("2026-11-02T01:00:00Z");

		void set(Instant instant) {
			now = instant;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
