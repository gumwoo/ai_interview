package io.github.gumwoo.aiinterview.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gumwoo.aiinterview.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class DevConsoleTest {

	@Nested
	@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
			"app.claude.api-key=test-key" })
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	class DefaultProfile {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void should_not_expose_console() throws Exception {
			mockMvc.perform(get("/dev/console")).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	@SpringBootTest(properties = { "app.api-token=" + TestcontainersConfiguration.TEST_API_TOKEN,
			"app.claude.api-key=test-key" })
	@AutoConfigureMockMvc
	@ActiveProfiles("dev")
	@Import(TestcontainersConfiguration.class)
	class DevProfile {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void should_serve_console() throws Exception {
			mockMvc.perform(get("/dev/console"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
		}

		@Test
		void should_still_require_token_for_api() throws Exception {
			mockMvc.perform(get("/api/v1/sessions/1")).andExpect(status().isUnauthorized());
		}

	}

}
