package io.github.gumwoo.aiinterview.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

	@Mock
	private DataSource dataSource;

	@Mock
	private Connection connection;

	@Test
	void should_return_up_when_database_available() throws Exception {
		given(dataSource.getConnection()).willReturn(connection);
		given(connection.isValid(2)).willReturn(true);

		ResponseEntity<HealthController.HealthResponse> response = new HealthController(dataSource).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo("UP");
	}

	@Test
	void should_return_503_when_database_unavailable() throws Exception {
		given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));

		ResponseEntity<HealthController.HealthResponse> response = new HealthController(dataSource).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().status()).isEqualTo("DOWN");
	}

}
