package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FeatureFlagService;
import com.yogesh.service.FeedService;
import com.yogesh.service.ProfileService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PageController {

	private final FeedService feedService;
	private final UserRepository userRepository;
	private final ProfileService profileService;
	private final FeatureFlagService featureFlagService;

	private boolean isAnonymous(Authentication authentication) {
		return authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal());
	}

	/*
	 * ======================== FEED / REELS PAGE ========================
	 */
	@GetMapping("/")
	public String home() {
		return "redirect:/feed";
	}

	@GetMapping("/feed")
	public String feedPage(Model model, Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		String email = authentication.getName();
		User user = userRepository.findByEmail(email).orElseThrow();
		model.addAttribute("userId", user.getId());
		model.addAttribute("userName", user.getName());
		return "reels";
	}

	/*
	 * ======================== PROFILE PAGE ========================
	 */
	@GetMapping("/profile")
	public String profile(Model model, Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		String email = authentication.getName();
		User user = profileService.getUserByEmail(email);
		model.addAttribute("user", user);
		model.addAttribute("videoCount", profileService.countByUserId(user.getId()));
		model.addAttribute("videos", profileService.getVideosByUserId(user.getId()));
		return "profile";
	}

	/*
	 * ======================== SEARCH PAGE ========================
	 */
	@GetMapping("/search")
	public String searchPage(@RequestParam(required = false) String q, Model model) {
		if (q != null && !q.isBlank())
			model.addAttribute("query", q);
		return "search";
	}

	@GetMapping("/maintenance")
	public String maintenancePage(Model model) {
		model.addAttribute("maintenanceMessage",
				featureFlagService.getString("maintenance:message", "Platform is under maintenance. Back soon!"));
		return "maintenance";
	}

	/*
	 * ======================== UPLOAD PAGE ========================
	 */
	@GetMapping("/upload")
	public String uploadPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "upload";
	}

	/*
	 * ======================== NOTIFICATIONS PAGE ========================
	 */
	@GetMapping("/notifications")
	public String notificationsPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "notifications";
	}

	/*
	 * ======================== PUBLIC USER PROFILE ========================
	 */
	@GetMapping("/profile/user/{userId}")
	public String userProfilePage(@PathVariable Long userId, Model model) {
		model.addAttribute("userId", userId);
		return "userprofile";
	}

	/*
	 * ======================== MESSAGES / DM PAGE — NEW ========================
	 */
	@GetMapping("/messages")
	public String messagesPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "messages";
	}

	@GetMapping("/messages/{userId}")
	public String messagesWith(@PathVariable Long userId, Model model, Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		model.addAttribute("targetUserId", userId);
		return "messages";
	}

	/*
	 * ======================== ANALYTICS PAGE — NEW ========================
	 */
	@GetMapping("/analytics")
	public String analyticsPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "analytics";
	}

	/*
	 * ======================== HASHTAG EXPLORE PAGE — NEW ========================
	 */
	@GetMapping("/hashtag/trending")
	public String hashtagTrending() {
		return "hashtag";
	}

	@GetMapping("/hashtag/{tag}")
	public String hashtagPage(@PathVariable String tag, Model model) {
		model.addAttribute("tag", tag);
		return "hashtag";
	}

	/*
	 * ======================== MONETIZATION PAGE ========================
	 */
	@GetMapping("/monetization")
	public String monetizationPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "monetization";
	}

	/*
	 * ======================== CREATOR STUDIO PAGE ========================
	 */
	@GetMapping("/studio")
	public String studioPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "monetization";
	}

	/*
	 * ======================== SAVED VIDEOS PAGE ========================
	 */
	@GetMapping("/saved")
	public String savedPage(Authentication authentication) {
		if (isAnonymous(authentication)) {
			return "redirect:/login";
		}
		return "saved";
	}
}
