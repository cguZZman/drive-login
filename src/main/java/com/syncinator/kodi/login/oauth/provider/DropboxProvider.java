package com.syncinator.kodi.login.oauth.provider;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component(Provider.NAME_PREFIX + DropboxProvider.NAME)
public class DropboxProvider extends Provider {
	protected static final String NAME = "dropbox";

	@Override
	public String authorize(String pin) {
		return getAuthorizeUrl(NAME, pin);
	}

	@Override
	public Map<String,Object> tokens(String grantType, String value) throws Exception {
		return getTokens(NAME, grantType, value);
	}
}
