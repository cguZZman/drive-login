package com.syncinator.kodi.login.controller.rest;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.codec.binary.Base64;
import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncinator.kodi.login.KodiLoginCacheManager;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.provider.Provider;

@RestController
@RequestMapping("/pin")
public class PinController {

	@SuppressWarnings("unused")
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private SecureRandom random;
	@Autowired
	private ApplicationContext context;

	@RequestMapping(method = RequestMethod.POST)
	public Pin generatePin(@RequestParam String provider) {
		context.getBean(Provider.NAME_PREFIX + provider);
		Cache<String, Pin> pinCache = KodiLoginCacheManager.getPinCache();
		Pin response = new Pin();
		String pin = null;
		while (pin == null || pinCache.containsKey(pin))
			pin = new BigInteger(24, random).toString(16).toLowerCase();
		response.setPin(pin.toUpperCase());
		response.setPassword(new BigInteger(1024, random).toString(16).toLowerCase());
		response.setProvider(provider);
		pinCache.put(pin, response);
		return response;
	}

	@RequestMapping(path = "/{pin}", method = RequestMethod.GET)
	public ResponseEntity<Map<String,Object>> getPin(@PathVariable String pin, HttpServletRequest request, HttpServletResponse response) throws IOException {
		Cache<String, Pin> pinCache = KodiLoginCacheManager.getPinCache();
		String key = pin.toLowerCase();
		Pin storedPin = pinCache.get(key);
		String auth = request.getHeader("authorization");
		if (storedPin != null && auth != null) {
			String[] data = auth.split(" ");
			if (data.length == 2 && data[0].equalsIgnoreCase("basic")) {
				String[] credentials = new String(Base64.decodeBase64(data[1].getBytes())).split(":");
				if (credentials.length > 1 && storedPin.getPassword().equals(credentials[1])) {
					if (storedPin.getAccessToken() == null) {
						return new ResponseEntity<>(HttpStatus.ACCEPTED);
					}
					pinCache.remove(key);
					return new ResponseEntity<Map<String,Object>>(storedPin.getAccessToken(), HttpStatus.OK);
				}
			}
		}
		response.sendError(HttpStatus.NOT_FOUND.value());
		return null;
	}

	@ExceptionHandler(NoSuchBeanDefinitionException.class)
	public void exceptionHandler(HttpServletResponse response, NoSuchBeanDefinitionException e) throws IOException{
		if (e.getBeanName().startsWith(Provider.NAME_PREFIX)) {
			response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid provider '"+e.getBeanName()+"'");
			return;
		}
		throw e;
	}
}
