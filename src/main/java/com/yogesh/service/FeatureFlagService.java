package com.yogesh.service;

public interface FeatureFlagService {
	boolean isEnabled(String key);

	boolean isEnabledForUser(String key, String uid);

	int getInt(String key, int def);

	String getString(String key, String def);
}