package com.syncinator.kodi.login.util;

import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

public class Utils {
	public static String getRemoteAddress(HttpServletRequest request) {
		String remote = request.getHeader("x-forwarded-for");
		if (remote == null) {
			remote = request.getRemoteAddr();
		}
		return remote;
	}
	public static String getSourceId(HttpServletRequest request) {
		String ip = getRemoteAddress(request);
		int sourceid = 0;
		try {
			sourceid = Stream.of(ip.contains(".")?ip.split("\\."):ip.split(":")).mapToInt(n -> Integer.parseInt(n)).sum();
		} catch(Exception e) {
			sourceid = -1;
		}
		return String.valueOf(sourceid);
	}
	
}
