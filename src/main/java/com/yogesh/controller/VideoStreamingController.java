package com.yogesh.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/stream")
public class VideoStreamingController {

	@Value("${upload.path}")
	private String uploadPath;

	/*
	 * ======================== HLS STREAMING (.m3u8 / .ts) ========================
	 */
	@GetMapping("/hls")
	public ResponseEntity<?> streamHLS(@RequestParam String path) {
		// FIX: ResponseEntity<?> — wildcard taaki exception handler conflict na kare
		try {
			File file = resolveSecure(path);
			if (file == null || !file.exists())
				return ResponseEntity.notFound().build();

			MediaType type;
			if (path.endsWith(".m3u8"))
				type = MediaType.valueOf("application/vnd.apple.mpegurl");
			else if (path.endsWith(".ts"))
				type = MediaType.valueOf("video/mp2t");
			else
				type = MediaType.APPLICATION_OCTET_STREAM;

			return ResponseEntity.ok().contentType(type).body(new FileSystemResource(file));
		} catch (Exception e) {
			log.error("HLS stream error: {}", e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	/*
	 * ======================== DIRECT MP4 STREAMING ========================
	 */
	@GetMapping("/video")
	public ResponseEntity<?> streamVideo(@RequestParam String path) {
		try {
			File file = resolveSecure(path);
			if (file == null || !file.exists())
				return ResponseEntity.notFound().build();

			return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4")).contentLength(file.length())
					.header(HttpHeaders.ACCEPT_RANGES, "bytes").body(new FileSystemResource(file));
		} catch (Exception e) {
			log.error("Video stream error: {}", e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	/*
	 * ======================== PATH TRAVERSAL PREVENTION ========================
	 */
	private File resolveSecure(String requestedPath) {
		try {
			String cleaned = requestedPath.startsWith("/") ? requestedPath.substring(1) : requestedPath;

			// uploadPath exist karta hai toh wahan se resolve karo
			Path base = Paths.get(uploadPath);
			if (Files.exists(base)) {
				Path resolved = base.toRealPath().resolve(cleaned).normalize();
				if (!resolved.startsWith(base.toRealPath()))
					return null;
				return resolved.toFile();
			}

			// Fallback: direct path
			return new File(requestedPath);
		} catch (Exception e) {
			return new File(requestedPath);
		}
	}
}