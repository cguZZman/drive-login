package com.syncinator.kodi.login.deprecated.onedrive;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Deprecated
@Controller
@RequestMapping("/forum")
public class ForumController {
	@RequestMapping
	public String forum(HttpServletResponse response) throws IOException {
		return "redirect:http://forum.kodi.tv/showthread.php?tid=228443";
	}
}
