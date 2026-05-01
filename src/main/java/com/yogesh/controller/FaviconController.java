package com.yogesh.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class FaviconController {

	/**
	 * FIX: favicon.ico 500 error fix. Spring Boot default favicon nahi serve karta
	 * agar static/favicon.ico nahi hai. 204 No Content return karo silently.
	 */
	@GetMapping("/favicon.ico")
	public void favicon(HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
}