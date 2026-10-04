package com.syncinator.kodi.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the sign-in server over real HTTP: pin creation, the redirect to the provider,
 * the callback, token pickup by Kodi and token refresh, for every provider, against a mock token endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class SignInFlowTest {
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

	@ParameterizedTest
	@CsvSource({
			"googledrive, https://www.googleapis.com/auth/drive.readonly, GET",
			"onedrive,    files.read.all,                                 POST"})
	void signInFlow(final String provider, final String expectedScope, final String callbackMethod) throws Exception {
		final Map<String, Object> pin = json(postForm("/pin", Map.of("provider", provider)));
		final String code = (String) pin.get("pin");
		final String password = (String) pin.get("password");
		assertThat(code).matches("[0-9A-F]{6}");
		assertThat(password).hasSizeGreaterThan(100);

		assertThat(get("/pin/" + code, basic(password)).statusCode()).as("poll before sign-in").isEqualTo(202);
		assertThat(get("/pin/" + code, basic("wrong")).statusCode()).as("poll with wrong password").isEqualTo(404);

		final HttpResponse<String> signin = get("/signin/" + code);
		assertThat(signin.statusCode()).isEqualTo(302);
		final URI authorize = URI.create(location(signin));
		assertThat(authorize.getHost() + authorize.getPath()).isEqualTo("login.example.com/" + provider + "/authorize");
		final Map<String, String> query = UriComponentsBuilder.fromUri(authorize).build().getQueryParams().toSingleValueMap()
				.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> decode(e.getValue())));
		assertThat(query)
				.containsEntry("client_id", provider + "-client")
				.containsEntry("redirect_uri", CALLBACK)
				.containsEntry("response_type", "code")
				.containsEntry("state", code);
		assertThat(query.get("scope")).contains(expectedScope);
		if (provider.equals("onedrive")) {
			assertThat(query).containsEntry("response_mode", "form_post");
		} else {
			assertThat(query).containsEntry("access_type", "offline").containsEntry("prompt", "consent");
			assertThat(query.get("scope")).as("Photos access was removed (Photos API AUP-3)").doesNotContain("photos");
		}

		final HttpResponse<String> callback = callbackMethod.equals("POST")
				? postForm("/callback", Map.of("code", "the-code", "state", code))
				: get("/callback?code=the-code&state=" + code);
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(location(callback)).endsWith("auth-success");
		assertThat(TOKENS.lastForm())
				.containsEntry("grant_type", "authorization_code")
				.containsEntry("code", "the-code")
				.containsEntry("client_id", provider + "-client")
				.containsEntry("client_secret", provider + "-secret")
				.containsEntry("redirect_uri", CALLBACK);

		final HttpResponse<String> tokens = get("/pin/" + code, basic(password));
		assertThat(tokens.statusCode()).isEqualTo(200);
		assertThat(json(tokens)).containsEntry("access_token", "AT").containsEntry("refresh_token", "RT");
		assertThat(get("/pin/" + code, basic(password)).statusCode()).as("pin is single use").isEqualTo(404);

		final HttpResponse<String> refresh = postForm("/refresh", Map.of("provider", provider, "refresh_token", "RT"));
		assertThat(refresh.statusCode()).isEqualTo(200);
		assertThat(json(refresh)).containsEntry("access_token", "AT");
		assertThat(TOKENS.lastForm())
				.containsEntry("grant_type", "refresh_token")
				.containsEntry("refresh_token", "RT")
				.containsEntry("client_secret", provider + "-secret");
	}

	@Test
	void unknownProviderIsRejected() throws Exception {
		assertThat(postForm("/pin", Map.of("provider", "dropbox")).statusCode()).isEqualTo(400);
	}

	@Test
	void refreshErrorsAreReportedAndLoggedWithoutTheToken(final CapturedOutput output) throws Exception {
		assertThat(postForm("/refresh", Map.of("provider", "googledrive", "refresh_token", "bad-secret-refresh-token")).statusCode()).isEqualTo(400);
		assertThat(postForm("/refresh", Map.of()).statusCode()).isEqualTo(400);
		assertThat(output.getOut())
				.contains("Token request failed: provider=googledrive grant_type=refresh_token status=400 error=invalid_grant description=Token has been expired or revoked.")
				.doesNotContain("bad-secret-refresh-token");
	}

	@Test
	void wrongCodeShowsErrorAndKeepsTheCode() throws Exception {
		final String page = get("/authorize?pin=ABCDEO").body();
		assertThat(page).contains("That code didn").contains("aria-invalid=\"true\"");
		assertThat(page).as("letter O is read as zero").contains("value=\"ABCDE0\"");
	}

	@Test
	void callbackErrorsAreExplained() throws Exception {
		assertThat(get("/callback?error=access_denied&error_description=nope").body()).contains("Access was not granted");
		assertThat(get("/callback?error=server_error&error_description=nope").body()).contains("server_error: nope");
		assertThat(get("/callback?code=x&state=ffffff").body()).contains("no longer valid");
		assertThat(get("/callback?code=x").body()).contains("request was invalid");
	}

	@Test
	void pagesRender() throws Exception {
		final String index = get("/").body();
		assertThat(index).contains("Connect Kodi to your cloud drive");
		assertThat(index).as("Google site verification").contains("LXJecqW73UwDJ2KkfDMtuKTNM8zejv21CRvNbBbtC_4");
		final HttpResponse<String> success = get("/auth-success");
		assertThat(success.statusCode()).isEqualTo(200);
		assertThat(success.body()).contains("signed in").doesNotContain("class=\"funding\"");
		assertThat(get("/privacypolicy").body()).contains("Limited Use");
	}

	@Test
	void staticAssets() throws Exception {
		assertThat(get("/css/app.css").statusCode()).isEqualTo(200);
		assertThat(get("/js/app.js").statusCode()).isEqualTo(200);
		assertThat(get("/js/jquery-2.1.3.min.js").statusCode()).isEqualTo(404);
	}

	@Test
	void securityHeaders() throws Exception {
		final HttpResponse<String> response = get("/");
		assertThat(response.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp)
				.contains("default-src 'self'", "script-src 'self'", "style-src 'self'", "frame-ancestors 'none'"));
		assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
		assertThat(response.headers().firstValue("Strict-Transport-Security")).isPresent();
	}

	@Test
	void pagesHaveNoInlineScriptsOrStyles() throws Exception {
		// The CSP blocks inline code, so any of these would silently break in the browser.
		for (final String path : List.of("/", "/authorize?pin=ABCDEF", "/auth-success", "/callback?code=x", "/privacypolicy")) {
			assertThat(get(path).body()).as(path)
					.doesNotContainPattern("<script(?![^>]*\\ssrc=)")
					.doesNotContainPattern("<style|\\sstyle=|\\son[a-z]+=");
		}
	}

	private HttpResponse<String> get(final String path, final String... headers) throws IOException, InterruptedException {
		final HttpRequest.Builder request = HttpRequest.newBuilder(url(path)).GET();
		if (headers.length > 0) {
			request.headers(headers);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postForm(final String path, final Map<String, String> form) throws IOException, InterruptedException {
		final String body = form.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		return http.send(HttpRequest.newBuilder(url(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI url(final String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String[] basic(final String password) {
		return new String[] {"Authorization", "Basic " + Base64.getEncoder().encodeToString((":" + password).getBytes(StandardCharsets.UTF_8))};
	}

	private static String location(final HttpResponse<String> response) {
		return response.headers().firstValue("Location").orElseThrow();
	}

	private static Map<String, Object> json(final HttpResponse<String> response) {
		return JsonParserFactory.getJsonParser().parseMap(response.body());
	}

	private static String decode(final String value) {
		return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
