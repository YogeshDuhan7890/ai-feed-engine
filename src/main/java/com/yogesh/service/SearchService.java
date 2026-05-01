package com.yogesh.service;

import com.yogesh.dto.SearchResultDTO;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.HashtagRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final PostRepository postRepository;
	private final HashtagRepository hashtagRepository;

	// ── User search ───────────────────────────────────────────────
	public List<SearchResultDTO.UserSearchDTO> searchUsers(String query) {
		if (query == null || query.trim().isEmpty())
			return List.of();
		Long currentUserId = getCurrentUserId();
		return userRepository.searchByNameOrEmail(query.trim(), PageRequest.of(0, 20)).stream()
				.filter(user -> currentUserId == null || !user.getId().equals(currentUserId)).map(user -> {
					boolean isFollowing = currentUserId != null
							&& followRepository.existsByFollowerIdAndFollowingId(currentUserId, user.getId());
					return new SearchResultDTO.UserSearchDTO(user.getId(), user.getName(), user.getEmail(),
							user.getAvatar(), (int) followRepository.countByFollowingId(user.getId()), isFollowing);
				}).collect(Collectors.toList());
	}

	// ── Post search ───────────────────────────────────────────────
	public List<Map<String, Object>> searchPosts(String query) {
		if (query == null || query.trim().isEmpty())
			return List.of();
		try {
			return postRepository.searchByContentOrTags(query.trim(), PageRequest.of(0, 20)).stream().map(p -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", p.getId());
				m.put("videoUrl", p.getVideoUrl());
				m.put("content", p.getContent() != null ? p.getContent() : "");
				m.put("tags", p.getTags() != null ? p.getTags() : "");
				m.put("thumbnailUrl", p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "");
				return m;
			}).collect(Collectors.toList());
		} catch (Exception e) {
			log.warn("searchPosts error: {}", e.getMessage());
			return List.of();
		}
	}

	// ── Hashtag search ────────────────────────────────────────────
	public List<Map<String, Object>> searchHashtags(String query) {
		if (query == null || query.trim().isEmpty())
			return List.of();
		try {
			return hashtagRepository
					.findByTagContainingIgnoreCaseOrderByPostCountDesc(query.trim(), PageRequest.of(0, 20)).stream()
					.map(h -> {
						Map<String, Object> m = new LinkedHashMap<>();
						m.put("tag", h.getTag());
						m.put("postCount", h.getPostCount());
						return m;
					}).collect(Collectors.toList());
		} catch (Exception e) {
			log.warn("searchHashtags error: {}", e.getMessage());
			return List.of();
		}
	}

	// ── Trending hashtags ─────────────────────────────────────────
	public List<Map<String, Object>> getTrending(int limit) {
		try {
			return hashtagRepository.findTopTrending(PageRequest.of(0, limit)).stream().map(h -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("tag", h.getTag());
				m.put("postCount", h.getPostCount());
				return m;
			}).collect(Collectors.toList());
		} catch (Exception e) {
			return List.of();
		}
	}

	private Long getCurrentUserId() {
		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth == null || !auth.isAuthenticated())
				return null;
			return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}
}
