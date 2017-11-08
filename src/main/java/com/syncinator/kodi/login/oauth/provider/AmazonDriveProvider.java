package com.syncinator.kodi.login.oauth.provider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component(Provider.NAME_PREFIX + AmazonDriveProvider.NAME)
public class AmazonDriveProvider extends Provider {
	protected static final String NAME = "amazondrive";
	
	@Override
	@SuppressWarnings("serial")
	public String authorize(String pin) {
		return getAuthorizeUrl(NAME, pin, new HashMap<String,String>() {{
			put("scope", "clouddrive:read_all");
			put("access_type", "offline");
		}});
	}

	@Override
	public Map<String,Object> tokens(String grantType, String value) throws Exception {
		return getTokens(NAME, grantType, value);
	}
}
