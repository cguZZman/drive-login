package com.syncinator.kodi.login.oauth.provider;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component(Provider.NAME_PREFIX + GoogleDriveProvider.NAME)
public class GoogleDriveProvider extends Provider {
	protected static final String NAME = "googledrive";
	
	@Override
	public String authorize(final String pin) {
		return getAuthorizeUrl(NAME, pin, Map.of(
				"scope", "https://www.googleapis.com/auth/drive.readonly profile",
				"access_type", "offline",
				"prompt", "consent"));
	}
	
	@Override
	public Map<String,Object> tokens(final String grantType, final String value) {
		return getTokens(NAME, grantType, value);
	}
}
