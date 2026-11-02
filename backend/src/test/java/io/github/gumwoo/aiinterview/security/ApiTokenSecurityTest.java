package io.github.gumwoo.aiinterview.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
		"app.claude.api-key=test-key" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiTokenSecurityTest {

	private static final String PROTECTED_PATH = "/api/v1/sessions/1";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void should_allow_health_without_token() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void should_return_401_when_token_missing() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer wrong-token", "Bearer ", "Basic abc",
			TestcontainersConfiguration.TEST_API_TOKEN })
	void should_return_401_when_authorization_header_invalid(String header) throws Exception {
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, header))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void should_pass_authentication_when_token_valid() throws Exception {
		mockMvc
			.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION,
					"Bearer " + TestcontainersConfiguration.TEST_API_TOKEN))
			.andExpect(status().isNotFound());
	}

}
