package io.github.gumwoo.aiinterview.application.usage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import io.github.gumwoo.aiinterview.application.llm.LlmException;
import io.github.gumwoo.aiinterview.domain.usage.ApiUsageLog;
import io.github.gumwoo.aiinterview.infrastructure.claude.ClaudeProperties;
import io.github.gumwoo.aiinterview.infrastructure.persistence.ApiUsageLogRepository;
import io.github.gumwoo.aiinterview.infrastructure.persistence.ApiUsageLogRepository.TokenTotals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApiUsageService {

	private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
	private static final int COST_SCALE = 6;

	private final ApiUsageLogRepository repository;
	private final ClaudeProperties properties;
	private final Clock clock;

	public ApiUsageService(ApiUsageLogRepository repository, ClaudeProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	/** 오늘 사용량이 상한에 닿았으면 호출 전에 막는다. 날짜 경계는 앱 시간대 자정이다. */
	public void ensureWithinDailyLimit() {
		Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
		TokenTotals totals = repository.sumTokensSince(startOfDay);
		if (totals.inputTokens() >= properties.dailyInputTokenLimit()
				|| totals.outputTokens() >= properties.dailyOutputTokenLimit()) {
			throw new LlmException.QuotaExceeded("오늘의 Claude 사용량 상한에 도달했습니다");
		}
	}

	@Transactional
	public void record(Long sessionId, long inputTokens, long outputTokens) {
		repository.save(new ApiUsageLog(sessionId, properties.model(), Math.toIntExact(inputTokens),
				Math.toIntExact(outputTokens), estimateCost(inputTokens, outputTokens), clock.instant()));
	}

	private BigDecimal estimateCost(long inputTokens, long outputTokens) {
		ClaudeProperties.ModelPrice price = properties.currentPrice();
		BigDecimal input = price.inputPerMillion().multiply(BigDecimal.valueOf(inputTokens));
		BigDecimal output = price.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens));
		return input.add(output).divide(ONE_MILLION, MathContext.DECIMAL64).setScale(COST_SCALE, RoundingMode.HALF_UP);
	}

}
