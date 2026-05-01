package com.yogesh.service;

import com.yogesh.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

	private final PostRepository postRepository;
	private final EngagementRepository engagementRepository;
	private final FollowRepository followRepository;
	private final BookmarkRepository bookmarkRepository;
	private final CommentRepository commentRepository;

	/**
	 * Creator ka full analytics dashboard data.
	 *
	 * ✅ FIX: row[0] cast safe karo.
	 *
	 * Problem: CAST(createdAt AS date) se PostgreSQL java.sql.Date return karta
	 * hai, lekin code directly row[0].toString() karta tha — null pe NPE possible
	 * tha. Plus getLast7DaysEngagementStats() FUNCTION('DATE',...) fail hota tha →
	 * 500.
	 *
	 * Solution: null check + safe toString().
	 */
	public Map<String, Object> getDashboard(Long userId) {
		Map<String, Object> result = new LinkedHashMap<>();

		// ---- Overview stats ----
		long totalVideos = postRepository.countByUserId(userId);
		long totalFollowers = followRepository.countByFollowingId(userId);
		long totalFollowing = followRepository.countByFollowerId(userId);

		// All my post ids
		List<Long> myPostIds = postRepository.findByUserId(userId).stream().map(p -> p.getId())
				.collect(Collectors.toList());

		long totalLikes = myPostIds.stream().mapToLong(pid -> engagementRepository.countLikes(pid)).sum();
		long totalComments = myPostIds.stream().mapToLong(pid -> engagementRepository.countComments(pid)).sum();
		long totalBookmarks = myPostIds.stream().mapToLong(pid -> bookmarkRepository.countByPostId(pid)).sum();

		Map<String, Object> overview = new LinkedHashMap<>();
		overview.put("totalVideos", totalVideos);
		overview.put("totalFollowers", totalFollowers);
		overview.put("totalFollowing", totalFollowing);
		overview.put("totalLikes", totalLikes);
		overview.put("totalComments", totalComments);
		overview.put("totalBookmarks", totalBookmarks);
		result.put("overview", overview);

		// ---- Last 7 days engagement ----
		// ✅ Uses fixed CAST query — FUNCTION('DATE',...) replaced in
		// EngagementRepository
		LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
		List<Object[]> engStats = engagementRepository.getLast7DaysEngagementStats(sevenDaysAgo);

		List<Map<String, Object>> engagementTrend = engStats.stream().map(row -> {
			Map<String, Object> d = new LinkedHashMap<>();
			// ✅ FIX: null-safe cast — row[0] is java.sql.Date from PostgreSQL CAST
			d.put("date", row[0] != null ? row[0].toString() : "unknown");
			d.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
			return d;
		}).collect(Collectors.toList());
		result.put("engagementTrend", engagementTrend);

		// ---- Top performing posts ----
		List<Map<String, Object>> topPosts = myPostIds.stream().map(pid -> {
			long likes = engagementRepository.countLikes(pid);
			long comments = engagementRepository.countComments(pid);
			long bookmarks = bookmarkRepository.countByPostId(pid);

			Map<String, Object> p = new LinkedHashMap<>();
			p.put("postId", pid);
			p.put("likes", likes);
			p.put("comments", comments);
			p.put("bookmarks", bookmarks);
			p.put("score", likes * 3 + comments * 2 + bookmarks);
			return p;
		}).sorted((a, b) -> Long.compare((long) b.get("score"), (long) a.get("score"))).limit(5)
				.collect(Collectors.toList());

		// Add videoUrl + content to top posts
		topPosts.forEach(tp -> {
			postRepository.findById((Long) tp.get("postId")).ifPresent(post -> {
				tp.put("videoUrl", post.getVideoUrl());
				tp.put("caption", post.getContent());
			});
		});
		result.put("topPosts", topPosts);

		result.put("followerCount", totalFollowers);

		// ---- Shares total ----
		long totalShares = myPostIds.stream().mapToLong(pid -> engagementRepository.countShares(pid)).sum();
		overview.put("totalShares", totalShares);

		// ---- Engagement rate ----
		double engRate = totalVideos > 0
				? Math.round(((double) (totalLikes + totalComments) / Math.max(totalFollowers, 1)) * 10000.0) / 100.0
				: 0.0;
		result.put("engagementRate", engRate + "%");

		return result;
	}

	/** Follower growth — last 30 days */
	public List<Map<String, Object>> getFollowerGrowth(Long userId) {
		try {
			LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
			List<Object[]> rows = followRepository.getFollowerGrowthByDay(userId, thirtyDaysAgo);
			return rows.stream().map(row -> {
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("date", row[0] != null ? row[0].toString() : "");
				d.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
				return d;
			}).collect(Collectors.toList());
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	/** Video performance breakdown */
	public List<Map<String, Object>> getVideoPerformance(Long userId) {
		List<Long> myPostIds = postRepository.findByUserId(userId).stream().map(p -> p.getId())
				.collect(Collectors.toList());
		return myPostIds.stream().map(pid -> {
			long likes = engagementRepository.countLikes(pid);
			long comments = engagementRepository.countComments(pid);
			long shares = engagementRepository.countShares(pid);
			long saves = bookmarkRepository.countByPostId(pid);
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("postId", pid);
			p.put("likes", likes);
			p.put("comments", comments);
			p.put("shares", shares);
			p.put("saves", saves);
			p.put("engagementScore", likes * 1 + comments * 2 + shares * 3 + saves * 3);
			postRepository.findById(pid).ifPresent(post -> {
				p.put("caption", post.getContent() != null ? post.getContent() : "");
				p.put("videoUrl", post.getVideoUrl());
				p.put("thumbnailUrl", post.getThumbnailUrl() != null ? post.getThumbnailUrl() : "");
				p.put("createdAt", post.getCreatedAt());
			});
			return p;
		}).sorted((a, b) -> Long.compare((long) b.get("engagementScore"), (long) a.get("engagementScore")))
				.collect(Collectors.toList());
	}

	/** Single post analytics */
	public Map<String, Object> getPostAnalytics(Long postId, Long userId) {
		var post = postRepository.findById(postId).orElseThrow();
		if (!post.getUserId().equals(userId))
			throw new RuntimeException("Not your post");

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("postId", postId);
		result.put("likes", engagementRepository.countLikes(postId));
		result.put("comments", engagementRepository.countComments(postId));
		result.put("shares", engagementRepository.countShares(postId));
		result.put("bookmarks", bookmarkRepository.countByPostId(postId));
		result.put("caption", post.getContent());
		result.put("videoUrl", post.getVideoUrl());
		result.put("createdAt", post.getCreatedAt());
		return result;
	}
}