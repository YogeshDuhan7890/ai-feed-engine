package com.yogesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════ FREE AI SERVICE —
 * Zero cost, no credit card
 * ═══════════════════════════════════════════════════════════
 *
 * Speech to Text → Groq API (Whisper free tier) Free: 28,800 sec audio/day Key:
 * console.groq.com (free signup)
 *
 * Translation → MyMemory API (completely free) Free: 5000 words/day, no key
 * needed
 *
 * Caption/Tags → Groq API (Llama3 free tier) Free: 14,400 requests/day
 *
 * Text to Speech → ResponsiveVoice / Web Speech API Free: browser-side TTS (no
 * server cost)
 *
 * Setup — application-dev.yml mein sirf yeh daalo: ai: groq-api-key:
 * gsk_XXXXXXXXXXXXXXXX ← groq.com se free
 *
 * Groq signup: console.groq.com → Free account → API Keys
 * ═══════════════════════════════════════════════════════════
 */
@Slf4j
@Service
public class OpenAiService {

	@Value("${ai.groq-api-key:}")
	private String groqApiKey;

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final String GROQ_BASE = "https://api.groq.com/openai/v1";
	private static final String MYMEMORY = "https://api.mymemory.translated.net/get";
	private static final String BOUNDARY = "----AiFeedBoundary";

