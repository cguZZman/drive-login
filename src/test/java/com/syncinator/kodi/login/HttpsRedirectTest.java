package com.syncinator.kodi.login;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind Heroku's router the original scheme arrives in X-Forwarded-Proto. The tests connect from 127.0.0.1,
 * a trusted proxy address, so they can send that header the way the router does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "security.require-https=true")
class HttpsRedirectTest extends IntegrationTest {

	@Test
	void plainHttpPagesRedirectToHttps() throws Exception {
		final HttpResponse<String> response = get("/privacypolicy?x=1", "X-Forwarded-Proto", "http");
		assertThat(response.statusCode()).isEqualTo(301);
		assertThat(location(response)).isEqualTo("https://localhost/privacypolicy?x=1");
	}

	@Test
	void plainHttpPostsKeepTheirMethod() throws Exception {
		final HttpResponse<String> response = postForm("/pin", Map.of("provider", "googledrive"), "X-Forwarded-Proto", "http");
		assertThat(response.statusCode()).isEqualTo(308);
		assertThat(location(response)).startsWith("https://").endsWith("/pin");
	}

	@Test
	void httpsIsServed() throws Exception {
		assertThat(get("/privacypolicy", "X-Forwarded-Proto", "https").statusCode()).isEqualTo(200);
	}
}
