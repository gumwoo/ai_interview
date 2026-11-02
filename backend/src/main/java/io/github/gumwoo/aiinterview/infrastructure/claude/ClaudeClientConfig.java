package io.github.gumwoo.aiinterview.infrastructure.claude;

import java.time.Clock;
import java.time.ZoneId;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClaudeClientConfig {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

	@Bean(destroyMethod = "close")
	AnthropicClient anthropicClient(ClaudeProperties properties) {
		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
			.apiKey(properties.apiKey())
			.timeout(properties.timeout())
			.maxRetries(properties.maxRetries());
		if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
			builder.baseUrl(properties.baseUrl());
		}
		return builder.build();
	}

	@Bean
	Clock clock() {
		return Clock.system(APP_ZONE);
	}

}
