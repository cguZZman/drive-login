package com.syncinator.kodi.login.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syncinator.kodi.login.util.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-minute request limits on the endpoints that create state or use the client secrets:
 * POST /pin, the sign-in redirect (/authorize, /signin/*) and POST /refresh (per IP and per refresh token).
 * Counters live only in memory; refresh tokens are counted by their SHA-256 hash, never stored as is.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
	static final Duration WINDOW = Duration.ofMinutes(1);

	// Fixed windows: an entry expires one window after its first request; incrementing does not extend it.
	private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
			.expireAfterWrite(WINDOW)
			.maximumSize(200_000)
			.build();
	private final ClientAddress clientAddress;
	private final int pinLimit;
	private final int signinLimit;
	private final int refreshIpLimit;
	private final int refreshTokenLimit;

	public RateLimitFilter(
			final ClientAddress clientAddress,
			@Value("${rate-limit.pin}") final int pinLimit,
			@Value("${rate-limit.signin}") final int signinLimit,
			@Value("${rate-limit.refresh.ip}") final int refreshIpLimit,
			@Value("${rate-limit.refresh.token}") final int refreshTokenLimit) {
		this.clientAddress = clientAddress;
		this.pinLimit = pinLimit;
		this.signinLimit = signinLimit;
		this.refreshIpLimit = refreshIpLimit;
		this.refreshTokenLimit = refreshTokenLimit;
	}

	@Override
	protected void doFilterInternal(
			final HttpServletRequest request,
			final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
		final String limited = exceededLimit(request);
		if (limited != null) {
			log.warn("Rate limit exceeded: {}", limited);
			response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
			response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests");
			return;
		}
		chain.doFilter(request, response);
	}

	/** The name of the exceeded limit, or null when the request may proceed. */
	private String exceededLimit(final HttpServletRequest request) {
		final String path = request.getRequestURI();
		final boolean post = "POST".equalsIgnoreCase(request.getMethod());
		if (post && path.equals("/pin")) {
			return acquire("pin:" + clientAddress.of(request), pinLimit) ? null : "pin";
		}
		if (path.equals("/authorize") || path.startsWith("/signin/")) {
			return acquire("signin:" + clientAddress.of(request), signinLimit) ? null : "signin";
		}
		if (post && path.equals("/refresh")) {
			if (!acquire("refresh-ip:" + clientAddress.of(request), refreshIpLimit)) {
				return "refresh per IP";
			}
			final String token = request.getParameter("refresh_token");
			if (StringUtils.hasText(token) && !acquire("refresh-token:" + sha256(token), refreshTokenLimit)) {
				return "refresh per token";
			}
		}
		return null;
	}

	private boolean acquire(final String key, final int limit) {
		return limit <= 0 || counters.get(key, k -> new AtomicInteger()).incrementAndGet() <= limit;
	}

	private static String sha256(final String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
