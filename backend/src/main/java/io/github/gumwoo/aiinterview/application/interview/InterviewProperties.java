package io.github.gumwoo.aiinterview.application.interview;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.interview")
public record InterviewProperties(@Min(1) int maxSessionTurns,
		@Min(0) int maxFollowUpDepth) {
}
