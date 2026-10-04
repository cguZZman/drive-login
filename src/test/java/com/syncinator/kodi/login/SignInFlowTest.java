package com.syncinator.kodi.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the sign-in server over real HTTP: pin creation, the redirect to the provider,
 * the callback, token pickup by Kodi and token refresh, for every provider, against a mock token endpoint.
 * Rate limits are off here; RateLimitTest covers them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rate-limit.pin=0", "rate-limit.signin=0", "rate-limit.refresh.ip=0", "rate-limit.refresh.token=0"})
@ExtendWith(OutputCaptureExtension.class)
class SignInFlowTest extends IntegrationTest {
	static final String COOKIE = "__Host-signin";

	@ParameterizedTest
	@CsvSource({
			"googledrive, https://www.googleapis.com/auth/drive.readonly profile,   GET",
			"onedrive,    offline_access sites.read.all files.read.all user.read, POST"})
	void signInFlow(final String provider, final String expectedScope, final String callbackMethod) throws Exception {
		final Map<String, Object> pin = json(postForm("/pin", Map.of("provider", provider)));
		final String code = (String) pin.get("pin");
		final String password = (String) pin.get("password");
		assertThat(code).matches("[0-9A-F]{6}");
		assertThat(password).hasSizeGreaterThan(100);
		assertThat(pin).as("browser secrets are never sent to Kodi").doesNotContainKeys("browserNonce", "codeVerifier");

		assertThat(get("/pin/" + code, basic(password)).statusCode()).as("poll before sign-in").isEqualTo(202);
		assertThat(get("/pin/" + code, basic("wrong")).statusCode()).as("poll with wrong password").isEqualTo(404);

		final HttpResponse<String> signin = get("/signin/" + code);
		assertThat(signin.statusCode()).isEqualTo(302);
		final String setCookie = signin.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(setCookie).startsWith(COOKIE + "=").contains("Path=/", "Max-Age=300", "Secure", "HttpOnly", "SameSite=None");
		final String browser = cookie(setCookie);

		final URI authorize = URI.create(location(signin));
		assertThat(authorize.getHost() + authorize.getPath()).isEqualTo("login.example.com/" + provider + "/authorize");
		final Map<String, String> query = query(authorize);
		assertThat(query)
				.containsEntry("client_id", provider + "-client")
				.containsEntry("redirect_uri", CALLBACK)
				.containsEntry("response_type", "code")
				.containsEntry("state", code)
				.containsEntry("scope", expectedScope)
				.containsEntry("code_challenge_method", "S256");
		if (provider.equals("onedrive")) {
			assertThat(query).containsEntry("response_mode", "form_post");
		} else {
			assertThat(query).containsEntry("access_type", "offline").containsEntry("prompt", "consent");
		}

		final HttpResponse<String> callback = callback(callbackMethod, code, "the-code", browser);
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(location(callback)).endsWith("auth-success");
		assertThat(callback.headers().firstValue("Set-Cookie")).as("cookie cleared").hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
		assertThat(TOKENS.lastForm())
				.containsEntry("grant_type", "authorization_code")
				.containsEntry("code", "the-code")
				.containsEntry("client_id", provider + "-client")
				.containsEntry("client_secret", provider + "-secret")
				.containsEntry("redirect_uri", CALLBACK);
		assertThat(s256(TOKENS.lastForm().get("code_verifier"))).as("PKCE verifier matches the challenge").isEqualTo(query.get("code_challenge"));

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
				.containsEntry("client_secret", provider + "-secret")
				.doesNotContainKey("code_verifier");
	}

	@ParameterizedTest
	@CsvSource({"googledrive, GET", "onedrive, POST"})
	void callbackOnlyWorksInTheBrowserThatStartedTheSignIn(final String provider, final String callbackMethod) throws Exception {
		// The attack: someone creates a code, starts the sign-in in their own browser and sends the provider link to a victim.
		final Map<String, Object> pin = json(postForm("/pin", Map.of("provider", provider)));
		final String code = (String) pin.get("pin");
		final String password = (String) pin.get("password");
		final String attackerBrowser = cookie(get("/signin/" + code).headers().firstValue("Set-Cookie").orElseThrow());

		assertThat(callback(callbackMethod, code, "victim-code", null).body()).contains("different browser");
		assertThat(callback(callbackMethod, code, "victim-code", "forged").body()).contains("different browser");
		assertThat(get("/pin/" + code, basic(password)).statusCode()).as("no tokens stored").isEqualTo(202);

		assertThat(callback(callbackMethod, code, "own-code", attackerBrowser).statusCode()).isEqualTo(302);
		assertThat(callback(callbackMethod, code, "replayed-code", attackerBrowser).body()).as("single use").contains("different browser");
	}

	@Test
	void callbackWithoutStartingTheSignInIsRejected() throws Exception {
		final String code = (String) json(postForm("/pin", Map.of("provider", "googledrive"))).get("pin");
		assertThat(callback("GET", code, "x", "anything").body()).contains("different browser");
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

	private HttpResponse<String> callback(final String method, final String state, final String code, final String browser) throws Exception {
		final String[] headers = browser == null ? new String[0] : new String[] {"Cookie", COOKIE + "=" + browser};
		return method.equals("POST")
				? postForm("/callback", Map.of("code", code, "state", state), headers)
				: get("/callback?code=" + code + "&state=" + state, headers);
	}

	private static String cookie(final String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}

	private static Map<String, String> query(final URI uri) {
		return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap()
				.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> decode(e.getValue())));
	}

	private static String s256(final String verifier) throws Exception {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
	}
}
