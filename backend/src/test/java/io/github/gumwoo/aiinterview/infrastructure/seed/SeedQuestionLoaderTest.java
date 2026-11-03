package io.github.gumwoo.aiinterview.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

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
class SeedQuestionLoaderTest {

	private static final Set<String> TOPICS = Set.of("Concurrency", "Transaction_Payment", "Redis_Queue",
			"Event_Outbox", "Spring_JPA", "Auth_Security", "Kubernetes", "Deployment_GitOps", "AWS", "Docker_CI",
			"LoadTest_Observability");

	private static final int SEED_COUNT = 60;

	@Autowired
	private SeedQuestionLoader loader;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() throws Exception {
		jdbc.update("TRUNCATE interview_session, question_bank, session_turn, review_item, api_usage_log");
		loader.run(null);
	}

	@Test
	void should_load_every_seed_question() {
		assertThat(count("SELECT count(*) FROM question_bank WHERE origin = 'SEED'")).isEqualTo(SEED_COUNT);
	}

	@Test
	void should_not_duplicate_when_loaded_again() throws Exception {
		loader.run(null);

		assertThat(count("SELECT count(*) FROM question_bank")).isEqualTo(SEED_COUNT);
	}

	@Test
	void should_refresh_points_and_source_when_seed_file_changes() throws Exception {
		jdbc.update("""
				UPDATE question_bank
				SET reference_points_json = '{"expectedPoints":["낡은 내용"],"commonMistakes":[]}'::jsonb,
				    source_commit = 'old'
				WHERE concept_key = 'seat-hold-conditional-update'
				""");

		loader.run(null);

		assertThat(count("SELECT count(*) FROM question_bank")).isEqualTo(SEED_COUNT);
		assertThat(count("""
				SELECT count(*) FROM question_bank
				WHERE concept_key = 'seat-hold-conditional-update'
				  AND reference_points_json::text NOT LIKE '%낡은 내용%'
				  AND source_commit <> 'old' AND rubric_version = 2
				""")).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM question_bank WHERE rubric_version <> 1")).isEqualTo(1);
	}

	@Test
	void should_not_touch_generated_question_with_same_text() throws Exception {
		jdbc.update("""
				UPDATE question_bank SET origin = 'GENERATED', source_commit = 'kept'
				WHERE concept_key = 'seat-hold-conditional-update'
				""");

		loader.run(null);

		assertThat(count("SELECT count(*) FROM question_bank WHERE source_commit = 'kept'")).isEqualTo(1);
	}

	@Test
	void should_use_known_topics_and_unique_concepts() {
		List<String> topics = jdbc.queryForList("SELECT DISTINCT topic FROM question_bank", String.class);
		assertThat(TOPICS).containsAll(topics);
		assertThat(count("SELECT count(DISTINCT concept_key) FROM question_bank")).isEqualTo(SEED_COUNT);
	}

	@Test
	void should_store_points_and_source_for_every_question() {
		assertThat(count("""
				SELECT count(*) FROM question_bank
				WHERE jsonb_array_length(reference_points_json -> 'expectedPoints') BETWEEN 2 AND 5
				  AND jsonb_typeof(reference_points_json -> 'commonMistakes') = 'array'
				  AND source_ref <> '' AND source_commit <> ''
				""")).isEqualTo(SEED_COUNT);
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

}
