package io.github.gumwoo.aiinterview.api;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);
	private static final int DB_CHECK_TIMEOUT_SECONDS = 2;

	private final DataSource dataSource;

	public HealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		if (isDatabaseAvailable()) {
			return ResponseEntity.ok(new HealthResponse("UP"));
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new HealthResponse("DOWN"));
	}

	private boolean isDatabaseAvailable() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(DB_CHECK_TIMEOUT_SECONDS);
		}
		catch (SQLException ex) {
			log.warn("DB 연결 확인 실패", ex);
			return false;
		}
	}

	public record HealthResponse(String status) {
	}

}
