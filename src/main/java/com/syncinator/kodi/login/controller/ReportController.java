package com.syncinator.kodi.login.controller;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncinator.kodi.login.util.Utils;

@RestController
@RequestMapping("/report")
public class ReportController {
	
	@Autowired
    public JavaMailSender emailSender;
	
	@Value("${report.email.to}")
	private String to;
	@Value("${report.email.subject.prefix}")
	private String subjectPrefix;
	
	@RequestMapping(method=RequestMethod.POST)
	public void reportError(@RequestParam String stacktrace, HttpServletRequest request) throws IOException {
		SimpleMailMessage message = new SimpleMailMessage(); 
        message.setTo(to); 
        message.setSubject(subjectPrefix + Utils.getRemoteAddress(request));
        StringBuffer text = new StringBuffer();
        text.append(stacktrace);
        text.append("\nReported from:\n");
        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
        	String header = headers.nextElement();
        	text.append(header).append(": ").append(request.getHeader(header)).append(System.lineSeparator());
        }
        message.setText(text.toString());
        emailSender.send(message);
	}
}
