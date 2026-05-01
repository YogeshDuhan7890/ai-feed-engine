package com.yogesh.config;

import com.yogesh.security.GoogleOAuthEnabledCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@Configuration
@Conditional(GoogleOAuthEnabledCondition.class)
public class GoogleOAuthClientConfig {

	@Bean
	@ConditionalOnMissingBean(ClientRegistrationRepository.class)
	public ClientRegistrationRepository clientRegistrationRepository(
			@Value("${google.oauth.client-id:${spring.security.oauth2.client.registration.google.client-id:}}") String clientId,
			@Value("${google.oauth.client-secret:${spring.security.oauth2.client.registration.google.client-secret:}}") String clientSecret) {

		ClientRegistration google = ClientRegistration.withRegistrationId("google")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("email", "profile")
				.authorizationUri("https://accounts.google.com/o/oauth2/auth")
				.tokenUri("https://oauth2.googleapis.com/token")
				.userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
				.userNameAttributeName("sub")
				.clientName("Google")
				.build();

		return new InMemoryClientRegistrationRepository(google);
	}

	@Bean
	@ConditionalOnMissingBean(InMemoryOAuth2AuthorizedClientService.class)
	public InMemoryOAuth2AuthorizedClientService authorizedClientService(
			ClientRegistrationRepository clientRegistrationRepository) {
		return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
	}

	@Bean
	@ConditionalOnMissingBean(OAuth2AuthorizedClientRepository.class)
	public OAuth2AuthorizedClientRepository authorizedClientRepository() {
		return new HttpSessionOAuth2AuthorizedClientRepository();
	}

	@Bean
	@ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
	public OAuth2AuthorizedClientManager authorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository,
			InMemoryOAuth2AuthorizedClientService authorizedClientService,
			OAuth2AuthorizedClientRepository authorizedClientRepository) {

		OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
				.authorizationCode()
				.refreshToken()
				.build();

		DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
				clientRegistrationRepository,
				authorizedClientRepository);
		manager.setAuthorizedClientProvider(authorizedClientProvider);

		return manager;
	}
}
