package com.yogesh.controller;

import com.yogesh.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

	private final OpenAiService openAiService;

	/** GET /api/ai/status */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of("translationAvailable", openAiService.isTranslationAvailable(), "whisperAvailable",
				openAiService.isWhisperAvailable(), "provider",
				Map.of("translation", "MyMemory (Free, no key needed)", "speechToText",
						"Whisper local (pip install openai-whisper)", "tts", "Browser Web Speech API (frontend)",
						"caption", "Rule-based (no API)"));
	}

	/** POST /api/ai/generate-caption */
	@PostMapping("/generate-caption")
	public Map<String, Object> generateCaption(@RequestBody Map<String, String> body) {
		String input = body.getOrDefault("transcript", body.getOrDefault("topic", ""));
		if (input.isBlank())
			return Map.of("success", false, "message", "Text daalo");

		Map<String, String> result = openAiService.generateCaption(input);
		return Map.of("success", true, "caption", result.getOrDefault("caption", ""), "tags",
				result.getOrDefault("tags", ""));
	}

	/** POST /api/ai/translate */
	@PostMapping("/translate")
	public Map<String, Object> translate(@RequestBody Map<String, String> body) {
		String text = body.getOrDefault("text", "");
		String lang = body.getOrDefault("lang", "en");
		if (text.isBlank())
			return Map.of("success", false, "message", "Text daalo");

		String translated = openAiService.translate(text, lang);
		return Map.of("success", true, "translated", translated, "lang", lang);
	}

	/** POST /api/ai/detect-language */
	@PostMapping("/detect-language")
	public Map<String, Object> detectLanguage(@RequestBody Map<String, String> body) {
		String text = body.getOrDefault("text", "");
		String lang = openAiService.detectLanguage(text);
		return Map.of("lang", lang, "name", "hi".equals(lang) ? "Hindi" : "English");
	}
}