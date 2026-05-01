package com.yogesh.service.impl;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import com.yogesh.config.RequireFeature;
import com.yogesh.service.FeatureFlagService;

@Aspect
@Component
@RequiredArgsConstructor
public class FeatureGuardAspect {

	private final FeatureFlagService featureFlagService;

	@Around("@annotation(requireFeature)")
	public Object checkFeature(ProceedingJoinPoint joinPoint, RequireFeature requireFeature) throws Throwable {

		String key = requireFeature.value();

		if (!featureFlagService.isEnabled(key)) {
			throw new RuntimeException("❌ Feature disabled: " + key);
		}

		return joinPoint.proceed();
	}
}