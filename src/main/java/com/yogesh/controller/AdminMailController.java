package com.yogesh.controller;

import com.yogesh.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailController {

	private final EmailService emailService;

	@GetMapping("/health")
	public Map<String, Object> health() {
		return emailService.mailHealth();
	}
}
