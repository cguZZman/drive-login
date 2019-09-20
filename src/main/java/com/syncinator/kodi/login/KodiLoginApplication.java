package com.syncinator.kodi.login;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

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

@Configuration
class ActuatorSecurity extends WebSecurityConfigurerAdapter {
	@Value("${security.user.name}")
	private String username;
	@Value("${security.user.password}")
	private String password;
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			.authorizeRequests()
				.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(username)
				.antMatchers("/**").permitAll()
				.and()
				.formLogin()
					.loginPage("/login")
					.failureUrl("/login-error")
				.and()
				.csrf().disable();
	}
	
	@Bean
    @Override
    public UserDetailsService userDetailsService() {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails user = User.builder().passwordEncoder(encoder::encode)
                .username(username)
                .password(password)
                .roles(username)
                .build();
        return new InMemoryUserDetailsManager(user);
    }

}
