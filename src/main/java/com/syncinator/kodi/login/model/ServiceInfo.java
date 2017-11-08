package com.syncinator.kodi.login.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ServiceInfo {
	private String capability;
	private String serviceApiVersion;
	private String serviceResourceId;
	private String serviceEndpointUri;
	
	public String getCapability() {
		return capability;
	}
	public void setCapability(String capability) {
		this.capability = capability;
	}
	public String getServiceApiVersion() {
		return serviceApiVersion;
	}
	public void setServiceApiVersion(String serviceApiVersion) {
		this.serviceApiVersion = serviceApiVersion;
	}
	public String getServiceResourceId() {
		return serviceResourceId;
	}
	public void setServiceResourceId(String serviceResourceId) {
		this.serviceResourceId = serviceResourceId;
	}
	public String getServiceEndpointUri() {
		return serviceEndpointUri;
	}
	public void setServiceEndpointUri(String serviceEndpointUri) {
		this.serviceEndpointUri = serviceEndpointUri;
	}
}
