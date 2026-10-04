package com.syncinator.kodi.login.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.provider.Provider;
import com.syncinator.kodi.login.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ApiController {
	@NonNull
	private ApplicationContext context;
	@NonNull
	private SecureRandom random;
	@NonNull
	private Cache<Object, Object> cache;

	@PostMapping("/pin")
	public Pin generatePin(@RequestParam final String provider, final HttpServletRequest request) {
		context.getBean(Provider.NAME_PREFIX + provider);
		String pin = null;
		while (pin == null || Objects.nonNull(cache.getIfPresent(pin))) {
			pin = String.format("%06x", new BigInteger(24, random));
		}
		final Pin response = Pin.builder()
				.pin(pin.toUpperCase())
				.password(new BigInteger(2048, random).toString(16).toLowerCase())
				.provider(provider)
				.owner(Utils.getRemoteAddress(request))
				.build();
		cache.put(pin, response);
		return response;
	}

	@SneakyThrows
	@GetMapping("/pin/{pin}")
	public ResponseEntity<Map<String,Object>> getPin(
			@PathVariable final String pin,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		final String key = pin.toLowerCase();
		final Pin storedPin = (Pin) cache.getIfPresent(key);
		final String auth = request.getHeader("authorization");
		if (storedPin != null && auth != null && storedPin.getOwner().equals(Utils.getRemoteAddress(request))) {
			final String[] data = auth.split(" ");
			if (data.length == 2 && data[0].equalsIgnoreCase("basic")) {
				final String[] credentials = new String(Base64.getDecoder().decode(data[1])).split(":");
				if (credentials.length > 1 && storedPin.getPassword().equals(credentials[1])) {
					if (storedPin.getAccessToken() == null) {
						return new ResponseEntity<>(HttpStatus.ACCEPTED);
					}
					cache.invalidate(key);
					return new ResponseEntity<>(storedPin.getAccessToken(), HttpStatus.OK);
				}
			}
		}
		response.sendError(HttpStatus.NOT_FOUND.value());
		return null;
	}

	@SneakyThrows
	@PostMapping("/refresh")
	public Map<String,Object> refresh(
			@RequestParam(required = false) final String provider,
			@RequestParam(required = false, name="refresh_token") final String refreshToken,
			final HttpServletResponse response) {
		if (!StringUtils.hasLength(provider) || !StringUtils.hasLength(refreshToken)) {
			response.sendError(HttpStatus.BAD_REQUEST.value(), "Provider and refresh token required");
			return null;
		}
		final Provider connector = context.getBean(Provider.NAME_PREFIX + provider, Provider.class);
		return connector.tokens(Provider.GRANT_TYPE_REFRESH_TOKEN, refreshToken);
	}

	@GetMapping("/ip")
	public String ip(final HttpServletRequest request) {
		return Utils.getRemoteAddress(request);
	}

	@SneakyThrows
	@ExceptionHandler(NoSuchBeanDefinitionException.class)
	public void exceptionHandler(
			final HttpServletResponse response,
			final NoSuchBeanDefinitionException e) {
		if (Objects.requireNonNull(e.getBeanName()).startsWith(Provider.NAME_PREFIX)) {
			response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid provider '"+e.getBeanName()+"'");
			return;
		}
		throw e;
	}

	@SneakyThrows
	@ExceptionHandler(HttpClientErrorException.class)
	public void exceptionHandler(
			final HttpServletResponse response,
			final HttpClientErrorException e) {
		response.sendError(e.getStatusCode().value(), e.getStatusText());
	}
}
