package com.syncinator.kodi.login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"callback.url=https://drive-login.example.com/callback",
		"security.require-https=false",
		"funding.url=https://example.org/donate",
		"funding.goal=1500",
		"funding.raised=420"})
class FundingCardTest {
	@Value("${local.server.port}")
	private int port;

	@Test
	void successPageShowsFundingCardWithProgress() throws Exception {
		final String page = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth-success")).build(),
				HttpResponse.BodyHandlers.ofString()).body();
		assertThat(page)
				.contains("class=\"funding\"")
				.contains("href=\"https://example.org/donate\"")
				.contains("value=\"28\"")
				.contains("$420 of $1,500 raised this year");
	}
}
