package io.github.gumwoo.aiinterview.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 음성 앱 없이 텍스트로 면접을 돌려 보는 화면. {@code dev} 프로필에서만 켜진다.
 * 화면 자체에는 비밀값이 없고, API 호출에는 화면에 입력한 토큰을 쓴다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevConsoleConfig {

	private static final String CONSOLE_PATH = "/dev/console";

	@Bean
	@Order(0)
	SecurityFilterChain devConsoleSecurity(HttpSecurity http) throws Exception {
		return http.securityMatcher(CONSOLE_PATH)
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.build();
	}

	@Controller
	@Profile("dev")
	static class DevConsoleController {

		@GetMapping(value = CONSOLE_PATH, produces = MediaType.TEXT_HTML_VALUE)
		@ResponseBody
		Resource console() {
			return new ClassPathResource("dev/console.html");
		}

	}

}
