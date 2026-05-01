package com.yogesh.config;

import java.time.Duration;

/**
 * One place for Redis cache TTLs and cache names.
 */
public final class RedisCachePolicy {
	private RedisCachePolicy() {
	}

	public static final String ANALYTICS = "analytics";
	public static final String PROFILES = "profiles";
	public static final String TRENDING = "trending";
	public static final String SEARCH = "search";
	public static final String SUGGESTED = "suggested";
	public static final String POSTS = "posts";
	public static final String COMMENT_COUNTS = "commentCounts";

	public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
	public static final Duration ANALYTICS_TTL = Duration.ofMinutes(10);
	public static final Duration PROFILES_TTL = Duration.ofMinutes(5);
	public static final Duration TRENDING_TTL = Duration.ofMinutes(15);
	public static final Duration SEARCH_TTL = Duration.ofMinutes(2);
	public static final Duration SUGGESTED_TTL = Duration.ofMinutes(10);
	public static final Duration POSTS_TTL = Duration.ofMinutes(30);
	public static final Duration COMMENT_COUNTS_TTL = Duration.ofMinutes(2);
	public static final Duration POST_ENGAGEMENT_TTL = Duration.ofMinutes(5);
	public static final Duration UPLOAD_STATUS_TTL = Duration.ofMinutes(10);

	public static final Duration MOBILE_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
	public static final int MOBILE_RATE_LIMIT_PER_WINDOW = 60;

	public static final Duration ENGAGEMENT_RATE_LIMIT_WINDOW = Duration.ofSeconds(10);
	public static final int ENGAGEMENT_RATE_LIMIT_PER_WINDOW = 20;
}
