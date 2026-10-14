package com.syncinator.kodi.login;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableScheduling
public class KodiLoginApplication {

	public static void main(String[] args) {
		SpringApplication.run(KodiLoginApplication.class, args);
	}

	@Bean
	public SecureRandom getSecureRandom() {
		return new SecureRandom();
	}

	@Bean
	public Cache<Object, Object> getCache() {
		return Caffeine.newBuilder()
				.maximumSize(100000)
				.expireAfterWrite(3, TimeUnit.MINUTES)
				.build();
	}
}
