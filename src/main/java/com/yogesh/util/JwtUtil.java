package com.yogesh.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

	private static final String LEGACY_DEFAULT_SECRET = "dev-only-change-me-jwt-secret-32-bytes-minimum-1234";
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;
	private final long expiration;

	public JwtUtil(@Value("${jwt.secret:}") String configuredSecret,
			@Value("${jwt.expiration}") long expiration,
			Environment environment) {
		this.key = Keys.hmacShaKeyFor(resolveSecret(configuredSecret, environment).getBytes(StandardCharsets.UTF_8));
		this.expiration = expiration;
	}

	public String generateToken(String username) {
		return Jwts.builder().setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + expiration))
				.signWith(key, SignatureAlgorithm.HS256).compact();
	}

	public String extractUsername(String token) {
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
	}

	public boolean isValid(String token) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
			return claims.getExpiration().after(new Date());
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	private String resolveSecret(String configuredSecret, Environment environment) {
		boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
		String secret = configuredSecret == null ? "" : configuredSecret.trim();

		if (secret.isBlank() || LEGACY_DEFAULT_SECRET.equals(secret)) {
			if (prod) {
				throw new IllegalStateException("JWT_SECRET env var must be set to a strong secret in production");
			}
			String generated = generateEphemeralSecret();
			log.warn("JWT_SECRET missing or legacy default detected. Using ephemeral dev secret for this startup only.");
			return generated;
		}

		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes long");
		}

		return secret;
	}

	private String generateEphemeralSecret() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
