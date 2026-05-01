package com.yogesh.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

	@RequestMapping("/error")
	public String handleError(HttpServletRequest request, Model model) {

		Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

		model.addAttribute("message", message);

		if (statusCode != null) {
			int code = Integer.parseInt(statusCode.toString());

			if (code == HttpStatus.NOT_FOUND.value()) {
				// FIX: "error/404" → "404"
				// Thymeleaf resolvers classpath:/structure/ mein dhundh rahe hain
				// wahan file "404.html" hai, "error/404.html" nahi
				return "404";
			}

			if (code == HttpStatus.FORBIDDEN.value()) {
				return "500";
			}
		}

		// FIX: "error/500" → "500"
		return "500";
	}
}