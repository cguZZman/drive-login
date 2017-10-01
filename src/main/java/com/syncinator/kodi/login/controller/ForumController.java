package com.syncinator.kodi.login.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/forum")
public class ForumController {
	@RequestMapping
	public void forum(HttpServletResponse response) throws IOException {
		response.sendRedirect("http://forum.kodi.tv/showthread.php?tid=228443");
	}
}
