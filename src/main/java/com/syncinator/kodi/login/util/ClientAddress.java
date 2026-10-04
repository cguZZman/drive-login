package com.syncinator.kodi.login.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Stream;

/**
 * The client's public IP address, used to pair the browser with the Kodi device and as the rate-limit key.
 * Behind Heroku's router the real address is the LAST X-Forwarded-For entry: the router appends it, and
 * anything before it was sent by the client and can be forged.
 */
@Component
public class ClientAddress {
	private final String forwardedHeader;

	public ClientAddress(@Value("${client-ip.forwarded-header}") final String forwardedHeader) {
		this.forwardedHeader = forwardedHeader;
	}

	public String of(final HttpServletRequest request) {
		if (StringUtils.hasText(forwardedHeader)) {
			final String forwarded = request.getHeader(forwardedHeader);
			if (StringUtils.hasText(forwarded)) {
				final String[] hops = forwarded.split(",");
				return hops[hops.length - 1].trim();
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
