package com.yogesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

	/*
	 * PRIMARY — used by EngagementService, FanoutService, FeedController,
	 * FeedRefillService, etc. Key: String | Value: String
	 */
	@Bean
	@Primary
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {

		return new StringRedisTemplate(connectionFactory);
	}

	/*
	 * SECONDARY — used by PostCacheService (stores hash objects) Key: String |
	 * Value: JSON Object
	 */
	@Bean("objectRedisTemplate")
	public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {

		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		StringRedisSerializer keySerializer = new StringRedisSerializer();
		RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

		template.setKeySerializer(keySerializer);
		template.setValueSerializer(jsonSerializer);
		template.setHashKeySerializer(keySerializer);
		template.setHashValueSerializer(jsonSerializer);

		template.afterPropertiesSet();
		return template;
	}
}