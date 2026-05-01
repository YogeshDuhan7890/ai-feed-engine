package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.ReportService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;
	private final UserRepository userRepository;

	/** POST /api/report/user/{userId} — user report karo */
	@PostMapping("/user/{userId}")
	public Map<String, Object> reportUser(@PathVariable Long userId, @RequestBody Map<String, String> body,
			Authentication auth) {
		User me = getUser(auth);
		return reportService.report(me.getId(), "USER", userId, body.get("reason"), body.get("description"));
	}

	/** POST /api/report/post/{postId} — post report karo */
	@PostMapping("/post/{postId}")
	public Map<String, Object> reportPost(@PathVariable Long postId, @RequestBody Map<String, String> body,
			Authentication auth) {
		User me = getUser(auth);
		return reportService.report(me.getId(), "POST", postId, body.get("reason"), body.get("description"));
	}

	/** GET /api/report/pending — admin: pending reports */
	@GetMapping("/pending")
	@PreAuthorize("hasRole('ADMIN')")
	public Object pending(@RequestParam(defaultValue = "0") int page) {
		return reportService.getPendingReports(page);
	}

	/** POST /api/report/{id}/resolve */
	@PostMapping("/{id}/resolve")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, String> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
		return reportService.resolveReport(id, body.getOrDefault("status", "RESOLVED"));
	}
	
//	@GetMapping("/resolved")
//	public Object resolved(@RequestParam(defaultValue = "0") int page) {
//	    return reportService.getResolvedReports(page);
//	}
	
	@GetMapping("/resolved")
	@PreAuthorize("hasRole('ADMIN')")
	public Object resolved(@RequestParam(defaultValue = "0") int page, Authentication auth) {
	    // ✅ Admin check
	    User me = getUser(auth);
	    if (!me.getRole().equals("ADMIN")) {
	        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
	    }
	    return reportService.getResolvedReports(page);
	}

	private User getUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}
}
