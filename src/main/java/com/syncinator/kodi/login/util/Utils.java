package com.syncinator.kodi.login.util;

import javax.servlet.http.HttpServletRequest;

public class Utils {
	public static String getRemoteAddress(HttpServletRequest request) {
		String remote = request.getHeader("x-forwarded-for");
		if (remote == null) {
			remote = request.getRemoteAddr();
		}
		return remote;
	}
}
