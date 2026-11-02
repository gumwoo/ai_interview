package io.github.gumwoo.aiinterview.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 고정 API 토큰을 확인한다. 토큰이 맞지 않으면 인증 정보를 남기지 않아 인가 단계에서 401로 거부된다.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String OWNER = "owner";

	private final byte[] expectedToken;

	public ApiTokenFilter(String expectedToken) {
		this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (hasValidToken(request.getHeader(HttpHeaders.AUTHORIZATION))) {
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(OWNER, null,
						AuthorityUtils.NO_AUTHORITIES));
		}
		chain.doFilter(request, response);
	}

	private boolean hasValidToken(String header) {
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return false;
		}
		byte[] token = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedToken, token);
	}

}
