package com.yogesh.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.yogesh.dto.SearchResultDTO;
import com.yogesh.service.SearchService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * BUG FIX: GET /search PageController mein pehle se mapped tha → "Ambiguous
 * mapping. Cannot map searchController to GET /search: There is already
 * pageController bean method PageController#searchPage mapped."
 *
 * Root cause: SearchController mein GET /search tha jo PageController se clash
 * karta tha. Fix: GET /search aur saare non-existent SearchService methods hata
 * diye. PageController already /search handle kar raha hai — woh kaafi hai.
 *
 * Sirf /public/search JSON API bacha lete hain.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	/**
	 * GET /public/search?q=keyword JSON API — frontend AJAX calls ke liye
	 */
	@GetMapping("/public/search")
	public ResponseEntity<SearchResponse> publicSearch(@RequestParam String q) {
		if (q == null || q.isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		List<SearchResultDTO.UserSearchDTO> users = searchService.searchUsers(q.trim());
		return ResponseEntity.ok(new SearchResponse(users));
	}

	@Data
	static class SearchResponse {
		private final List<SearchResultDTO.UserSearchDTO> users;
	}
}