package com.yogesh.migration;

import com.yogesh.util.RedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * One-time migration to convert legacy hidden-post keys:
 *   admin:hidden:post:{postId}  -> admin:hidden:posts (set)
 *
 * This avoids runtime `keys()` scans in hot paths.
 */
@Component
@RequiredArgsConstructor
public class HiddenPostKeyMigrator {

	private final StringRedisTemplate redisTemplate;

	@PostConstruct
	public void migrateHiddenPostsOnce() {
		try {
			if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.ADMIN_HIDDEN_POST_MIGRATED_FLAG))) {
				return;
			}

			String prefix = RedisKeys.ADMIN_HIDDEN_POST_PREFIX;
			String matchPattern = prefix + "*";

			Set<String> migratedIds = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
				Set<String> ids = new HashSet<>();
				Cursor<byte[]> cursor = connection.scan(
						ScanOptions.scanOptions().match(matchPattern).count(1000).build());
				while (cursor.hasNext()) {
					byte[] rawKey = cursor.next();
					if (rawKey == null || rawKey.length == 0) {
						continue;
					}
					String key = new String(rawKey, StandardCharsets.UTF_8);
					if (!key.startsWith(prefix)) {
						continue;
					}
					String id = key.substring(prefix.length());
					if (!id.isBlank()) {
						ids.add(id);
					}
				}
				return ids;
			});

			if (migratedIds != null && !migratedIds.isEmpty()) {
				redisTemplate.opsForSet().add(RedisKeys.ADMIN_HIDDEN_POSTS_SET, migratedIds.toArray(new String[0]));
			}

			redisTemplate.opsForValue().set(RedisKeys.ADMIN_HIDDEN_POST_MIGRATED_FLAG, "1");
		} catch (Exception ignored) {
			// If migration fails, app still works with "new" set-based hides.
		}
	}
}

