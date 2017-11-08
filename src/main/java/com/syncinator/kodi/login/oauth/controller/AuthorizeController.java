package com.syncinator.kodi.login.oauth.controller;

import java.io.IOException;

import org.ehcache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.syncinator.kodi.login.KodiLoginCacheManager;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.provider.Provider;

@Controller
public class AuthorizeController {
	
	@Autowired
	private ApplicationContext context;
	
	@RequestMapping("/authorize")
	public String login(@RequestParam String pin, Model model) throws IOException {
		Cache<String, Pin> pinCache = KodiLoginCacheManager.getPinCache();
		pin = pin.replace('O', '0');
		Pin storedPin = pinCache.get(pin.toLowerCase());
		if (storedPin != null) {
			Provider connector = context.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
			return "redirect:" + connector.authorize(pin);
		}
		model.addAttribute("errorMessage", "error.pin.invalid");
		return "index";
	}
}
