package com.syncinator.kodi.login;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for the Google and Microsoft token endpoints. Answers every POST with fake tokens,
 * except a refresh_token starting with "bad", which gets 400 invalid_grant. Remembers the last form it received.
 */
class MockTokenServer {
	private final HttpServer server;
	private volatile Map<String, String> lastForm = Map.of();

	MockTokenServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/token", exchange -> {
			final Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			lastForm = form;
			final boolean bad = form.getOrDefault("refresh_token", "").startsWith("bad");
			final byte[] body = (bad
					? "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"
					: "{\"access_token\":\"AT\",\"refresh_token\":\"RT\",\"expires_in\":3600}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(bad ? 400 : 200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	String tokenUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
	}

	Map<String, String> lastForm() {
		return lastForm;
	}

	private static Map<String, String> parseForm(final String body) {
		final Map<String, String> form = new LinkedHashMap<>();
		for (final String pair : body.split("&")) {
			final String[] kv = pair.split("=", 2);
			form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
		}
		return form;
	}
}
