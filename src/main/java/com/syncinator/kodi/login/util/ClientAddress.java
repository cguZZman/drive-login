package com.syncinator.kodi.login.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * The client's public IP address, used to pair the browser with the Kodi device and as the rate-limit key.
 * Behind Heroku's router the real address is the LAST X-Forwarded-For entry: the router adds it, and
 * anything before it was sent by the client and can be forged. When the client sends its own header, the
 * router adds its value as a separate header line, so all lines are read, not just the first.
 */
@Component
public class ClientAddress {
	private final String forwardedHeader;

	public ClientAddress(@Value("${client-ip.forwarded-header}") final String forwardedHeader) {
		this.forwardedHeader = forwardedHeader;
	}

	public String of(final HttpServletRequest request) {
		if (StringUtils.hasText(forwardedHeader)) {
			final List<String> lines = Collections.list(request.getHeaders(forwardedHeader));
			final String[] hops = String.join(",", lines).split(",");
			for (int i = hops.length - 1; i >= 0; i--) {
				if (StringUtils.hasText(hops[i])) {
					return hops[i].trim();
				}
			}
		}
		return request.getRemoteAddr();
	}

	/** Sum of the address parts, shown to users so they can compare networks between Kodi and the browser. */
	public String sourceId(final HttpServletRequest request) {
		final String ip = of(request);
		try {
			return String.valueOf(Stream.of(ip.contains(".") ? ip.split("\\.") : ip.split(":"))
					.mapToInt(Integer::parseInt).sum());
		} catch (final Exception e) {
			return "-1";
		}
	}
}
