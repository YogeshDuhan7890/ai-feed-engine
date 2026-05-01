package com.yogesh.util;

public class ScoreUtil {

    private static final double MULTIPLIER = 1_000_000;

    private ScoreUtil() {}

    public static double buildCompositeScore(double finalScore, long postId) {
        return (finalScore * MULTIPLIER) + postId;
    }

    public static double extractBaseScore(double compositeScore) {
        return compositeScore / MULTIPLIER;
    }
}