package com.syncinator.kodi.login.controller.rest;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.syncinator.kodi.login.util.Utils;

@RestController
public class IpController {
	@GetMapping("/ip")
	public String ip(HttpServletRequest request) {
		return Utils.getRemoteAddress(request);
	}
}
