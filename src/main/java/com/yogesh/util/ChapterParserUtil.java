package com.yogesh.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChapterParserUtil {

	private ChapterParserUtil() {
	}

	public record Chapter(int timeSeconds, String title) {
	}

	public record ParseResult(String cleanedCaption, List<Chapter> chapters) {
	}

	// Matches:
	//   0:00 Intro
	//   2:30 Main part
	//   01:02:03 Long video
	private static final Pattern CHAPTER_TIME_HHMMSS = Pattern.compile("^(\\d{1,3}):(\\d{2}):(\\d{2})\\s*(.*)$");
	private static final Pattern CHAPTER_TIME_MMSS = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*(.*)$");

	public static ParseResult parseChaptersFromCaption(String caption) {
		if (caption == null) {
			return new ParseResult(null, List.of());
		}

		String raw = caption.trim();
		if (raw.isEmpty()) {
			return new ParseResult(raw, List.of());
		}

		// Split by comma (common: "0:00 Intro, 2:30 Main part")
		String[] parts = raw.split(",");
		List<Chapter> chapters = new ArrayList<>();
		List<String> keptCaptionParts = new ArrayList<>();

		for (String part : parts) {
			String p = part == null ? "" : part.trim();
			if (p.isEmpty()) continue;

			Matcher hh = CHAPTER_TIME_HHMMSS.matcher(p);
			if (hh.matches()) {
				int hhNum = Integer.parseInt(hh.group(1));
				int mm = Integer.parseInt(hh.group(2));
				int ss = Integer.parseInt(hh.group(3));
				int total = hhNum * 3600 + mm * 60 + ss;
				String title = normalizeTitle(hh.group(4));
				chapters.add(new Chapter(total, title.isBlank() ? "Chapter" : title));
				continue;
			}

			Matcher mm = CHAPTER_TIME_MMSS.matcher(p);
			if (mm.matches()) {
				int min = Integer.parseInt(mm.group(1));
				int sec = Integer.parseInt(mm.group(2));
				int total = min * 60 + sec;
				String title = normalizeTitle(mm.group(3));
				chapters.add(new Chapter(total, title.isBlank() ? "Chapter" : title));
				continue;
			}

			// Not a chapter token — keep in caption
			keptCaptionParts.add(p);
		}

		String cleaned = keptCaptionParts.isEmpty() ? "" : String.join(", ", keptCaptionParts);
		return new ParseResult(cleaned, chapters);
	}

	private static String normalizeTitle(String title) {
		if (title == null) return "";
		String t = title.trim();
		// Normalize weird whitespace.
		return t.replaceAll("\\s+", " ");
	}

	public static boolean hasLikelyChapters(String caption) {
		if (caption == null) return false;
		String raw = caption.trim();
		if (raw.isEmpty()) return false;
		// Cheap heuristic: starts with digit then ':' somewhere.
		return raw.matches("^\\d{1,3}:\\d{2}(:\\d{2})?.*");
	}
}

