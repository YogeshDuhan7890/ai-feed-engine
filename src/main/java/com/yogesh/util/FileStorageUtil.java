package com.yogesh.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FileStorageUtil {

	private static final String BASE = "uploads/";
	public static final long MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024L;
	public static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of("jpg", "png", "mp4");
	public static final Set<String> BLOCKED_UPLOAD_EXTENSIONS = Set.of("exe", "php", "js");
	public static final Set<String> IMAGE_UPLOAD_EXTENSIONS = Set.of("jpg", "png");
	public static final Set<String> VIDEO_UPLOAD_EXTENSIONS = Set.of("mp4");
	private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
			"jpg", Set.of("image/jpeg", "image/pjpeg"),
			"png", Set.of("image/png"),
			"mp4", Set.of("video/mp4", "application/mp4"));

	public static final String VIDEO_ORIGINAL = BASE + "videos/original/";
	public static final String AUDIO_EXTRACTED = BASE + "audio/extracted/";
	public static final String AUDIO_HINDI = BASE + "audio/hindi/";
	public static final String AUDIO_ENGLISH = BASE + "audio/english/";
	public static final String THUMBNAILS = BASE + "thumbnails/";
	public static final String HLS = BASE + "hls/";

	public static String generateFileName(String ext) {

		return UUID.randomUUID() + "." + ext;

	}

	public static String generateFileName(MultipartFile file, String fallbackExt) {
		return generateFileName(resolveExtension(file, fallbackExt));
	}

	public static String resolveExtension(MultipartFile file, String fallbackExt) {
		String fallback = sanitizeExtension(fallbackExt);
		if (file == null) {
			return fallback;
		}

		String originalName = file.getOriginalFilename();
		if (originalName != null) {
			int dotIndex = originalName.lastIndexOf('.');
			if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
				String ext = sanitizeExtension(originalName.substring(dotIndex + 1));
				if (!ext.isBlank()) {
					return ext;
				}
			}
		}

		String contentType = file.getContentType();
		if (contentType != null) {
			String normalized = contentType.toLowerCase(Locale.ROOT);
			if (normalized.contains("webm")) return "webm";
			if (normalized.contains("mp4")) return "mp4";
			if (normalized.contains("quicktime")) return "mov";
			if (normalized.contains("ogg")) return "ogg";
			if (normalized.contains("jpeg")) return "jpg";
			if (normalized.contains("png")) return "png";
			if (normalized.contains("gif")) return "gif";
			if (normalized.contains("heic")) return "heic";
		}

		return fallback;
	}

	public static String detectVideoExtension(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}

		try (InputStream in = file.getInputStream()) {
			byte[] header = in.readNBytes(16);
			if (header.length < 4) {
				return null;
			}
			if (isMp4(header)) {
				return "mp4";
			}
			if (isWebm(header)) {
				return "webm";
			}
			if (isQuickTime(header)) {
				return "mov";
			}
			if (isOgg(header)) {
				return "ogg";
			}
			return null;
		} catch (IOException e) {
			throw new RuntimeException("File signature read failed", e);
		}
	}

	public static String validateAllowedUpload(MultipartFile file) {
		return validateAllowedUpload(file, ALLOWED_UPLOAD_EXTENSIONS);
	}

	public static String validateAllowedUpload(MultipartFile file, Set<String> allowedExtensions) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File select karo");
		}
		if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
			throw new IllegalArgumentException("File 10MB se badi nahi ho sakti");
		}

		String extension = getOriginalExtension(file);
		if (extension == null || extension.isBlank()) {
			throw new IllegalArgumentException("File extension missing hai");
		}
		if (BLOCKED_UPLOAD_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("Blocked file type: " + extension);
		}
		if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
			String allowed = allowedExtensions == null ? "" : String.join(", ", allowedExtensions);
			throw new IllegalArgumentException("Allowed file types: " + allowed);
		}

		String mimeType = normalizeContentType(file.getContentType());
		Set<String> expectedMimeTypes = ALLOWED_MIME_TYPES.get(extension);
		if (mimeType == null || expectedMimeTypes == null || !expectedMimeTypes.contains(mimeType)) {
			throw new IllegalArgumentException("Invalid MIME type");
		}

		String signatureExtension = detectAllowedSignatureExtension(file);
		if (signatureExtension == null || !signatureExtension.equals(extension)) {
			throw new IllegalArgumentException("File content type mismatch");
		}

		return extension;
	}

	public static String createDateFolder(String base) {

		try {

			String folder = base + LocalDate.now() + "/";

			Path path = Paths.get(folder);

			if (!Files.exists(path)) {

				Files.createDirectories(path);

			}

			return folder;

		} catch (Exception e) {

			throw new RuntimeException("Folder creation failed", e);

		}

	}

	public static Path ensureDirectory(String folder) {
		try {
			Path path = Paths.get(folder);
			Files.createDirectories(path);
			return path;
		} catch (Exception e) {
			throw new RuntimeException("Folder creation failed", e);
		}
	}

	private static String sanitizeExtension(String ext) {
		if (ext == null) {
			return "bin";
		}
		String clean = ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
		clean = clean.replaceAll("[^a-z0-9]", "");
		return clean.isBlank() ? "bin" : clean;
	}

	private static String getOriginalExtension(MultipartFile file) {
		String originalName = file.getOriginalFilename();
		if (originalName == null) {
			return null;
		}
		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
			return null;
		}
		String extension = sanitizeExtension(originalName.substring(dotIndex + 1));
		return "jpeg".equals(extension) ? "jpg" : extension;
	}

	private static String normalizeContentType(String contentType) {
		if (contentType == null) {
			return null;
		}
		int separator = contentType.indexOf(';');
		String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
		return normalized.trim().toLowerCase(Locale.ROOT);
	}

	private static String detectAllowedSignatureExtension(MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			byte[] header = in.readNBytes(32);
			if (isJpeg(header)) return "jpg";
			if (isPng(header)) return "png";
			if (isMp4(header)) return "mp4";
			return null;
		} catch (IOException e) {
			throw new RuntimeException("File signature read failed", e);
		}
	}

	private static boolean isJpeg(byte[] header) {
		return header.length >= 3
				&& (header[0] & 0xFF) == 0xFF
				&& (header[1] & 0xFF) == 0xD8
				&& (header[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] header) {
		byte[] magic = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
		return header.length >= magic.length && Arrays.equals(Arrays.copyOf(header, magic.length), magic);
	}

	private static boolean isMp4(byte[] header) {
		return header.length >= 8
				&& header[4] == 'f'
				&& header[5] == 't'
				&& header[6] == 'y'
				&& header[7] == 'p';
	}

	private static boolean isWebm(byte[] header) {
		byte[] magic = new byte[] { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 };
		return header.length >= 4 && Arrays.equals(Arrays.copyOf(header, 4), magic);
	}

	private static boolean isQuickTime(byte[] header) {
		return header.length >= 12
				&& header[4] == 'f'
				&& header[5] == 't'
				&& header[6] == 'y'
				&& header[7] == 'p'
				&& header[8] == 'q'
				&& header[9] == 't';
	}

	private static boolean isOgg(byte[] header) {
		return header.length >= 4
				&& header[0] == 'O'
				&& header[1] == 'g'
				&& header[2] == 'g'
				&& header[3] == 'S';
	}

	public static void createDirectories() {

		createDateFolder(VIDEO_ORIGINAL);
		createDateFolder(AUDIO_EXTRACTED);
		createDateFolder(AUDIO_HINDI);
		createDateFolder(AUDIO_ENGLISH);
		createDateFolder(THUMBNAILS);
		createDateFolder(HLS);

	}

}
