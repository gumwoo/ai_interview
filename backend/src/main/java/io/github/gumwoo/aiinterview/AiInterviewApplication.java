package io.github.gumwoo.aiinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 인증은 API 토큰으로만 하므로, 임시 비밀번호를 만들어 로그에 남기는 기본 사용자를 끈다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AiInterviewApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiInterviewApplication.class, args);
	}

}
