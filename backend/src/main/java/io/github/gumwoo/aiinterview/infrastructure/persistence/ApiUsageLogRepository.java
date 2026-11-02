package io.github.gumwoo.aiinterview.infrastructure.persistence;

import java.time.Instant;

import io.github.gumwoo.aiinterview.domain.usage.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

	@Query("""
			select new io.github.gumwoo.aiinterview.infrastructure.persistence.ApiUsageLogRepository$TokenTotals(
				coalesce(sum(u.inputTokens), 0L), coalesce(sum(u.outputTokens), 0L))
			from ApiUsageLog u
			where u.createdAt >= :from
			""")
	TokenTotals sumTokensSince(@Param("from") Instant from);

	record TokenTotals(long inputTokens, long outputTokens) {
	}

}
