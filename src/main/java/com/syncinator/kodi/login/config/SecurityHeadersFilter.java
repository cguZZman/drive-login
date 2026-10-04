package com.syncinator.kodi.login.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Runs first so the headers are also on responses other filters end early (e.g. 429).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {
	private final boolean requireHttps;

	public SecurityHeadersFilter(@Value("${security.require-https}") final boolean requireHttps) {
		this.requireHttps = requireHttps;
	}

	// No inline scripts or styles anywhere. form-action allows https: because
	// /authorize answers the code form with a redirect to the provider's sign-in page.
	private static final String CONTENT_SECURITY_POLICY = String.join("; ",
			"default-src 'self'",
			"script-src 'self'",
			"style-src 'self'",
			"img-src 'self' data:",
			"object-src 'none'",
			"base-uri 'none'",
			"frame-ancestors 'none'",
			"form-action 'self' https:");

	@Override
	protected void doFilterInternal(
			final HttpServletRequest request,
			final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
		// isSecure() reflects the original scheme through the proxy's X-Forwarded-Proto (server.forward-headers-strategy).
		if (requireHttps && !request.isSecure()) {
			final String query = request.getQueryString();
			final boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
			// 308 keeps the method and body for POSTs.
			response.setStatus(read ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
			response.setHeader("Location", "https://" + request.getServerName() + request.getRequestURI() + (query == null ? "" : "?" + query));
			return;
		}
		response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
		response.setHeader("Strict-Transport-Security", "max-age=31536000");
		response.setHeader("X-Content-Type-Options", "nosniff");
		// Keeps the pin in /signin/{pin} out of the Referer sent to providers and donation sites.
		response.setHeader("Referrer-Policy", "no-referrer");
		chain.doFilter(request, response);
	}
}
