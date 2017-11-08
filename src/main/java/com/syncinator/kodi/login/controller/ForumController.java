package com.syncinator.kodi.login.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ForumController {
	
	@RequestMapping("/forum")
	public String forum(HttpServletResponse response) throws IOException {
		return "redirect:http://forum.kodi.tv/showthread.php?tid=228443";
	}
}
