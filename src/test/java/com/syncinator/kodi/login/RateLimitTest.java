package com.syncinator.kodi.login;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Low limits so they are easy to hit. Each test uses its own client address (the last X-Forwarded-For
 * entry, as appended by Heroku's router) so the counters of one test don't affect another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rate-limit.pin=2", "rate-limit.signin=2", "rate-limit.refresh.ip=3", "rate-limit.refresh.token=2"})
class RateLimitTest extends IntegrationTest {

	@Test
	void pinCreationIsLimitedPerClient() throws Exception {
		final String[] client = forwardedFor("203.0.113.1");
		assertThat(postForm("/pin", Map.of("provider", "googledrive"), client).statusCode()).isEqualTo(200);
		assertThat(postForm("/pin", Map.of("provider", "googledrive"), client).statusCode()).isEqualTo(200);
		final HttpResponse<String> limited = postForm("/pin", Map.of("provider", "googledrive"), client);
		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.headers().firstValue("Retry-After")).hasValue("60");
		assertThat(limited.headers().firstValue("Content-Security-Policy")).as("security headers on 429").isPresent();
		assertThat(postForm("/pin", Map.of("provider", "googledrive"), forwardedFor("203.0.113.2")).statusCode())
				.as("other clients are not affected").isEqualTo(200);
	}

	@Test
	void forgedForwardedForEntriesDoNotEscapeTheLimit() throws Exception {
		// Only the last entry counts: it is the one Heroku's router appends; earlier ones come from the client.
		assertThat(postForm("/pin", Map.of("provider", "onedrive"), forwardedFor("1.1.1.1, 203.0.113.3")).statusCode()).isEqualTo(200);
		assertThat(postForm("/pin", Map.of("provider", "onedrive"), forwardedFor("2.2.2.2, 203.0.113.3")).statusCode()).isEqualTo(200);
		assertThat(postForm("/pin", Map.of("provider", "onedrive"), forwardedFor("3.3.3.3, 203.0.113.3")).statusCode()).isEqualTo(429);
		assertThat(get("/ip", forwardedFor("9.9.9.9, 203.0.113.3")).body()).isEqualTo("203.0.113.3");
	}

	@Test
	void signInPagesAreLimitedWithAFriendlyPage() throws Exception {
		final String[] client = forwardedFor("203.0.113.4");
		assertThat(get("/authorize?pin=AAAAAA", client).statusCode()).isEqualTo(200);
		assertThat(get("/signin/AAAAAA", client).statusCode()).isEqualTo(200);
		final HttpResponse<String> limited = get("/authorize?pin=AAAAAA", "X-Forwarded-For", "203.0.113.4", "Accept", "text/html");
		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.body()).contains("Too many attempts");
	}

	@Test
	void refreshIsLimitedPerToken() throws Exception {
		assertThat(refresh("same-token", "203.0.113.5").statusCode()).isEqualTo(200);
		assertThat(refresh("same-token", "203.0.113.6").statusCode()).isEqualTo(200);
		assertThat(refresh("same-token", "203.0.113.7").statusCode()).as("same token from another IP").isEqualTo(429);
	}

	@Test
	void refreshIsLimitedPerClient() throws Exception {
		assertThat(refresh("token-a", "203.0.113.8").statusCode()).isEqualTo(200);
		assertThat(refresh("token-b", "203.0.113.8").statusCode()).isEqualTo(200);
		assertThat(refresh("token-c", "203.0.113.8").statusCode()).isEqualTo(200);
		assertThat(refresh("token-d", "203.0.113.8").statusCode()).isEqualTo(429);
	}

	private HttpResponse<String> refresh(final String token, final String ip) throws Exception {
		return postForm("/refresh", Map.of("provider", "googledrive", "refresh_token", token), forwardedFor(ip));
	}

	private static String[] forwardedFor(final String value) {
		return new String[] {"X-Forwarded-For", value};
	}
}
