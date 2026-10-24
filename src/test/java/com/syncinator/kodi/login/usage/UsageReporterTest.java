package com.syncinator.kodi.login.usage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class UsageReporterTest {
	private HttpServer server;
	private final List<String> bodies = new CopyOnWriteArrayList<>();
	private final List<String> authorizations = new CopyOnWriteArrayList<>();
	private final List<String> signatures = new CopyOnWriteArrayList<>();
	private volatile int status = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ingest/usage", exchange -> {
			bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
			signatures.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Usage-Signature")));
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private UsageReporter reporter() {
		return new UsageReporter("http://127.0.0.1:" + server.getAddress().getPort() + "/ingest/usage", "report-token");
	}

	@Test
	void countsByEventAndVersionAndReportsHourly() {
		final UsageReporter usage = reporter();
		usage.count("refresh", "googledrive", "ok", "", "plugin.googledrive 1.5.6/1.4.0");
		usage.count("refresh", "googledrive", "ok", "", "plugin.googledrive 1.5.6/1.4.0");
		usage.count("refresh", "googledrive", "failed", "invalid_grant", "not a version");
		assertThat(usage.pending()).containsExactlyInAnyOrderEntriesOf(Map.of(
				new UsageReporter.Key("refresh", "googledrive", "ok", "", "plugin.googledrive", "1.5.6", "1.4.0"), 2L,
				new UsageReporter.Key("refresh", "googledrive", "failed", "invalid_grant", "", "", ""), 1L));

		usage.report();
		assertThat(authorizations).as("the token is never sent").containsExactly("null");
		assertThat(bodies).hasSize(1);
		final String[] parts = signatures.getFirst().split("[=,]");
		assertThat(parts[0]).isEqualTo("t");
		assertThat(Long.parseLong(parts[1])).isCloseTo(java.time.Instant.now().getEpochSecond(), org.assertj.core.data.Offset.offset(5L));
		assertThat(signatures.getFirst()).as("HMAC of the exact body").isEqualTo(usage.signature(bodies.getFirst(), Long.parseLong(parts[1])));
		assertThat(bodies.getFirst())
				.contains("\"event\":\"refresh\"", "\"addon\":\"plugin.googledrive\"", "\"addon_version\":\"1.5.6\"", "\"common_version\":\"1.4.0\"", "\"count\":2")
				.contains("\"error\":\"invalid_grant\"", "\"count\":1");
		assertThat(usage.pending()).isEmpty();
		usage.report();
		assertThat(bodies).as("nothing to send").hasSize(1);
	}

	@Test
	void signsWithTheSharedTokenAsTheKey() {
		// HMAC-SHA256(key "report-token", "1700000000.{}"), computed independently; signin-server checks the same vector.
		assertThat(reporter().signature("{}", 1_700_000_000L)).isEqualTo("t=1700000000,v1=dd048e5b96b9ea5a246af6fae0229cdb7fd89d8bc24def0b197dbb146c393906");
	}

	@Test
	void countsEachAccountOnceADayWithoutSendingIt() {
		final UsageReporter usage = reporter();
		usage.active("googledrive", "token-a", null);
		usage.active("googledrive", "token-a", null);
		usage.active("onedrive", "token-a", null);
		usage.active("googledrive", "token-b", null);
		assertThat(usage.pending()).containsExactlyInAnyOrderEntriesOf(Map.of(
				new UsageReporter.Key("active_account", "googledrive", "ok", "", "", "", ""), 2L,
				new UsageReporter.Key("active_account", "onedrive", "ok", "", "", "", ""), 1L));
		usage.report();
		assertThat(bodies.getFirst()).doesNotContain("token-a", "token-b");
	}

	@Test
	void keepsTheCountsWhenTheReportFails() {
		final UsageReporter usage = reporter();
		usage.count("pin", "onedrive", "created", "", null);
		status = 500;
		usage.report();
		assertThat(usage.pending()).containsEntry(new UsageReporter.Key("pin", "onedrive", "created", "", "", "", ""), 1L);
		status = 200;
		usage.count("pin", "onedrive", "created", "", null);
		usage.report();
		assertThat(bodies.getLast()).contains("\"count\":2");
		assertThat(usage.pending()).isEmpty();
	}

	@Test
	void doesNothingWhenNotConfigured() {
		final UsageReporter usage = new UsageReporter("", "");
		usage.count("pin", "onedrive", "created", "", null);
		usage.active("onedrive", "token", null);
		usage.report();
		assertThat(usage.pending()).isEmpty();
		assertThat(bodies).isEmpty();
	}
}
