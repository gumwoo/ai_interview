package io.github.gumwoo.aiinterview.domain.usage;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_usage_log")
public class ApiUsageLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id")
	private Long sessionId;

	@Column(name = "model_id", nullable = false)
	private String modelId;

	@Column(name = "input_tokens", nullable = false)
	private int inputTokens;

	@Column(name = "output_tokens", nullable = false)
	private int outputTokens;

	@Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
	private BigDecimal estimatedCostUsd;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ApiUsageLog() {
	}

	public ApiUsageLog(Long sessionId, String modelId, int inputTokens, int outputTokens, BigDecimal estimatedCostUsd,
			Instant createdAt) {
		this.sessionId = sessionId;
		this.modelId = modelId;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.estimatedCostUsd = estimatedCostUsd;
		this.createdAt = createdAt;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public String getModelId() {
		return modelId;
	}

	public int getInputTokens() {
		return inputTokens;
	}

	public int getOutputTokens() {
		return outputTokens;
	}

	public BigDecimal getEstimatedCostUsd() {
		return estimatedCostUsd;
	}

}
