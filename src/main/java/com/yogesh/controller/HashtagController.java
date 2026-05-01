package com.yogesh.controller;

import com.yogesh.service.HashtagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hashtags")
@RequiredArgsConstructor
public class HashtagController {

	private final HashtagService hashtagService;
	private final UserRepository userRepository;

	/** GET /api/hashtags/trending */
	@GetMapping("/trending")
	public List<Map<String, Object>> trending() {
		return hashtagService.getTrending();
	}

	/** GET /api/hashtags/search?tag=cricket */
	@GetMapping("/search")
	public Map<String, Object> search(@RequestParam String tag, Authentication auth) {
		Long userId = auth != null ? userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null) : null;
		return hashtagService.searchByTag(tag, userId);
	}

	/** GET /api/hashtags/autocomplete?q=cri */
	@GetMapping("/autocomplete")
	public List<String> autocomplete(@RequestParam String q) {
		return hashtagService.autocomplete(q);
	}
}