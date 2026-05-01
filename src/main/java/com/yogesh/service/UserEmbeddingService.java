package com.yogesh.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserEmbeddingService {

	private final StringRedisTemplate stringRedisTemplate;
	private final RedisTemplate<String, Object> objectRedisTemplate;

	// FIX: post:embedding hash objectRedisTemplate mein hai
	// user:vector StringRedisTemplate mein plain string hai
	public UserEmbeddingService(StringRedisTemplate stringRedisTemplate,
			@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.objectRedisTemplate = objectRedisTemplate;
	}

	public void updateUserVector(Long userId) {

		Set<String> watchedPosts = stringRedisTemplate.opsForSet().members("user:" + userId + ":watched");

		if (watchedPosts == null || watchedPosts.isEmpty())
			return;

		List<double[]> vectors = new ArrayList<>();

		for (String postId : watchedPosts) {
			// FIX: objectRedisTemplate se embedding fetch karo
			Object embedding = objectRedisTemplate.opsForHash().get("post:embedding", postId);

			if (embedding == null)
				continue;

			try {
				vectors.add(parseVector(embedding.toString()));
			} catch (NumberFormatException ignored) {
				// Corrupt embedding — skip
			}
		}

		if (vectors.isEmpty())
			return;

		double[] avg = average(vectors);

		stringRedisTemplate.opsForValue().set("user:" + userId + ":vector", vectorToString(avg));
	}

	private double[] parseVector(String str) {
		String[] parts = str.split(",");
		double[] v = new double[parts.length];
		for (int i = 0; i < parts.length; i++)
			v[i] = Double.parseDouble(parts[i].trim());
		return v;
	}

	private double[] average(List<double[]> vectors) {
		int size = vectors.get(0).length;
		double[] avg = new double[size];
		for (double[] v : vectors)
			for (int i = 0; i < size; i++)
				avg[i] += v[i];
		for (int i = 0; i < size; i++)
			avg[i] /= vectors.size();
		return avg;
	}

	private String vectorToString(double[] v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.length; i++) {
			sb.append(v[i]);
			if (i < v.length - 1)
				sb.append(",");
		}
		return sb.toString();
	}
}