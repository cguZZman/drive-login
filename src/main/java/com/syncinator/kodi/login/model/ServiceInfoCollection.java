package com.syncinator.kodi.login.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ServiceInfoCollection {
	List<ServiceInfo> value;
	
	public List<ServiceInfo> getValue() {
		return value;
	}
	public void setValue(List<ServiceInfo> value) {
		this.value = value;
	}
}
