package com.syncinator.kodi.login.oauth.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

@Slf4j
public abstract class Provider {
	public static final String NAME_PREFIX = "provider.";
	public static final String ENV_PREFIX = "PROVIDER_";
	public static final String ENV_CLIENT_ID = "_CLIENT_ID";
	public static final String ENV_CLIENT_SECRET = "_CLIENT_SECRET";
	public static final String ENV_URL_AUTHORIZE = "_URL_AUTHORIZE";
	public static final String ENV_URL_TOKEN = "_URL_TOKEN";
	public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
	public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
	
	protected RestClient restClient = RestClient.create();
	
	public abstract String authorize(String pin);
	public abstract Map<String,Object> tokens(String grantType, String value);
	
	@Value("${callback.url}")
	protected String callbackUrl;

	public String getAuthorizeUrl(final String name, final String pin, final Map<String,String> extraParams) {
		final UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(getEnv(name, ENV_URL_AUTHORIZE))
				.queryParam("client_id", getEnv(name, ENV_CLIENT_ID))
				.queryParam("redirect_uri", callbackUrl)
				.queryParam("state", pin)
				.queryParam("response_type", "code");
		if (extraParams != null && !extraParams.isEmpty()) {
			for (final Entry<String,String> e : extraParams.entrySet()) {
				builder.queryParam(e.getKey(), e.getValue());
			}
		}
		return builder.build().toUriString();
		
	}
	
	protected Map<String,Object> getTokens(
			final String name,
			final String grantType,
			final String value) {
		final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("client_id", getEnv(name, ENV_CLIENT_ID));
		params.add("redirect_uri", callbackUrl);
		final String secret = getEnv(name, ENV_CLIENT_SECRET);
		if (secret != null && !secret.isEmpty()) {
			params.add("client_secret", secret);
		}
		params.add("grant_type", grantType);
		params.add(grantType.replace("authorization_", ""), value);
		try {
			return oauthPost(getEnv(name, ENV_URL_TOKEN), params);
		} catch (final RestClientResponseException e) {
			// Only the provider's error code and description: never the token or the raw response body.
			final Map<String,Object> error = parseError(e.getResponseBodyAsString());
			log.warn("Token request failed: provider={} grant_type={} status={} error={} description={}",
					name, grantType, e.getStatusCode().value(), error.get("error"), truncate(error.get("error_description")));
			throw e;
		}
	}

	private static Map<String,Object> parseError(final String body) {
		try {
			return JsonParserFactory.getJsonParser().parseMap(body);
		} catch (final RuntimeException e) {
			return Map.of();
		}
	}

	private static String truncate(final Object value) {
		final String text = String.valueOf(value);
		return text.length() > 200 ? text.substring(0, 200) + "..." : text;
	}

	protected Map<String,Object> oauthPost(final String url, final MultiValueMap<String, String> params) {
		return restClient.post()
				.uri(URI.create(url))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(params)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
	}
	
	protected String getEnv(final String provider, final String var) {
		return System.getenv(ENV_PREFIX + getEnvProvider(provider.toUpperCase()) + var);
	}
	protected String getEnvProvider(final String provider) {
		return provider.replace('.', '_');
	}
}
