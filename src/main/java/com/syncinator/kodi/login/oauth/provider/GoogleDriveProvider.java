package com.syncinator.kodi.login.oauth.provider;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component(Provider.NAME_PREFIX + GoogleDriveProvider.NAME)
public class GoogleDriveProvider extends Provider {
	protected static final String NAME = "googledrive";
	
	@Override
	public String authorize(final String pin, final String codeChallenge) {
		return getAuthorizeUrl(NAME, pin, codeChallenge, Map.of(
				"scope", "https://www.googleapis.com/auth/drive.readonly",
				"access_type", "offline",
				"prompt", "consent"));
	}
	
	@Override
	public Map<String,Object> exchangeCode(final String code, final String codeVerifier) {
		return getTokens(NAME, GRANT_TYPE_AUTHORIZATION_CODE, code, codeVerifier);
	}

	@Override
	public Map<String,Object> tokens(final String grantType, final String value) {
		return getTokens(NAME, grantType, value);
	}
}
