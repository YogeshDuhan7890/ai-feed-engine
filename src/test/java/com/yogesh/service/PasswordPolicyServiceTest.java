package com.yogesh.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyServiceTest {

	private final PasswordPolicyService passwordPolicyService = new PasswordPolicyService();

	@Test
	void acceptsStrongPassword() {
		assertDoesNotThrow(() -> passwordPolicyService.validateOrThrow("Strong@123"));
	}

	@Test
	void rejectsPasswordWithoutSpecialCharacter() {
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> passwordPolicyService.validateOrThrow("Strong123"));
		org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("special"));
	}
}
