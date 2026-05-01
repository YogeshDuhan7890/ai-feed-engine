package com.yogesh.util;

/**
 * Central place for Redis key names to avoid mismatches across services/workers.
 */
public final class RedisKeys {
	private RedisKeys() {
	}

	// Trending (raw scores)
	public static final String TRENDING_GLOBAL_ZSET = "trending:global";

	// Cold-start snapshot (ranked list for explore)
	public static final String FEED_GLOBAL_TRENDING_ZSET = "feed:global:trending";

	// Admin hidden posts
	public static final String ADMIN_HIDDEN_POSTS_SET = "admin:hidden:posts";
	public static final String ADMIN_HIDDEN_POST_PREFIX = "admin:hidden:post:";
	public static final String ADMIN_HIDDEN_POST_MIGRATED_FLAG = "admin:hidden:migrated:v1";

	// Admin banned words (content moderation)
	public static final String ADMIN_BANNED_WORDS_LIST = "admin:banned:words";
	public static final String ADMIN_BANNED_WORDS_SET = "admin:banned:words:set";
	public static final String ADMIN_AI_MODERATION_FEATURE = "admin:settings:feature:ai_moderation";

	// OTP security
	public static String otpToken(String email, String purpose) {
		return "otp:" + purpose + ":" + email;
	}

	public static String otpCooldown(String email, String purpose) {
		return "otp:cooldown:" + purpose + ":" + email;
	}
}

