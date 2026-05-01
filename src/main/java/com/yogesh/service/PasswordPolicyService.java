package com.yogesh.service;

import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyService {

	public void validateOrThrow(String password) {
		if (password == null || password.isBlank()) {
			throw new RuntimeException("Password daalo");
		}
		if (password.length() < 8) {
			throw new RuntimeException("Password kam se kam 8 characters ka hona chahiye");
		}
		if (!password.chars().anyMatch(Character::isUpperCase)) {
			throw new RuntimeException("Password me kam se kam 1 uppercase letter hona chahiye");
		}
		if (!password.chars().anyMatch(Character::isLowerCase)) {
			throw new RuntimeException("Password me kam se kam 1 lowercase letter hona chahiye");
		}
		if (!password.chars().anyMatch(Character::isDigit)) {
			throw new RuntimeException("Password me kam se kam 1 number hona chahiye");
		}
		if (password.chars().noneMatch(ch -> isSpecial((char) ch))) {
			throw new RuntimeException("Password me kam se kam 1 special character hona chahiye");
		}
	}

	private boolean isSpecial(char ch) {
		return "!@#$%^&*()_+-=[]{}|;:'\",.<>/?`~\\"
				.indexOf(ch) >= 0;
	}
}
