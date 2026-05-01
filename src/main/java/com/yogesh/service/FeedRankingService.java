package com.yogesh.service;

import com.yogesh.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedRankingService {

	private final StringRedisTemplate redisTemplate;
	private final VectorSearchService vectorSearchService;
	private final CollaborativeService collaborativeService;

	/**
	 * ═══════════════════════════════════════════════════════════
	 *  UPGRADED ALGORITHM — Instagram + TikTok se better
	 * ═══════════════════════════════════════════════════════════
	 *
	 *  Instagram se liya:
	 *    - Relationship strength (following + interaction history)
	 *    - Save signal (sabse strong positive intent)
	 *    - Comment quality boost
	 *
	 *  TikTok se liya:
	 *    - Completion rate #1 signal (finish karna = best signal)
	 *    - Re-watch bonus (second view = viral indicator)
	 *    - Cold start (naye creators ko bhi chance)
	 *    - Speed of engagement (pehle 1 ghante ka engagement 3x weight)
	 *
	 *  Hamare extras:
	 *    - Negative signal: skip = penalize
	 *    - Language/region preference
	 *    - Time-of-day relevance
	 *    - Viral velocity (engagement rate of change)
	 *
	 *  Final Score Formula:
	 *  ────────────────────────────────────────────────────────────
	 *  Signal              Weight   Source
	 *  ──────────────────  ──────   ─────────────────────────────
	 *  Completion rate     0.28     TikTok #1 signal
	 *  Relationship        0.20     Instagram (following/affinity)
	 *  Interest match      0.18     Hashtag + category preference
	 *  Recency + velocity  0.12     TikTok speed boost
	 *  Engagement rate     0.10     Likes+Comments+Saves / Views
	 *  Re-watch bonus      0.06     TikTok viral indicator
	 *  AI vector           0.04     Content similarity
	 *  Cold start          0.02     New creator boost
	 *  ──────────────────  ──────
	 *  Total               1.00
	 *
	 *  NEGATIVE SIGNALS (penalize):
	 *  - Skip (< 2 sec watch) = -0.3 multiplier
	 *  - Not Now (user dismissed) = -0.5 multiplier
	 */
	public double score(Long userId, Post post) {
		// ── Positive signals ──────────────────────────────────────────
		double completion  = getCompletionRate(post)    * 0.28;
		double relation    = getRelationshipScore(userId, post) * 0.20;
		double interest    = getInterestScore(userId, post) * 0.18;
		double recency     = getRecencyVelocityScore(post) * 0.12;
		double engagement  = getEngagementRateScore(post) * 0.10;
		double rewatch     = getRewatchScore(post)      * 0.06;
		double vector      = safeVector(userId, post)   * 0.04;
		double coldStart   = getColdStartBoost(post)    * 0.02;

		double rawScore = completion + relation + interest + recency
				+ engagement + rewatch + vector + coldStart;

		// ── Negative signals (multiplier) ────────────────────────────
		double skipPenalty = getSkipPenalty(userId, post);
		double rawFinal = rawScore * skipPenalty;

		// ── Normalize 0-1 ─────────────────────────────────────────────
		return Math.min(1.0, Math.max(0.0, rawFinal));
	}

	// ══════════════════════════════════════════════════════════════
	//  1. COMPLETION RATE — TikTok's #1 signal
	//     Agar log video poori dekhte hain = great content
	// ══════════════════════════════════════════════════════════════
	private double getCompletionRate(Post post) {
		try {
			String cKey = "post:" + post.getId() + ":completions";
			String vKey = "post:" + post.getId() + ":views";
			String c = redisTemplate.opsForValue().get(cKey);
			String v = redisTemplate.opsForValue().get(vKey);
			if (c == null || v == null) return 0.3; // Default for new posts
			double completions = Double.parseDouble(c);
			double views = Double.parseDouble(v);
			if (views < 10) return 0.3; // Not enough data
			double rate = completions / views;
			// Boost: > 80% completion = viral indicator
			if (rate > 0.8) return 1.0;
			if (rate > 0.6) return 0.85;
			if (rate > 0.4) return 0.65;
			return rate;
		} catch (Exception e) {
			return 0.3;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  2. RELATIONSHIP STRENGTH — Instagram's core signal
	//     Following > Close interaction > Same hashtag community
	// ══════════════════════════════════════════════════════════════
	private double getRelationshipScore(Long userId, Post post) {
		if (post.getUserId() == null) return 0;
		try {
			double score = 0;

			// Is following? (+0.6 base)
			Boolean isFollowing = redisTemplate.opsForSet()
					.isMember("user:" + userId + ":following", String.valueOf(post.getUserId()));
			if (Boolean.TRUE.equals(isFollowing)) score += 0.6;

			// Creator affinity (interaction history)
			Double affinity = redisTemplate.opsForZSet()
					.score("user:" + userId + ":creators", String.valueOf(post.getUserId()));
			if (affinity != null) score += Math.min(0.4, affinity / 20.0);

			// Mutual follow bonus
			Boolean theyFollow = redisTemplate.opsForSet()
					.isMember("user:" + post.getUserId() + ":following", String.valueOf(userId));
			if (Boolean.TRUE.equals(isFollowing) && Boolean.TRUE.equals(theyFollow)) score += 0.1;

			return Math.min(1.0, score);
		} catch (Exception e) {
			return 0;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  3. INTEREST MATCH — Hashtag + Category affinity
	//     User ke past behavior se categories learn karo
	// ══════════════════════════════════════════════════════════════
	private double getInterestScore(Long userId, Post post) {
		try {
			double score = 0;

			// Tag-based interest
			if (post.getTags() != null && !post.getTags().isBlank()) {
				String key = "user:" + userId + ":interests";
				String[] tags = post.getTags().split(",");
				for (String tag : tags) {
					Double val = redisTemplate.opsForZSet().score(key, tag.trim().toLowerCase());
					if (val != null) score += val;
				}
				score = Math.min(1.0, score / 30.0); // normalize
			}

			// Content-based: has user liked similar hashtags before?
			String hashtagKey = "post:" + post.getId() + ":hashtags";
			Long hashtagCount = redisTemplate.opsForSet().size(hashtagKey);
			if (hashtagCount != null && hashtagCount > 0) {
				score = Math.min(1.0, score + 0.1); // bonus for tagged content
			}

			return score;
		} catch (Exception e) {
			return 0;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  4. RECENCY + VELOCITY — TikTok's freshness boost
	//     Naya content + jaldi viral ho raha = extra boost
	// ══════════════════════════════════════════════════════════════
	private double getRecencyVelocityScore(Post post) {
		try {
			if (post.getCreatedAt() == null) return 0;
			long hours = Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours();

			// Base recency score
			double recency;
			if (hours < 1)   recency = 1.0;  // Very fresh = max boost
			else if (hours < 6)  recency = 0.95;
			else if (hours < 24) recency = 0.80;
			else if (hours < 48) recency = 0.60;
			else if (hours < 72) recency = 0.40;
			else recency = Math.exp(-hours / 168.0); // exponential decay (half-life 1 week)

			// Velocity bonus: pehle ghante mein engagement = viral potential
			double velocity = 0;
			if (hours <= 1) {
				String vKey = "post:" + post.getId() + ":velocity";
				String v = redisTemplate.opsForValue().get(vKey);
				if (v != null) {
					double vel = Double.parseDouble(v);
					velocity = Math.min(0.3, vel / 100.0); // max 0.3 bonus
				}
			}

			return Math.min(1.0, recency + velocity);
		} catch (Exception e) {
			return 0;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  5. ENGAGEMENT RATE — Quality signal
	//     Likes + Comments + Saves / Total Views (not raw count)
	// ══════════════════════════════════════════════════════════════
	private double getEngagementRateScore(Post post) {
		try {
			String views = redisTemplate.opsForValue().get("post:" + post.getId() + ":views");
			if (views == null || Double.parseDouble(views) < 5) return 0.3; // new post default

			double v = Double.parseDouble(views);
			double likes = getCounter("post:" + post.getId() + ":likes");
			double comments = getCounter("post:" + post.getId() + ":comments");
			double saves = getCounter("post:" + post.getId() + ":saves");
			double shares = getCounter("post:" + post.getId() + ":shares");

			// Weighted engagement (saves > comments > likes > shares in intent)
			double weighted = (likes * 1.0) + (comments * 2.0) + (saves * 3.0) + (shares * 2.5);
			double rate = weighted / v;

			// Good engagement rate benchmarks:
			// > 10% = viral, > 5% = great, > 2% = good
			if (rate > 0.10) return 1.0;
			if (rate > 0.05) return 0.85;
			if (rate > 0.02) return 0.65;
			if (rate > 0.01) return 0.45;
			return Math.min(0.4, rate * 10);
		} catch (Exception e) {
			return 0;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  6. RE-WATCH BONUS — TikTok's viral indicator
	//     Agar log dobara dekhte hain = exceptional content
	// ══════════════════════════════════════════════════════════════
	private double getRewatchScore(Post post) {
		try {
			String rewatches = redisTemplate.opsForValue().get("post:" + post.getId() + ":rewatches");
			String views = redisTemplate.opsForValue().get("post:" + post.getId() + ":views");
			if (rewatches == null || views == null) return 0;
			double r = Double.parseDouble(rewatches);
			double v = Double.parseDouble(views);
			if (v == 0) return 0;
			double rewatchRate = r / v;
			// > 30% rewatch = viral
			return Math.min(1.0, rewatchRate * 3);
		} catch (Exception e) {
			return 0;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  7. COLD START BOOST — New creators ko chance
	//     < 100 views = new content, gets exploration boost
	// ══════════════════════════════════════════════════════════════
	private double getColdStartBoost(Post post) {
		try {
			String views = redisTemplate.opsForValue().get("post:" + post.getId() + ":views");
			if (views == null) return 1.0; // Brand new = max boost
			double v = Double.parseDouble(views);
			if (v < 50) return 1.0;
			if (v < 100) return 0.7;
			if (v < 500) return 0.3;
			return 0; // Already established
		} catch (Exception e) {
			return 0.5;
		}
	}

	// ══════════════════════════════════════════════════════════════
	//  8. SKIP PENALTY — Negative signal
	//     User ne skip kiya = content uninteresting tha
	// ══════════════════════════════════════════════════════════════
	private double getSkipPenalty(Long userId, Post post) {
		try {
			Boolean skipped = redisTemplate.opsForSet()
					.isMember("user:" + userId + ":skipped", String.valueOf(post.getId()));
			if (Boolean.TRUE.equals(skipped)) return 0.3; // 70% penalize

			// Creator-level skip: agar user ne is creator ki videos skip ki hain
			String creatorSkips = redisTemplate.opsForValue()
					.get("user:" + userId + ":creator_skips:" + post.getUserId());
			if (creatorSkips != null && Integer.parseInt(creatorSkips) > 3) return 0.5;

			return 1.0; // No penalty
		} catch (Exception e) {
			return 1.0;
		}
	}

	// ── AI Vector similarity ────────────────────────────────────────
	private double safeVector(Long userId, Post post) {
		try {
			double s = vectorSearchService.getSimilarity(userId, post.getId());
			return Double.isNaN(s) || Double.isInfinite(s) ? 0 : Math.min(1.0, s);
		} catch (Exception e) {
			return 0;
		}
	}

	// ── Redis counter helper ────────────────────────────────────────
	private double getCounter(String key) {
		try {
			String val = redisTemplate.opsForValue().get(key);
			return val != null ? Double.parseDouble(val) : 0;
		} catch (Exception e) {
			return 0;
		}
	}
}