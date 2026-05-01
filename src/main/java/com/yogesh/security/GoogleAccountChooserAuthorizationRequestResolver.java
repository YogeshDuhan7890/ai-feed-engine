package com.yogesh.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Conditional(GoogleOAuthEnabledCondition.class)
public class GoogleAccountChooserAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public GoogleAccountChooserAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository,
				AUTHORIZATION_BASE_URI);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
		if (authorizationRequest == null) {
			return null;
		}
		return customizeIfGoogle(request, authorizationRequest, null);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
		if (authorizationRequest == null) {
			return null;
		}
		return customizeIfGoogle(request, authorizationRequest, clientRegistrationId);
	}

	private OAuth2AuthorizationRequest customizeIfGoogle(
			HttpServletRequest request,
			OAuth2AuthorizationRequest authorizationRequest,
			String clientRegistrationId) {

		String resolvedRegistrationId = clientRegistrationId;
		if (resolvedRegistrationId == null) {
			String uri = request.getRequestURI();
			if (uri != null && uri.startsWith(AUTHORIZATION_BASE_URI + "/")) {
				resolvedRegistrationId = uri.substring((AUTHORIZATION_BASE_URI + "/").length());
			}
		}

		if (!"google".equalsIgnoreCase(resolvedRegistrationId)) {
			return authorizationRequest;
		}

		Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
		additionalParameters.put("prompt", "select_account");
		String authorizationRequestUri = UriComponentsBuilder
				.fromUriString(authorizationRequest.getAuthorizationRequestUri())
				.replaceQueryParam("prompt", "select_account")
				.build(true)
				.toUriString();

		return OAuth2AuthorizationRequest.from(authorizationRequest)
				.additionalParameters(additionalParameters)
				.authorizationRequestUri(authorizationRequestUri)
				.build();
	}
}
