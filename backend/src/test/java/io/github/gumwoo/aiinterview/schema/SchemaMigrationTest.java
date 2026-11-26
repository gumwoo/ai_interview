package io.github.gumwoo.aiinterview.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.claude.api-key=test-key" })
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

	private static final String UNINDEXED_FOREIGN_KEYS = """
			SELECT conrelid::regclass::text || '.' || a.attname
			FROM pg_constraint c
			JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
			WHERE c.contype = 'f'
			  AND c.connamespace = 'public'::regnamespace
			  AND NOT EXISTS (
			    SELECT 1 FROM pg_index i
			    WHERE i.indrelid = c.conrelid AND a.attnum = ANY(i.indkey)
			  )
			""";

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() {
		jdbc.update("TRUNCATE interview_session, question_bank, session_turn, review_item, api_usage_log");
	}

	@Test
	void should_reject_second_scheduled_session_on_same_date() {
		insertSession("SCHEDULED");

		assertThatThrownBy(() -> insertSession("SCHEDULED")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void should_allow_multiple_manual_sessions_on_same_date() {
		insertSession("SCHEDULED");
		insertSession("MANUAL");
		insertSession("MANUAL");

		assertThat(count("interview_session")).isEqualTo(3);
	}

	@Test
	void should_reject_duplicate_session_request_id() {
		UUID requestId = UUID.randomUUID();
		insertSession("MANUAL", requestId);

		assertThatThrownBy(() -> insertSession("MANUAL", requestId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void should_reject_duplicate_answer_request_id_in_same_session() {
		long sessionId = insertSession("MANUAL");
		UUID requestId = UUID.randomUUID();
		insertTurn(sessionId, 1, requestId);

		assertThatThrownBy(() -> insertTurn(sessionId, 2, requestId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void should_delete_turns_and_reviews_but_keep_usage_when_session_deleted() {
		long sessionId = insertSession("MANUAL");
		long turnId = insertTurn(sessionId, 1, UUID.randomUUID());
		jdbc.update("INSERT INTO review_item (concept_key, source_turn_id) VALUES ('c', ?)", turnId);
		jdbc.update("""
				INSERT INTO api_usage_log (session_id, model_id, input_tokens, output_tokens, estimated_cost_usd)
				VALUES (?, 'claude-haiku-5-5', 10, 20, 0.000011)
				""", sessionId);

		jdbc.update("DELETE FROM interview_session WHERE id = ?", sessionId);

		assertThat(count("session_turn")).isZero();
		assertThat(count("review_item")).isZero();
		assertThat(count("api_usage_log")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT session_id FROM api_usage_log", Long.class)).isNull();
	}

	@Test
	void should_remove_non_lecture_questions_but_keep_past_turns() {
		long oldQuestion = insertQuestion("Concurrency", "old-concept");
		long lectureQuestion = insertQuestion("Kubernetes_EKS", "pod-creation-flow");
		long sessionId = insertSession("MANUAL");
		long oldTurn = insertTurn(sessionId, 1, UUID.randomUUID());
		long lectureTurn = insertTurn(sessionId, 2, UUID.randomUUID());
		jdbc.update("UPDATE session_turn SET question_id = ?, concept_key = 'old-concept' WHERE id = ?", oldQuestion,
				oldTurn);
		jdbc.update("UPDATE session_turn SET question_id = ?, concept_key = 'pod-creation-flow' WHERE id = ?",
				lectureQuestion, lectureTurn);
		jdbc.update("INSERT INTO review_item (concept_key, source_turn_id) VALUES ('old-concept', ?)", oldTurn);
		jdbc.update("INSERT INTO review_item (concept_key, source_turn_id) VALUES ('pod-creation-flow', ?)",
				lectureTurn);

		new ResourceDatabasePopulator(
				new ClassPathResource("db/migration/V4__replace_seed_with_cloud_lectures.sql"))
			.execute(jdbc.getDataSource());

		assertThat(jdbc.queryForList("SELECT concept_key FROM question_bank", String.class))
			.containsExactly("pod-creation-flow");
		assertThat(count("session_turn")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT question_id FROM session_turn WHERE id = ?", Long.class, oldTurn))
			.isNull();
		assertThat(jdbc.queryForObject("SELECT question_id FROM session_turn WHERE id = ?", Long.class, lectureTurn))
			.isEqualTo(lectureQuestion);
		assertThat(jdbc.queryForList("SELECT concept_key FROM review_item", String.class))
			.containsExactly("pod-creation-flow");
	}

	@Test
	void should_have_index_on_every_foreign_key() {
		assertThat(jdbc.queryForList(UNINDEXED_FOREIGN_KEYS, String.class)).isEmpty();
	}

	private long insertSession(String source) {
		return insertSession(source, UUID.randomUUID());
	}

	private long insertSession(String source, UUID requestId) {
		return jdbc.queryForObject("""
				INSERT INTO interview_session (client_request_id, scheduled_date, source, status)
				VALUES (?, DATE '2026-11-02', ?, 'ASKING')
				RETURNING id
				""", Long.class, requestId, source);
	}

	private long insertTurn(long sessionId, int turnNo, UUID requestId) {
		return jdbc.queryForObject("""
				INSERT INTO session_turn (session_id, turn_no, concept_key, question_text, expected_points_json, client_request_id)
				VALUES (?, ?, 'c', 'q', '[]'::jsonb, ?)
				RETURNING id
				""", Long.class, sessionId, turnNo, requestId);
	}

	private long insertQuestion(String topic, String conceptKey) {
		return jdbc.queryForObject("""
				INSERT INTO question_bank (topic, concept_key, difficulty, question_text, reference_points_json, origin)
				VALUES (?, ?, 'intermediate', ?, '{"expectedPoints":[],"commonMistakes":[]}'::jsonb, 'SEED')
				RETURNING id
				""", Long.class, topic, conceptKey, conceptKey + " 질문");
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

}
