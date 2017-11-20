package com.syncinator.kodi.login.deprecated.onedrive;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncinator.kodi.login.deprecated.onedrive.ServiceController.ServiceResponse;
import com.syncinator.kodi.login.util.Utils;

@Deprecated
@RestController
@RequestMapping("/report-error.jsp")
public class ReportErrorController {
	
	@Autowired
    public JavaMailSender emailSender;
	
	@Value("${report.email.to}")
	private String to;
	@Value("${report.email.subject.prefix}")
	private String subjectPrefix;
	
	public void sendEmail(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage(); 
        message.setTo(to); 
        message.setSubject(subject); 
        message.setText(text);
        emailSender.send(message);
	}
	
	@RequestMapping
	public ServiceResponse reportError(@RequestParam String stacktrace, HttpServletRequest request) throws IOException {
		sendEmail(to, subjectPrefix + Utils.getRemoteAddress(request), stacktrace);
		ServiceResponse serviceResponse = new ServiceResponse();
		serviceResponse.setSuccess(true);
		return serviceResponse;
	}
}
