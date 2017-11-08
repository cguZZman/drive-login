package com.syncinator.kodi.login.oauth.provider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component(Provider.NAME_PREFIX + OneDriveBusinessProvider.NAME)
public class OneDriveBusinessProvider extends Provider {
	protected static final String NAME = "onedrive.business";
	
	@Override
	@SuppressWarnings("serial")
	public String authorize(String pin) {
		return getAuthorizeUrl(NAME, pin, new HashMap<String,String>() {{
			put("response_mode", "form_post");
		}});
	}
	
	@Override
	public Map<String,Object> tokens(String grantType, String value) throws Exception {
		return getTokens(NAME, grantType, value);
	}
	/*
	@Override
	public Map<String,Object> tokens(String code) throws Exception {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("client_id", "f8b0244a-8640-41e8-bc5f-882f8653b7d6");
		params.add("redirect_uri", "http://localhost:8888/callback");
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("resource", "https://api.office.com/discovery/");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		ResponseEntity<HashMap<String,Object>> tokensEntity = restTemplate.exchange(
			new URI("https://login.microsoftonline.com/common/oauth2/token"),
			HttpMethod.POST,
			new HttpEntity<MultiValueMap<String, String>>(params, headers),
			new ParameterizedTypeReference<HashMap<String, Object>>() {}
		);
		Map<String,Object> tokens = tokensEntity.getBody();
		headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("access_token"));
		ResponseEntity<ServiceInfoCollection> servicesEntity = restTemplate.exchange(
			new URI("https://api.office.com/discovery/v2.0/me/services"),
			HttpMethod.GET,
			new HttpEntity<>(headers),
			ServiceInfoCollection.class
		);
		ServiceInfo endPoint = null; 
		for (ServiceInfo serviceInifo : servicesEntity.getBody().getValue()) {
			if (serviceInifo.getCapability().equals("MyFiles") && serviceInifo.getServiceApiVersion().equals("v2.0")) {
				endPoint = serviceInifo;
			}
		}
		if (endPoint != null) {
			params.clear();
			params.add("client_id", "f8b0244a-8640-41e8-bc5f-882f8653b7d6");
			params.add("redirect_uri", "http://localhost:8888/callback");
			params.add("grant_type", "refresh_token");
			params.add("refresh_token", (String) tokens.get("refresh_token"));
			params.add("resource", endPoint.getServiceResourceId());
			tokensEntity = restTemplate.exchange(
				new URI("https://login.microsoftonline.com/common/oauth2/token"),
				HttpMethod.POST,
				new HttpEntity<MultiValueMap<String, String>>(params, headers),
				new ParameterizedTypeReference<HashMap<String, Object>>() {}
			);
			return tokensEntity.getBody();
		}
		return null;
	}
	*/
}
