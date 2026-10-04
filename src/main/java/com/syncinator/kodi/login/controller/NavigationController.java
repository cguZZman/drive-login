package com.syncinator.kodi.login.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.Pkce;
import com.syncinator.kodi.login.oauth.provider.Provider;
import com.syncinator.kodi.login.util.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NavigationController {
	// Ties the browser that starts the sign-in to the one that finishes it, so a sign-in link
	// cannot be sent to someone else to collect their tokens. SameSite=None because Microsoft
	// returns with a cross-site form POST (response_mode=form_post), which would not carry a Lax cookie.
	static final String SIGNIN_COOKIE = "__Host-signin";
	private static final Duration SIGNIN_COOKIE_AGE = Duration.ofMinutes(5);

	@NonNull
	private ApplicationContext context;
	@NonNull
	private Cache<Object, Object> cache;
	@NonNull
	private SecureRandom random;
	@NonNull
	private ClientAddress clientAddress;

	@Value("${funding.url}")
	private String fundingUrl;
	@Value("${funding.goal}")
	private int fundingGoal;
	@Value("${funding.raised}")
	private int fundingRaised;

	@GetMapping("/")
	public String index(final HttpServletRequest request) {
		request.setAttribute("sourceid", clientAddress.sourceId(request));
		return "index";
	}

	@RequestMapping("/signin/{pin}")
	public String signin(
			@PathVariable final String pin,
			final Model model,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		return login(pin, model, request, response);
	}

	@RequestMapping("/authorize")
	public String login(
			@RequestParam final String pin,
			final Model model,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		final String unambiguousPin = pin.replace('O', '0');
		final Pin storedPin = (Pin) cache.getIfPresent(unambiguousPin.toLowerCase());
		if (storedPin != null && storedPin.getOwner().equals(clientAddress.of(request))) {
			final Provider connector = context
					.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
			final String nonce = randomToken();
			final String codeVerifier = Pkce.verifier(random);
			storedPin.setBrowserNonce(nonce);
			storedPin.setCodeVerifier(codeVerifier);
			response.addHeader(HttpHeaders.SET_COOKIE, signinCookie(nonce, SIGNIN_COOKIE_AGE));
			return "redirect:" + connector.authorize(unambiguousPin, Pkce.challenge(codeVerifier));
		}
		request.setAttribute("sourceid", clientAddress.sourceId(request));
		model.addAttribute("pin", unambiguousPin);
		model.addAttribute("errorMessage", "error.pin.invalid");
		return "index";
	}

	@RequestMapping("/callback")
	public String callback(
			@RequestParam(required=false) final String code,
			@RequestParam(required=false) final String state,
			@RequestParam(required=false) final String error,
			@RequestParam(required=false, name="error_description") final String errorDescription,
			@CookieValue(name=SIGNIN_COOKIE, required=false) final String browserNonce,
			final Model model,
			final HttpServletResponse response) {
		if ("access_denied".equals(error)) {
			model.addAttribute("errorCode", "failure.denied");
		} else if (error != null) {
			model.addAttribute("errorText", error + ": " + errorDescription);
		} else if (state == null) {
			model.addAttribute("errorCode", "failure.code.3");
		} else {
			final Pin storedPin = (Pin) cache.getIfPresent(state.toLowerCase());
			if (storedPin != null && !sameBrowser(storedPin, browserNonce)) {
				log.warn("Sign-in callback rejected: not finished in the browser that started it");
				model.addAttribute("errorCode", "failure.code.4");
			} else if (storedPin != null) {
				// Single use: a second callback for this code is rejected.
				storedPin.setBrowserNonce(null);
				response.addHeader(HttpHeaders.SET_COOKIE, signinCookie("", Duration.ZERO));
				final Provider connector = context
						.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
				final Map<String, Object> tokens = connector.exchangeCode(code, storedPin.getCodeVerifier());
				if (tokens != null) {
					storedPin.setAccessToken(tokens);
					return "redirect:auth-success";
				} else {
					model.addAttribute("errorCode", "failure.code.2");
				}
			} else {
				model.addAttribute("errorCode", "failure.code.1");
			}
		}
		return "auth-failure";
	}

	private static boolean sameBrowser(final Pin pin, final String browserNonce) {
		final String expected = pin.getBrowserNonce();
		return expected != null && browserNonce != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), browserNonce.getBytes(StandardCharsets.UTF_8));
	}

	private String randomToken() {
		final byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String signinCookie(final String value, final Duration maxAge) {
		return ResponseCookie.from(SIGNIN_COOKIE, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.path("/")
				.maxAge(maxAge)
				.build()
				.toString();
	}

	@GetMapping("/auth-success")
	public String success(final Model model) {
		if (StringUtils.hasText(fundingUrl)) {
			model.addAttribute("fundingUrl", fundingUrl);
			if (fundingGoal > 0) {
				model.addAttribute("fundingGoal", String.format("%,d", fundingGoal));
				model.addAttribute("fundingRaised", String.format("%,d", fundingRaised));
				model.addAttribute("fundingPercent", Math.min(100, Math.max(0, fundingRaised * 100 / fundingGoal)));
			}
		}
		return "auth-success";
	}
	
	@GetMapping("/failure")
	public String failure() {
		return "auth-failure";
	}
	
	@GetMapping("/privacypolicy")
	public String privacypolicy() {
		return "privacypolicy";
	}

	@ExceptionHandler(Exception.class)
	public String exceptionHandler(
			final Model model,
			final Exception e){
		model.addAttribute("errorText", e.getMessage());
		return "auth-failure";
	}
}
