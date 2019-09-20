package com.syncinator.kodi.login.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.syncinator.kodi.login.util.Utils;

@Controller
public class IndexController {
	
	@RequestMapping("/")
	public String login(HttpServletRequest request) throws IOException {
		request.setAttribute("sourceid", Utils.getSourceId(request));
		return "index";
	}
	
	@RequestMapping("/auth-success")
	public String success() throws IOException {
		return "auth-success";
	}
	
	@RequestMapping("/failure")
	public String failure() throws IOException {
		return "auth-failure";
	}
	
	@RequestMapping("/privacypolicy")
	public String privacypolicy() throws IOException {
		return "privacypolicy";
	}
	
	@RequestMapping("/login")
	public String login() throws IOException {
		return "login";
	}
	
	@RequestMapping("/login-error")
	public String loginError(Model model) throws IOException {
		model.addAttribute("loginError", true);
		return "login";
	}
}
