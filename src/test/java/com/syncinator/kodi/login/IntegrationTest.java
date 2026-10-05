package com.syncinator.kodi.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared setup for tests that run the server on a random port against {@link MockTokenServer}. */
abstract class IntegrationTest {
	static final MockTokenServer TOKENS = new MockTokenServer();
	static final String CALLBACK = "https://drive-login.example.com/callback";

	@DynamicPropertySource
	static void providers(final DynamicPropertyRegistry registry) {
		for (final String provider : List.of("googledrive", "onedrive")) {
			final String prefix = "PROVIDER_" + provider.toUpperCase();
			registry.add(prefix + "_CLIENT_ID", () -> provider + "-client");
			registry.add(prefix + "_CLIENT_SECRET", () -> provider + "-secret");
			registry.add(prefix + "_URL_AUTHORIZE", () -> "https://login.example.com/" + provider + "/authorize");
			registry.add(prefix + "_URL_TOKEN", TOKENS::tokenUrl);
		}
		registry.add("callback.url", () -> CALLBACK);
	}

	@Value("${local.server.port}")
	private int port;

	// Never follows redirects, so the tests see the 302s.
	private final HttpClient http = HttpClient.newHttpClient();

	HttpResponse<String> get(final String path, final String... headers) throws IOException, InterruptedException {
		final HttpRequest.Builder request = HttpRequest.newBuilder(url(path)).GET();
		if (headers.length > 0) {
			request.headers(headers);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	HttpResponse<String> postForm(final String path, final Map<String, String> form, final String... headers) throws IOException, InterruptedException {
		final String body = form.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		final HttpRequest.Builder request = HttpRequest.newBuilder(url(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (headers.length > 0) {
			request.headers(headers);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI url(final String path) {
		return URI.create("http://localhost:" + port + path);
	}

	HttpResponse<String> getFromHost(final String host, final String path) throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
	}

	static String[] basic(final String password) {
		return new String[] {"Authorization", "Basic " + Base64.getEncoder().encodeToString((":" + password).getBytes(StandardCharsets.UTF_8))};
	}

	static String location(final HttpResponse<String> response) {
		return response.headers().firstValue("Location").orElseThrow();
	}

	static Map<String, Object> json(final HttpResponse<String> response) {
		return JsonParserFactory.getJsonParser().parseMap(response.body());
	}

	static String decode(final String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
