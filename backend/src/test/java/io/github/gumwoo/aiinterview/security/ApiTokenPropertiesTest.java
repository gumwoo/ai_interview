package io.github.gumwoo.aiinterview.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApiTokenPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class);

	@Test
	void should_fail_startup_when_token_missing() {
		runner.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void should_fail_startup_when_token_too_short() {
		runner.withPropertyValues("app.api-token=short").run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void should_start_when_token_long_enough() {
		runner.withPropertyValues("app.api-token=0123456789-0123456789-0123456789")
			.run((context) -> assertThat(context).hasNotFailed());
	}

	@Test
	void should_hide_token_in_to_string() {
		assertThat(new ApiTokenProperties("secret-value")).asString().doesNotContain("secret-value");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ApiTokenProperties.class)
	static class PropertiesConfiguration {

	}

}
