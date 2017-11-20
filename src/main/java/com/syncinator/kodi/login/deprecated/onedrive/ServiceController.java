package com.syncinator.kodi.login.deprecated.onedrive;

import java.math.BigInteger;
import java.security.SecureRandom;

import javax.servlet.http.HttpServletRequest;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Deprecated
@RestController
@RequestMapping("/service.jsp")
public class ServiceController {

	private final String PIN = "pin";
	private final String CODE = "code";
	private final String BEGIN = "begin";
	private final String LOGIN = "login";
	
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);
	
	@Autowired
	private SecureRandom random;
		
	@RequestMapping
	public ServiceResponse service(
			@RequestParam(required=false, defaultValue=PIN) String action, 
			@RequestParam(required=false, defaultValue="1") String version, 
			@RequestParam(required=false) String pin, 
			@RequestParam(required=false) String code,
			HttpServletRequest request) {
		Cache<String, String> pinCache = KodiLoginCacheManager.getPinCache();
		ServiceResponse loginResponse = new ServiceResponse();
		if (action.equals(PIN)) {
			pin = null;
			while (pin == null || pinCache.containsKey(pin))
				pin = new BigInteger(20, random).toString(16).toUpperCase();
			loginResponse.setSuccess(true);
			loginResponse.setPin(pin);
			loginResponse.setVersion(version);
			pinCache.put(pin, version);
		} else if (action.equals(BEGIN)) {
			loginResponse.setSuccess(pin != null && pinCache.containsKey(pin));
			if (loginResponse.isSuccess()) {
				loginResponse.setVersion(pinCache.get(pin));
			}
		} else if (action.equals(LOGIN)) {
			loginResponse.setSuccess(pin != null && pinCache.containsKey(pin) && code != null);
			if (loginResponse.isSuccess()) {
				pinCache.put(pin, code);
			}
		} else if (action.equals(CODE)) {
			loginResponse.setSuccess(pin != null && pinCache.get(pin) != null && pinCache.get(pin).length() > 5);
			if (loginResponse.isSuccess()) {
				loginResponse.setCode(pinCache.get(pin));
			}
		}
		return loginResponse;
	}
	
	@JsonInclude(Include.NON_NULL)
	public static class ServiceResponse {
		private String version;
		private String pin;
		private String code;
		private boolean success;
		
		public String getPin() {
			return pin;
		}
		public void setPin(String pin) {
			this.pin = pin;
		}
		public String getVersion() {
			return version;
		}
		public void setVersion(String version) {
			this.version = version;
		}
		public String getCode() {
			return code;
		}
		public void setCode(String code) {
			this.code = code;
		}
		public boolean isSuccess() {
			return success;
		}
		public void setSuccess(boolean success) {
			this.success = success;
		}
	}
}
