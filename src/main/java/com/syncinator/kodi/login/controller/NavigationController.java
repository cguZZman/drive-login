package com.syncinator.kodi.login.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.provider.Provider;
import com.syncinator.kodi.login.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NavigationController {
	@NonNull
	private ApplicationContext context;
	@NonNull
	private Cache<Object, Object> cache;

	@Value("${funding.url}")
	private String fundingUrl;
	@Value("${funding.goal}")
	private int fundingGoal;
	@Value("${funding.raised}")
	private int fundingRaised;

	@GetMapping("/")
	public String index(final HttpServletRequest request) {
		request.setAttribute("sourceid", Utils.getSourceId(request));
		return "index";
	}

	@RequestMapping("/signin/{pin}")
	public String signin(
			@PathVariable final String pin,
			final Model model,
			final HttpServletRequest request) {
		return login(pin, model, request);
	}

	@RequestMapping("/authorize")
	public String login(
			@RequestParam final String pin,
			final Model model,
			final HttpServletRequest request) {
		final String unambiguousPin = pin.replace('O', '0');
		final Pin storedPin = (Pin) cache.getIfPresent(unambiguousPin.toLowerCase());
		if (storedPin != null && storedPin.getOwner().equals(Utils.getRemoteAddress(request))) {
			final Provider connector = context
					.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
			return "redirect:" + connector.authorize(unambiguousPin);
		}
		request.setAttribute("sourceid", Utils.getSourceId(request));
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
			final Model model) {
		if ("access_denied".equals(error)) {
			model.addAttribute("errorCode", "failure.denied");
		} else if (error != null) {
			model.addAttribute("errorText", error + ": " + errorDescription);
		} else if (state == null) {
			model.addAttribute("errorCode", "failure.code.3");
		} else {
			final Pin storedPin = (Pin) cache.getIfPresent(state.toLowerCase());
			if (storedPin != null) {
				final Provider connector = context
						.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
				final Map<String, Object> tokens = connector.tokens(Provider.GRANT_TYPE_AUTHORIZATION_CODE, code);
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
