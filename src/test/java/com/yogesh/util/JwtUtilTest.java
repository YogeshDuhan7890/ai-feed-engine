package com.yogesh.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

	@Test
	void generatesEphemeralSecretInDevWhenMissing() {
		JwtUtil jwtUtil = new JwtUtil("", 60_000L, new MockEnvironment());

		String token = jwtUtil.generateToken("demo@example.com");

		assertTrue(jwtUtil.isValid(token));
		assertEquals("demo@example.com", jwtUtil.extractUsername(token));
	}

	@Test
	void rejectsMissingSecretInProd() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");

		assertThrows(IllegalStateException.class, () -> new JwtUtil("", 60_000L, environment));
	}

	@Test
	void rejectsShortSecret() {
		assertThrows(IllegalStateException.class,
				() -> new JwtUtil("too-short-secret", 60_000L, new MockEnvironment()));
	}
}
