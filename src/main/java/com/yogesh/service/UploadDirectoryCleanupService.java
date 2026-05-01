package com.yogesh.service;

import com.yogesh.util.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
@Slf4j
public class UploadDirectoryCleanupService {

	private static final Path ROOT = Paths.get("uploads");

	@Scheduled(fixedDelay = 21600000)
	public void cleanupEmptyFolders() {
		try {
			Files.createDirectories(ROOT);
			try (Stream<Path> walk = Files.walk(ROOT)) {
				walk.filter(Files::isDirectory)
						.sorted(Comparator.reverseOrder())
						.forEach(this::deleteIfEmpty);
			}
		} catch (IOException e) {
			log.warn("Upload directory cleanup failed: {}", e.getMessage());
		}
	}

	@Scheduled(fixedDelay = 43200000, initialDelay = 15000)
	public void ensureBaseFolders() {
		try {
			Files.createDirectories(ROOT);
			FileStorageUtil.createDirectories();
		} catch (Exception e) {
			log.warn("Upload base folder ensure failed: {}", e.getMessage());
		}
	}

	private void deleteIfEmpty(Path directory) {
		if (directory == null || ROOT.equals(directory)) {
			return;
		}
		try (Stream<Path> children = Files.list(directory)) {
			if (!children.findAny().isPresent()) {
				Files.deleteIfExists(directory);
			}
		} catch (IOException ignored) {
			// Non-empty or temporarily locked directories can be skipped safely.
		}
	}
}
