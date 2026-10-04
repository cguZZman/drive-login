package com.syncinator.kodi.login.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * The client's public IP address, used to pair the browser with the Kodi device and as the rate-limit key.
 * Comes from getRemoteAddr(): with server.forward-headers-strategy=native, Tomcat takes it from X-Forwarded-For,
 * trusting only entries added by proxies on private networks (Heroku's router), and leaves just the
 * client-sent, forgeable entries in the header. Never read X-Forwarded-For directly.
 */
@Component
public class ClientAddress {

	public String of(final HttpServletRequest request) {
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
