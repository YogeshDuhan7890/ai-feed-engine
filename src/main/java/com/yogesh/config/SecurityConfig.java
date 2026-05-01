package com.yogesh.config;

import com.yogesh.security.CustomLogoutHandler;
import com.yogesh.security.GoogleAccountChooserAuthorizationRequestResolver;
import com.yogesh.security.IpBanFilter;
import com.yogesh.security.JwtAuthFilter;
import com.yogesh.security.LoginFailureHandler;
import com.yogesh.security.OAuth2SuccessHandler;
import com.yogesh.security.RateLimitingFilter;
import com.yogesh.security.RoleBasedSuccessHandler;
import com.yogesh.security.TwoFactorEnforcementFilter;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/login", "/login/otp", "/register", "/auth/google", "/maintenance",
			"/css/**", "/js/**", "/images/**", "/fonts/**", "/uploads/**", "/videos/**",
			"/sw.js", "/favicon.ico", "/manifest.json",
			"/api/hybrid", "/api/payment/webhook", "/ws/**",
			"/api/live/active", "/api/live/*", "/api/ai/status", "/api/presence/online/**",
			"/verify-email", "/resend-verification", "/verify-email-change", "/forgot-password",
			"/reset-password", "/actuator/health"
	};

	private static final String[] PUBLIC_API_ENDPOINTS = {
			"/api/auth/**",
			"/api/account/forgot-password",
			"/api/account/reset-password",
			"/api/account/send-otp",
			"/api/account/verify-otp",
			"/api/account/login-2fa/verify",
			"/api/account/login-2fa/resend"
	};

	private final RoleBasedSuccessHandler successHandler;
	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final LoginFailureHandler loginFailureHandler;
	private final ObjectProvider<GoogleAccountChooserAuthorizationRequestResolver> googleAuthorizationRequestResolverProvider;
	private final CustomLogoutHandler customLogoutHandler;
	private final JwtAuthFilter jwtAuthFilter;
	private final IpBanFilter ipBanFilter;
	private final RateLimitingFilter rateLimitingFilter;
	private final TwoFactorEnforcementFilter twoFactorEnforcementFilter;
	@Value("${google.oauth.client-id:${spring.security.oauth2.client.registration.google.client-id:}}")
	private String googleClientId;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		http.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers(
						new AntPathRequestMatcher("/login"),
						new AntPathRequestMatcher("/register"),
						new AntPathRequestMatcher("/api/payment/webhook/**"),
						request -> {
							String auth = request.getHeader("Authorization");
							return auth != null && auth.startsWith("Bearer ");
						}))

				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_ENDPOINTS)
						.permitAll()

						.requestMatchers(PUBLIC_API_ENDPOINTS).permitAll()

						.requestMatchers("/account/settings").authenticated()

						.requestMatchers("/admin/**", "/api/admin/**", "/actuator/**")
						.hasRole("ADMIN")

						.requestMatchers("/api/**", "/profile/**", "/messages/**", "/feed/**").authenticated()

						.anyRequest().authenticated())

				.addFilterBefore(ipBanFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(rateLimitingFilter, JwtAuthFilter.class)
				.addFilterAfter(twoFactorEnforcementFilter, UsernamePasswordAuthenticationFilter.class)

				.formLogin(form -> form.loginPage("/login").successHandler(successHandler)
						.failureHandler(loginFailureHandler).permitAll())

				.logout(logout -> logout.logoutUrl("/logout").addLogoutHandler(customLogoutHandler)
						.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
						.permitAll())

				.sessionManagement(session -> session.maximumSessions(1).maxSessionsPreventsLogin(false))

				.headers(headers -> headers.frameOptions(fo -> fo.sameOrigin())
						.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));

		if (StringUtils.hasText(googleClientId)) {
			http.oauth2Login(oauth -> {
				oauth.loginPage("/login").successHandler(oAuth2SuccessHandler);
				GoogleAccountChooserAuthorizationRequestResolver resolver = googleAuthorizationRequestResolverProvider
						.getIfAvailable();
				if (resolver != null) {
					oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver));
				}
			});
		}

		return http.build();
	}
}
