package com.yogesh.service;

import com.yogesh.util.RedisKeys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

	private static final long BANNED_WORDS_CACHE_REFRESH_MS = 5 * 60 * 1000L;

	private static final List<String> DEFAULT_BANNED_WORDS = List.of(
			"hate", "abuse", "kill", "stupid", "idiot", "slur", "terror", "bomb");

	private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d[\\d\\s-]{8,}\\d)");
	private static final Pattern LONG_REPEAT_PATTERN = Pattern.compile("(.)\\1{5,}");
	private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[^a-z0-9]+");

	private final StringRedisTemplate redis;
	private final OpenAiService openAiService;

	private volatile Set<String> bannedWordsCache = null;
	private volatile Set<String> lastGoodBannedWords = null;
	private volatile long bannedWordsLoadedAt = 0;

	public ModerationResult reviewText(String text) {
		return reviewText(text, "Content");
	}

	public ModerationResult reviewText(String text, String label) {
		String normalized = normalize(text);
		List<String> reasons = new ArrayList<>();
		Set<String> matchedTerms = new LinkedHashSet<>();
		double score = 0;

		if (normalized.isBlank()) {
			return new ModerationResult(true, 0, List.of(), List.of(), normalized);
		}

		Set<String> bannedWords = getBannedWordsCached();
		for (String token : WORD_SPLIT_PATTERN.split(normalized)) {
			if (token == null || token.isBlank()) {
				continue;
			}
			if (bannedWords.contains(token)) {
				matchedTerms.add(token);
			}
		}

		if (!matchedTerms.isEmpty()) {
			score += Math.min(0.9, matchedTerms.size() * 0.35);
			reasons.add(label + " mein restricted words mile: " + String.join(", ", matchedTerms));
		}

		if (LONG_REPEAT_PATTERN.matcher(normalized).find()) {
			score += 0.2;
			reasons.add(label + " mein repeated spam pattern mila");
		}

		int urlHits = countMatches(URL_PATTERN, text == null ? "" : text);
		if (urlHits > 2) {
			score += 0.2;
			reasons.add(label + " mein bahut zyada links hain");
		}

		if (PHONE_PATTERN.matcher(text == null ? "" : text).find()) {
			score += 0.15;
			reasons.add(label + " mein direct contact info mila");
		}

		long upperCaseCount = text == null ? 0 : text.chars().filter(Character::isUpperCase).count();
		if (upperCaseCount >= 16) {
			score += 0.1;
			reasons.add(label + " all-caps spam jaisa lag raha hai");
		}

		boolean blocked = !matchedTerms.isEmpty() || score >= 0.95;

		OpenAiService.AiModerationVerdict aiVerdict = tryAiModeration(text, label);
		if (aiVerdict != null) {
			if (aiVerdict.flagged()) {
				score = Math.max(score, Math.max(0.70, aiVerdict.score()));
				reasons.add(label + " AI moderation flag: "
						+ (aiVerdict.reason() == null || aiVerdict.reason().isBlank()
								? "suspicious content"
								: aiVerdict.reason()));
			}
			blocked = blocked || aiVerdict.flagged() || score >= 0.95;
		}

		return new ModerationResult(!blocked, Math.min(score, 1.0), new ArrayList<>(matchedTerms), reasons, normalized);
	}

	public void assertAllowed(String text, String label) {
		ModerationResult result = reviewText(text, label);
		if (!result.isAllowed()) {
			String detail = result.getReasons().isEmpty()
					? label + " moderation check pass nahi kar paya"
					: result.getReasons().get(0);
			throw new IllegalArgumentException(detail);
		}
	}

	private Set<String> getBannedWordsCached() {
		long now = System.currentTimeMillis();
		Set<String> cached = bannedWordsCache;
		if (cached != null && (now - bannedWordsLoadedAt) < BANNED_WORDS_CACHE_REFRESH_MS) {
			return cached;
		}

		synchronized (this) {
			// Re-check inside lock
			cached = bannedWordsCache;
			if (cached != null && (now - bannedWordsLoadedAt) < BANNED_WORDS_CACHE_REFRESH_MS) {
				return cached;
			}

			Set<String> merged = new LinkedHashSet<>(DEFAULT_BANNED_WORDS);

			try {
				// Prefer Redis Set for fast membership (fallback to list once).
				Set<String> storedSet = redis.opsForSet().members(RedisKeys.ADMIN_BANNED_WORDS_SET);
				if (storedSet != null && !storedSet.isEmpty()) {
					for (String word : storedSet) {
						String normalizedWord = normalizeWord(word);
						if (!normalizedWord.isBlank()) {
							merged.add(normalizedWord);
						}
					}
				} else {
					List<String> storedList = redis.opsForList().range(RedisKeys.ADMIN_BANNED_WORDS_LIST, 0, -1);
					if (storedList != null) {
						for (String word : storedList) {
							String normalizedWord = normalizeWord(word);
							if (!normalizedWord.isBlank()) {
								merged.add(normalizedWord);
							}
						}
					}

					// Best-effort: seed the set so later checks stay O(1)
					if (!merged.isEmpty()) {
						try {
							redis.opsForSet().add(RedisKeys.ADMIN_BANNED_WORDS_SET, merged.toArray(new String[0]));
						} catch (Exception ignored) {
						}
					}
				}
			} catch (Exception e) {
				// Redis down ho toh service degrade na ho: last successful rules continue rakho.
				log.warn("Moderation banned words refresh failed, using last known cache: {}", e.getMessage());
				Set<String> fallback = lastGoodBannedWords;
				if (fallback != null && !fallback.isEmpty()) {
					bannedWordsCache = fallback;
					bannedWordsLoadedAt = now;
					return fallback;
				}
			}

			Set<String> finalSet = merged.isEmpty() ? Collections.emptySet() : merged;
			bannedWordsCache = finalSet;
			lastGoodBannedWords = finalSet;
			bannedWordsLoadedAt = now;
			return finalSet;
		}
	}

	public double scoreText(String text) {
		return reviewText(text).getScore();
	}

	private int countMatches(Pattern pattern, String text) {
		int count = 0;
		Matcher matcher = pattern.matcher(text == null ? "" : text);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private String normalize(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
	}

	private String normalizeWord(String word) {
		return normalize(word).replaceAll("\\s+", " ");
	}

	private OpenAiService.AiModerationVerdict tryAiModeration(String text, String label) {
		if (!isAiModerationEnabled()) {
			return null;
		}
		return openAiService.moderateText(text, label);
	}

	private boolean isAiModerationEnabled() {
		try {
			String enabled = redis.opsForValue().get(RedisKeys.ADMIN_AI_MODERATION_FEATURE);
			return "true".equalsIgnoreCase(enabled) && openAiService.isConfigured();
		} catch (Exception e) {
			return false;
		}
	}

	@Getter
	public static class ModerationResult {
		private final boolean allowed;
		private final double score;
		private final List<String> matchedTerms;
		private final List<String> reasons;
		private final String normalizedText;

		public ModerationResult(boolean allowed, double score, List<String> matchedTerms, List<String> reasons,
				String normalizedText) {
			this.allowed = allowed;
			this.score = score;
			this.matchedTerms = matchedTerms;
			this.reasons = reasons;
			this.normalizedText = normalizedText;
		}
	}
}
