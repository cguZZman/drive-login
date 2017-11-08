package com.syncinator.kodi.login.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Pin {
	private String pin;
	private String code;
	private String password;
	private String provider;
	private Map<String,Object> accessToken;
	
	public String getPin() {
		return pin;
	}
	public void setPin(String pin) {
		this.pin = pin;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getProvider() {
		return provider;
	}
	public void setProvider(String provider) {
		this.provider = provider;
	}
	public Map<String, Object> getAccessToken() {
		return accessToken;
	}
	public void setAccessToken(Map<String, Object> accessToken) {
		this.accessToken = accessToken;
	}
}