	// ══════════════════════════════════════════════════════════════
	// 1. SPEECH TO TEXT — Groq Whisper (FREE)
	// 28,800 sec audio/day free
	// ══════════════════════════════════════════════════════════════
	public String speechToText(String audioFilePath) {
		if (!isConfigured()) {
			log.info("Groq not configured — speechToText skipped");
			return "";
		}
		try {
			Path audioPath = Paths.get(audioFilePath);
			if (!Files.exists(audioPath)) {
				log.warn("Audio not found: {}", audioFilePath);
				return "";
			}

			byte[] audioBytes = Files.readAllBytes(audioPath);
			String fileName = audioPath.getFileName().toString();

			byte[] body = buildMultipart(Map.of("model", "whisper-large-v3-turbo", "response_format", "text"), "file",
					fileName, audioBytes);

			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(GROQ_BASE + "/audio/transcriptions"))
					.timeout(REQUEST_TIMEOUT)
					.header("Authorization", "Bearer " + groqApiKey)
					.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
					.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 200) {
				String transcript = resp.body().trim();
				log.info("Groq transcript: {} chars", transcript.length());
				return transcript;
			}
			log.error("Groq Whisper error {}: {}", resp.statusCode(), resp.body());
			return "";
		} catch (Exception e) {
			log.error("speechToText error: {}", e.getMessage());
			return "";
		}
	}

	// ══════════════════════════════════════════════════════════════
	// 2. TRANSLATION — MyMemory API (FREE, no key needed)
	// 5000 words/day free, supports Hindi perfectly
	// ══════════════════════════════════════════════════════════════
	public String translate(String text, String targetLang) {
		if (text == null || text.isBlank())
			return text;
		try {
			// MyMemory lang codes: hi|en, en|hi
			String langPair = "en|" + targetLang;
			if ("en".equals(targetLang))
				langPair = "hi|en";

			// Split long text into chunks (MyMemory 500 char limit per request)
			if (text.length() > 500) {
				return translateChunked(text, langPair);
			}

			String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
			String url = MYMEMORY + "?q=" + encoded + "&langpair=" + langPair;

			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 200) {
				String translated = extractMyMemory(resp.body());
				log.info("Translated to {}: {} chars", targetLang, translated.length());
				return translated;
			}
			log.warn("MyMemory error {}", resp.statusCode());
			return text;
		} catch (Exception e) {
			log.error("translate error: {}", e.getMessage());
			return text;
		}
	}

	// Chunked translation for long text
	private String translateChunked(String text, String langPair) {
		try {
			StringBuilder result = new StringBuilder();
			// Split by sentences
			String[] sentences = text.split("(?<=[.!?।])\\s+");
			StringBuilder chunk = new StringBuilder();

			for (String sentence : sentences) {
				if (chunk.length() + sentence.length() > 450) {
					if (chunk.length() > 0) {
						result.append(translateSingle(chunk.toString().trim(), langPair)).append(" ");
						chunk = new StringBuilder();
					}
				}
				chunk.append(sentence).append(" ");
			}
			if (chunk.length() > 0) {
				result.append(translateSingle(chunk.toString().trim(), langPair));
			}
			return result.toString().trim();
		} catch (Exception e) {
			return text;
		}
	}

	private String translateSingle(String text, String langPair) throws Exception {
		String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
		String url = MYMEMORY + "?q=" + encoded + "&langpair=" + langPair;
		HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() == 200)
			return extractMyMemory(resp.body());
		return text;
	}

	// ══════════════════════════════════════════════════════════════
	// 3. AUTO CAPTION + HASHTAGS — Groq Llama3 (FREE)
	// 14,400 requests/day free
	// ══════════════════════════════════════════════════════════════
	public Map<String, String> generateCaption(String transcript) {
		if (transcript == null || transcript.isBlank())
			return Map.of("caption", "", "tags", "");

		// If Groq not configured — use smart local generation
		if (!isConfigured()) {
			return generateLocalCaption(transcript);
		}

		try {
			String prompt = "Video transcript: \"" + transcript.substring(0, Math.min(400, transcript.length()))
					+ "\"\n\n" + "Generate:\n1. Catchy caption (Hindi ya English, max 80 chars)\n"
					+ "2. 5 hashtags (comma separated, no #)\n\n"
					+ "Reply ONLY in JSON: {\"caption\":\"...\",\"tags\":\"tag1,tag2,tag3,tag4,tag5\"}";

			String result = groqChat(prompt, "llama3-8b-8192", 150);
			String caption = extractJson(result, "caption");
			String tags = extractJson(result, "tags");

			if (caption.isBlank())
				return generateLocalCaption(transcript);
			return Map.of("caption", caption, "tags", tags);
		} catch (Exception e) {
			log.error("generateCaption error: {}", e.getMessage());
			return generateLocalCaption(transcript);
		}
	}

	// ── Local caption generation (no API needed) ──────────────────
	private Map<String, String> generateLocalCaption(String transcript) {
		// Extract first meaningful sentence as caption
		String caption = transcript.length() > 80 ? transcript.substring(0, 77) + "..." : transcript;

		// Extract keywords as tags
		String[] words = transcript.toLowerCase().replaceAll("[^a-zA-Z\\s]", "").split("\\s+");
		Set<String> stopWords = Set.of("the", "a", "an", "is", "are", "was", "were", "be", "been", "have", "has", "had",
				"do", "does", "did", "will", "would", "could", "should", "may", "might", "shall", "can", "need", "dare",
				"ought", "used", "to", "of", "in", "on", "at", "by", "for", "with", "about", "against", "between",
				"into", "through", "during", "yeh", "hai", "mein", "ka", "ki", "ke", "se", "ko", "ne", "par", "aur",
				"ek", "bhi", "kuch");

		List<String> tags = new ArrayList<>();
		Map<String, Integer> freq = new LinkedHashMap<>();
		for (String w : words) {
			if (w.length() > 3 && !stopWords.contains(w)) {
				freq.merge(w, 1, Integer::sum);
			}
		}
		freq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
				.forEach(e -> tags.add(e.getKey()));

		tags.addAll(List.of("viral", "trending"));
		return Map.of("caption", caption, "tags", String.join(",", tags.subList(0, Math.min(8, tags.size()))));
	}

	// ══════════════════════════════════════════════════════════════
	// 4. TEXT TO SPEECH — Google Translate TTS (FREE)
	// Uses Google's public TTS endpoint (no key needed)
	// Note: For production use gTTS python or browser TTS
	// ══════════════════════════════════════════════════════════════
	public String textToSpeech(String text, String outputPath) {
		if (text == null || text.isBlank())
			return null;
		try {
			// Google Translate TTS (public, free, unofficial)
			// Max 200 chars per request
			String chunk = text.length() > 200 ? text.substring(0, 200) : text;
			String encoded = URLEncoder.encode(chunk, StandardCharsets.UTF_8);
			String lang = outputPath.contains("hindi") || outputPath.contains("/hi/") ? "hi" : "en";
			String url = "https://translate.google.com/translate_tts?ie=UTF-8&q=" + encoded + "&tl=" + lang
					+ "&client=tw-ob";

			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).header("User-Agent", "Mozilla/5.0").GET()
					.build();

			HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
			if (resp.statusCode() == 200 && resp.body().length > 100) {
				Files.createDirectories(Paths.get(outputPath).getParent());
				Files.write(Paths.get(outputPath), resp.body());
				log.info("TTS saved: {}", outputPath);
				return outputPath;
			}
			log.warn("TTS failed status={}", resp.statusCode());
			return null;
		} catch (Exception e) {
			log.error("textToSpeech error: {}", e.getMessage());
			return null;
		}
	}

	// ── Groq Chat Completion ──────────────────────────────────────
	private String groqChat(String prompt, String model, int maxTokens) throws Exception {
		String body = String.format(
				"{\"model\":\"%s\",\"max_tokens\":%d,\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}", model,
				maxTokens, esc(prompt));

		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(GROQ_BASE + "/chat/completions"))
				.timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + groqApiKey).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();

		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() == 200) {
			JsonNode root = objectMapper.readTree(resp.body());
			return root.at("/choices/0/message/content").asText("");
		}
		log.error("Groq chat error {}: {}", resp.statusCode(), resp.body());
		return "";
	}

	// ── MyMemory JSON extract ─────────────────────────────────────
	private String extractMyMemory(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			return root.at("/responseData/translatedText").asText("");
		} catch (Exception e) {
			return "";
		}
	}

	// ── Build multipart ───────────────────────────────────────────
	private byte[] buildMultipart(Map<String, String> fields, String fileField, String fileName, byte[] fileBytes)
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String nl = "\r\n";
		for (Map.Entry<String, String> e : fields.entrySet()) {
			out.write(("--" + BOUNDARY + nl).getBytes());
			out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + nl + nl).getBytes());
			out.write((e.getValue() + nl).getBytes());
		}
		out.write(("--" + BOUNDARY + nl).getBytes());
		out.write(("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"" + nl)
				.getBytes());
		out.write(("Content-Type: audio/mpeg" + nl + nl).getBytes());
		out.write(fileBytes);
		out.write(nl.getBytes());
		out.write(("--" + BOUNDARY + "--" + nl).getBytes());
		return out.toByteArray();
	}

	// ── JSON extract helper ───────────────────────────────────────
	private String extractJson(String json, String field) {
		try {
			if (json == null || json.isBlank() || field == null || field.isBlank()) {
				return "";
			}

			// Groq sometimes wraps JSON with extra text; safely extract the first JSON object.
			int start = json.indexOf('{');
			int end = json.lastIndexOf('}');
			if (start < 0 || end <= start) {
				return "";
			}

			JsonNode root = objectMapper.readTree(json.substring(start, end + 1));
			JsonNode node = root.get(field);
			if (node == null) {
				return "";
			}

			if (node.isTextual()) {
				return node.asText("");
			}
			return node.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String esc(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
				"\\t");
	}

	// ══════════════════════════════════════════════════════════════
	// 5. DETECT LANGUAGE — Hindi Unicode check (no API needed)
	// ══════════════════════════════════════════════════════════════
	public String detectLanguage(String text) {
		if (text == null || text.isBlank())
			return "en";
		long hindiChars = text.chars().filter(c -> c >= 0x0900 && c <= 0x097F).count();
		return hindiChars > text.length() * 0.3 ? "hi" : "en";
	}

	// ── isWhisperAvailable — Groq configured hai? ─────────────────
	public boolean isWhisperAvailable() {
		return isConfigured(); // Groq Whisper available iff API key set
	}

	// ── isTranslationAvailable — MyMemory always free ────────────
	public boolean isTranslationAvailable() {
		return true; // MyMemory needs no key — always available
	}

	public boolean isConfigured() {
		return groqApiKey != null && !groqApiKey.isBlank();
	}

	/**
	 * Lightweight moderation check using Groq chat model.
	 * Returns null when AI moderation is unavailable or parsing fails.
	 */
	public AiModerationVerdict moderateText(String text, String label) {
		if (!isConfigured() || text == null || text.isBlank()) {
			return null;
		}
		try {
			String safeLabel = (label == null || label.isBlank()) ? "content" : label;
			String snippet = text.substring(0, Math.min(500, text.length()));
			String prompt = "Analyze if this " + safeLabel + " is toxic/abusive/hate/spam/sexual/violent.\n"
					+ "Return ONLY strict JSON with keys: flagged(boolean), score(number 0-1), reason(string).\n"
					+ "Text: \"" + snippet + "\"";

			String raw = groqChat(prompt, "llama3-8b-8192", 120);
			String flaggedRaw = extractJson(raw, "flagged");
			String scoreRaw = extractJson(raw, "score");
			String reason = extractJson(raw, "reason");

			boolean flagged = "true".equalsIgnoreCase(flaggedRaw) || "1".equals(flaggedRaw);
			double score = 0.0;
			try {
				score = Double.parseDouble(scoreRaw);
			} catch (Exception ignored) {
			}
			score = Math.max(0.0, Math.min(1.0, score));
			return new AiModerationVerdict(flagged, score, reason == null ? "" : reason);
		} catch (Exception e) {
			log.warn("AI moderation failed: {}", e.getMessage());
			return null;
		}
	}

	public record AiModerationVerdict(boolean flagged, double score, String reason) {
	}
}
