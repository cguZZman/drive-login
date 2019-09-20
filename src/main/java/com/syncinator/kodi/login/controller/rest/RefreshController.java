package com.syncinator.kodi.login.controller.rest;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import com.syncinator.kodi.login.oauth.provider.Provider;

@RestController
@RequestMapping("/refresh")
public class RefreshController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private ApplicationContext context;

	@RequestMapping(method = RequestMethod.POST)
	public Map<String,Object> refresh(@RequestParam(required = false) String provider, 
			@RequestParam(name="refresh_token", required = false) 
		String refreshToken, HttpServletResponse response) throws Exception {
		logger.info("Provider: " + provider);
		if (StringUtils.isEmpty(provider) || StringUtils.isEmpty(refreshToken)) {
			logger.error(refreshToken);
			response.sendError(HttpStatus.BAD_REQUEST.value(), "Provider and refresh token required");
			return null;
		}
		Provider connector = context.getBean(Provider.NAME_PREFIX + provider, Provider.class);
		return connector.tokens(Provider.GRANT_TYPE_REFRESH_TOKEN, refreshToken);
	}


	@ExceptionHandler(NoSuchBeanDefinitionException.class)
	public void exceptionHandler(HttpServletRequest request, HttpServletResponse response, NoSuchBeanDefinitionException e) throws IOException{
		logger.error("NoSuchBeanDefinitionException: " + e.getBeanName());
		logger.error(request.getParameterMap().toString());
		if (e.getBeanName().startsWith(Provider.NAME_PREFIX)) {
			response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid provider '"+e.getBeanName()+"'");
			return;
		}
		throw e;
	}
	
	@ExceptionHandler(HttpClientErrorException.class)
	public void exceptionHandler(HttpServletResponse response, HttpClientErrorException e) throws IOException{
		logger.error("HttpClientErrorException: " + e.getStatusCode().value());
		logger.error(e.getStatusText());
		response.sendError(e.getStatusCode().value(), e.getStatusText());
		return;
	}
}
