package com.syncinator.kodi.login.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/forum")
public class ForumController {
	@RequestMapping(method = RequestMethod.GET)
	public void forum(HttpServletResponse response) throws IOException {
		response.sendRedirect("http://forum.kodi.tv/showthread.php?tid=228443");
	}
}
