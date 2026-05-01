package com.yogesh.admin.service;

public interface AbTestService {
	String assignVariant(String testId, String userId);

	void trackExposure(String testId, String userId, String variant);

	void trackConversion(String testId, String userId, String variant);

	java.util.Map<String, Object> getResults(String testId);

	java.util.Map<String, Object> chooseWinner(String testId, double minLiftPct, long minSamplePerVariant);
}