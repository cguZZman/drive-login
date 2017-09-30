package com.syncinator.kodi.login;

import java.security.SecureRandom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KodiLoginApplication {

	public static void main(String[] args) {
		SpringApplication.run(KodiLoginApplication.class, args);
	}
	
	@Bean
	public SecureRandom getSecureRandom() {
		return new SecureRandom();
	}
}
