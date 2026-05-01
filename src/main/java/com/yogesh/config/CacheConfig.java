package com.yogesh.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;

import static com.yogesh.config.RedisCachePolicy.*;

@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

		// Default cache config
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(DEFAULT_TTL)
				.disableCachingNullValues()
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer()));

		// Per-cache TTL config
		Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

		// Analytics — 10 min (changes slowly)
		cacheConfigs.put(ANALYTICS, defaultConfig.entryTtl(ANALYTICS_TTL));

		// User profiles — 5 min
		cacheConfigs.put(PROFILES, defaultConfig.entryTtl(PROFILES_TTL));

		// Trending hashtags — 15 min
		cacheConfigs.put(TRENDING, defaultConfig.entryTtl(TRENDING_TTL));

		// Search results — 2 min
		cacheConfigs.put(SEARCH, defaultConfig.entryTtl(SEARCH_TTL));

		// Suggested users — 10 min
		cacheConfigs.put(SUGGESTED, defaultConfig.entryTtl(SUGGESTED_TTL));

		// Post details — 5 min
		cacheConfigs.put(POSTS, defaultConfig.entryTtl(POSTS_TTL));

		// Comment counts — 2 min
		cacheConfigs.put(COMMENT_COUNTS, defaultConfig.entryTtl(COMMENT_COUNTS_TTL));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(cacheConfigs).build();
	}
}
