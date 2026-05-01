package com.yogesh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeans {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/*
	 * BUG FIX: ObjectMapper mein JavaTimeModule add kiya
	 * Pehle sirf new ObjectMapper() tha — LocalDateTime serialize nahi hoti thi
	 * JSON response mein (e.g. createdAt array aa jata tha "2025-03-18T10:00:00"
	 * ki jagah).
	 *
	 * JavaTimeModule register karne ke baad LocalDateTime properly
	 * ISO-8601 string mein serialize hogi: "2025-03-18T10:00:00"
	 *
	 * Aur pom.xml mein jackson-datatype-jsr310 already spring-boot-starter-json
	 * ke through aa raha hai — alag dependency add nahi chahiye.
	 */
	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		// ISO string chahiye, timestamps nahi
		mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}