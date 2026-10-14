package com.syncinator.kodi.login.usage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonymous usage counts, sent every hour to the new sign-in server (POST /ingest/usage), which stores them
 * with its own. Nothing identifying is counted or sent: no IP addresses, tokens or hashes. Daily active
 * accounts are counted with a set of token hashes kept in memory for the current UTC day only.
 * Turned off unless USAGE_REPORT_URL and USAGE_REPORT_TOKEN are set.
 */
@Slf4j
@Component
public class UsageReporter {
	// The Worker accepts at most this many events per request.
	static final int MAX_EVENTS_PER_REQUEST = 200;
	// "plugin.googledrive 1.5.6/1.4.0": the add-on and common module versions, sent by every add-on version.
	private static final Pattern ADDON_HEADER = Pattern.compile("^(plugin\\.[\\w.-]{1,40}) ([\\w.+-]{1,20})/([\\w.+-]{1,20})$");

	/** One counted combination; empty strings for what doesn't apply. */
	public record Key(String event, String provider, String outcome, String error, String addon, String addonVersion, String commonVersion) {
	}

	private final Map<Key, LongAdder> counts = new ConcurrentHashMap<>();
	private final Set<String> activeToday = new HashSet<>();
	private LocalDate activeDay = LocalDate.now(ZoneOffset.UTC);
	private final String url;
	private final String token;
	private final RestClient restClient;

	public UsageReporter(
			@Value("${usage.report-url:}") final String url,
			@Value("${usage.report-token:}") final String token) {
		this.url = url;
		this.token = token;
		this.restClient = RestClient.create();
	}

	public boolean enabled() {
		return StringUtils.hasText(url) && StringUtils.hasText(token);
	}

	/** Counts an event. addonHeader is the request's "addon" header (may be null). */
	public void count(final String event, final String provider, final String outcome, final String error, final String addonHeader) {
		if (!enabled()) {
			return;
		}
		String addon = "", addonVersion = "", commonVersion = "";
		if (addonHeader != null) {
			final Matcher m = ADDON_HEADER.matcher(addonHeader.trim());
			if (m.matches()) {
				addon = m.group(1);
				addonVersion = m.group(2);
				commonVersion = m.group(3);
			}
		}
		counts.computeIfAbsent(new Key(event, clip(provider), clip(outcome), clip(error), addon, addonVersion, commonVersion), k -> new LongAdder())
				.increment();
	}

	/** Counts the account (by its refresh token) as active today, once per UTC day. */
	public void active(final String provider, final String refreshToken, final String addonHeader) {
		if (!enabled() || refreshToken == null) {
			return;
		}
		final String hash = sha256(provider + ":" + refreshToken);
		final boolean first;
		synchronized (activeToday) {
			final LocalDate today = LocalDate.now(ZoneOffset.UTC);
			if (!today.equals(activeDay)) {
				activeDay = today;
				activeToday.clear();
			}
			first = activeToday.add(hash);
		}
		if (first) {
			count("active_account", provider, "ok", "", addonHeader);
		}
	}

	@Scheduled(cron = "0 0 * * * *", zone = "UTC")
	public void report() {
		if (!enabled() || counts.isEmpty()) {
			return;
		}
		final Map<Key, Long> snapshot = new LinkedHashMap<>();
		for (final Key key : List.copyOf(counts.keySet())) {
			final LongAdder adder = counts.remove(key);
			if (adder != null && adder.sum() > 0) {
				snapshot.put(key, adder.sum());
			}
		}
		final List<Map<String, Object>> events = new ArrayList<>();
		snapshot.forEach((k, n) -> {
			final Map<String, Object> event = new LinkedHashMap<>();
			event.put("event", k.event());
			event.put("provider", k.provider());
			event.put("app", "current");
			event.put("outcome", k.outcome());
			event.put("error", k.error());
			event.put("addon", k.addon());
			event.put("addon_version", k.addonVersion());
			event.put("common_version", k.commonVersion());
			event.put("count", n);
			events.add(event);
		});
		for (int i = 0; i < events.size(); i += MAX_EVENTS_PER_REQUEST) {
			final List<Map<String, Object>> batch = events.subList(i, Math.min(events.size(), i + MAX_EVENTS_PER_REQUEST));
			try {
				restClient.post()
						.uri(URI.create(url))
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.body(Map.of("events", batch))
						.retrieve()
						.toBodilessEntity();
			} catch (final RuntimeException e) {
				// Kept for the next report.
				log.warn("Usage report failed: {}", e.getMessage());
				for (final Map<String, Object> event : batch) {
					final Key key = new Key((String) event.get("event"), (String) event.get("provider"), (String) event.get("outcome"),
							(String) event.get("error"), (String) event.get("addon"), (String) event.get("addon_version"), (String) event.get("common_version"));
					counts.computeIfAbsent(key, k -> new LongAdder()).add((Long) event.get("count"));
				}
			}
		}
	}

	/** Sends what was counted since the last report when the dyno stops (Heroku restarts it daily). */
	@PreDestroy
	public void flush() {
		report();
	}

	Map<Key, Long> pending() {
		final Map<Key, Long> copy = new LinkedHashMap<>();
		counts.forEach((k, v) -> copy.put(k, v.sum()));
		return copy;
	}

	private static String clip(final String value) {
		if (value == null) {
			return "";
		}
		return value.length() > 64 ? value.substring(0, 64) : value;
	}

	private static String sha256(final String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
