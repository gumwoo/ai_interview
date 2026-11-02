package io.github.gumwoo.aiinterview.infrastructure.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ClaudePropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class)
		.withPropertyValues("app.claude.model=claude-haiku-5-5", "app.claude.effort=medium", "app.claude.timeout=20s",
				"app.claude.max-retries=2", "app.claude.max-output-tokens=4000",
				"app.claude.daily-input-token-limit=1000", "app.claude.daily-output-token-limit=1000",
				"app.claude.pricing.claude-haiku-5-5.input-per-million=0.10",
				"app.claude.pricing.claude-haiku-5-5.output-per-million=0.50");

	@Test
	void should_start_with_complete_settings() {
		runner.withPropertyValues("app.claude.api-key=key").run((context) -> assertThat(context).hasNotFailed());
	}

	@Test
	void should_fail_startup_when_api_key_blank() {
		runner.withPropertyValues("app.claude.api-key=").run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void should_fail_startup_when_model_has_no_price() {
		runner.withPropertyValues("app.claude.api-key=key", "app.claude.model=claude-unknown")
			.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void should_hide_api_key_in_to_string() {
		runner.withPropertyValues("app.claude.api-key=secret-value")
			.run((context) -> assertThat(context.getBean(ClaudeProperties.class)).asString()
				.doesNotContain("secret-value"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ClaudeProperties.class)
	static class PropertiesConfiguration {

	}

}
