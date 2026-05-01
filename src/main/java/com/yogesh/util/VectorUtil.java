package com.yogesh.util;

public class VectorUtil {

    public static double cosineSimilarity(String v1, String v2) {

        if (v1 == null || v2 == null)
            return 0;

        String[] a = v1.split(",");
        String[] b = v2.split(",");

        if (a.length != b.length)
            return 0;

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {

            double x = Double.parseDouble(a[i].trim());
            double y = Double.parseDouble(b[i].trim());

            dot += x * y;
            normA += x * x;
            normB += y * y;
        }

        if (normA == 0 || normB == 0)
            return 0;

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}