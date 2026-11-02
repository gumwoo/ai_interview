package io.github.gumwoo.aiinterview.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import io.github.gumwoo.aiinterview.application.llm.LlmException;
import io.github.gumwoo.aiinterview.domain.usage.ApiUsageLog;
import io.github.gumwoo.aiinterview.infrastructure.claude.ClaudeProperties;
import io.github.gumwoo.aiinterview.infrastructure.persistence.ApiUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.claude.api-key=test-key" })
@Import(TestcontainersConfiguration.class)
class ApiUsageServiceTest {

	private static final String MODEL = "claude-haiku-5-5";

	/** 한국 시간 2026-11-03 00:10 */
	private static final Instant NOW = Instant.parse("2026-11-02T15:10:00Z");

	@Autowired
	private ApiUsageLogRepository repository;

	private ApiUsageService service;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		ClaudeProperties properties = new ClaudeProperties("key", null, MODEL, "medium", Duration.ofSeconds(20), 2, 4000,
				1000, 500, Map.of(MODEL, new ClaudeProperties.ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50"))));
		service = new ApiUsageService(repository, properties, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
	}

	@Test
	void should_record_tokens_and_estimated_cost() {
		service.record(null, 1000, 2000);

		ApiUsageLog log = repository.findAll().get(0);
		assertThat(log.getModelId()).isEqualTo(MODEL);
		assertThat(log.getInputTokens()).isEqualTo(1000);
		assertThat(log.getOutputTokens()).isEqualTo(2000);
		assertThat(log.getEstimatedCostUsd()).isEqualByComparingTo("0.001100");
	}

	@Test
	void should_allow_call_below_limit() {
		service.record(null, 999, 499);

		assertThatCode(service::ensureWithinDailyLimit).doesNotThrowAnyException();
	}

	@Test
	void should_block_call_when_input_limit_reached() {
		service.record(null, 1000, 0);

		assertThatThrownBy(service::ensureWithinDailyLimit).isInstanceOf(LlmException.QuotaExceeded.class);
	}

	@Test
	void should_block_call_when_output_limit_reached() {
		service.record(null, 0, 500);

		assertThatThrownBy(service::ensureWithinDailyLimit).isInstanceOf(LlmException.QuotaExceeded.class);
	}

	@Test
	void should_not_count_usage_before_midnight_in_app_zone() {
		repository.save(new ApiUsageLog(null, MODEL, 5000, 5000, BigDecimal.ONE, NOW.minus(Duration.ofMinutes(11))));

		assertThatCode(service::ensureWithinDailyLimit).doesNotThrowAnyException();
	}

	@Test
	void should_count_usage_after_midnight_in_app_zone() {
		repository.save(new ApiUsageLog(null, MODEL, 5000, 5000, BigDecimal.ONE, NOW.minus(Duration.ofMinutes(9))));

		assertThatThrownBy(service::ensureWithinDailyLimit).isInstanceOf(LlmException.QuotaExceeded.class);
	}

}
