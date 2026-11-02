package io.github.gumwoo.aiinterview.application.interview;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.claude.api-key=test-key" })
@Import(TestcontainersConfiguration.class)
class StartupJobsTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private InterruptedGradingRecovery recovery;

	@BeforeEach
	void cleanTables() {
		jdbc.update("TRUNCATE interview_session, question_bank, session_turn, review_item, api_usage_log");
	}

	@Test
	void should_make_interrupted_grading_retryable_on_startup() {
		Long sessionId = jdbc.queryForObject("""
				INSERT INTO interview_session (client_request_id, scheduled_date, source, status)
				VALUES (gen_random_uuid(), DATE '2026-11-02', 'MANUAL', 'GRADING') RETURNING id
				""", Long.class);
		jdbc.update("""
				INSERT INTO session_turn (session_id, turn_no, concept_key, question_text, expected_points_json, processing_status)
				VALUES (?, 1, 'c', 'q', '[]'::jsonb, 'GRADING')
				""", sessionId);

		recovery.run(null);

		assertThat(jdbc.queryForObject("SELECT processing_status FROM session_turn", String.class)).isEqualTo("FAILED");
	}

}
