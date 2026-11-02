package io.github.gumwoo.aiinterview.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record ApiTokenProperties(@NotBlank @Size(min = 32) String apiToken) {

	@Override
	public String toString() {
		return "ApiTokenProperties[apiToken=***]";
	}

}
