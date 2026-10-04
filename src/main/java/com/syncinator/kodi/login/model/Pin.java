package com.syncinator.kodi.login.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class Pin {
	private String pin;
	private String code;
	private String password;
	private String owner;
	private String provider;
	private Map<String,Object> accessToken;
	// Set when a browser starts the sign-in (/authorize); never sent to Kodi.
	@JsonIgnore
	private String browserNonce;
	@JsonIgnore
	private String codeVerifier;
}
