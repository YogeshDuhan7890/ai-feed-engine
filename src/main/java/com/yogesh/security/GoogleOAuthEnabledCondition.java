package com.yogesh.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class GoogleOAuthEnabledCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String clientId = firstNonBlank(
				context.getEnvironment().getProperty("google.oauth.client-id"),
				context.getEnvironment().getProperty("spring.security.oauth2.client.registration.google.client-id"));
		String clientSecret = firstNonBlank(
				context.getEnvironment().getProperty("google.oauth.client-secret"),
				context.getEnvironment().getProperty("spring.security.oauth2.client.registration.google.client-secret"));
		return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}
}
