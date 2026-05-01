package com.yogesh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Value("${app.security.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}")
	private String allowedOrigins;

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {

		registry.enableSimpleBroker("/topic", "/queue");

		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");

	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {

		registry.addEndpoint("/ws").setAllowedOriginPatterns(resolveAllowedOrigins()).withSockJS();
	}

	private String[] resolveAllowedOrigins() {
		List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank())
				.toList();
		return origins.toArray(String[]::new);
	}
}
