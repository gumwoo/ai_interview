package io.github.gumwoo.aiinterview.infrastructure.claude;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.claude")
public record ClaudeProperties(@NotBlank String apiKey, String baseUrl, @NotBlank String model,
		@NotBlank String effort, @NotNull Duration timeout, @Min(0) int maxRetries, @Min(1) long maxOutputTokens,
		@Min(1) long dailyInputTokenLimit, @Min(1) long dailyOutputTokenLimit,
		@NotEmpty Map<String, @Valid ModelPrice> pricing) {

	public ModelPrice currentPrice() {
		return pricing.get(model);
	}

	/** 가격을 모르는 모델로 바꾸면 사용량 추산이 틀어지므로 기동을 막는다. */
	@AssertTrue(message = "app.claude.pricing에 현재 모델의 가격이 있어야 합니다")
	public boolean isCurrentModelPriced() {
		return pricing == null || model == null || pricing.containsKey(model);
	}

	@Override
	public String toString() {
		return "ClaudeProperties[model=" + model + ", effort=" + effort + ", apiKey=***]";
	}

	public record ModelPrice(@NotNull BigDecimal inputPerMillion, @NotNull BigDecimal outputPerMillion) {
	}

}
