package com.yogesh.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStorageUtilTest {

	@Test
	void detectsMp4ByMagicBytes() {
		byte[] mp4 = new byte[] { 0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2' };
		MockMultipartFile file = new MockMultipartFile("file", "clip.bin", "video/mp4", mp4);

		assertEquals("mp4", FileStorageUtil.detectVideoExtension(file));
	}

	@Test
	void rejectsSpoofedVideoContentTypeWithoutVideoMagicBytes() {
		MockMultipartFile file = new MockMultipartFile("file", "not-video.txt", "video/mp4",
				"hello world".getBytes());

		assertNull(FileStorageUtil.detectVideoExtension(file));
	}

	@Test
	void validatesAllowedJpgPngAndMp4Uploads() {
		MockMultipartFile jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
				new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
		MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png",
				new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
		MockMultipartFile mp4 = new MockMultipartFile("file", "clip.mp4", "video/mp4",
				new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});

		assertEquals("jpg", FileStorageUtil.validateAllowedUpload(jpg));
		assertEquals("png", FileStorageUtil.validateAllowedUpload(png));
		assertEquals("mp4", FileStorageUtil.validateAllowedUpload(mp4));
	}

	@Test
	void blocksDeniedAndSpoofedUploadTypes() {
		MockMultipartFile php = new MockMultipartFile("file", "shell.php", "image/jpeg",
				new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
		MockMultipartFile spoofed = new MockMultipartFile("file", "clip.mp4", "video/mp4",
				"not really an mp4".getBytes());
		MockMultipartFile javascript = new MockMultipartFile("file", "payload.js", "application/javascript",
				"alert(1)".getBytes());

		assertThrows(IllegalArgumentException.class, () -> FileStorageUtil.validateAllowedUpload(php));
		assertThrows(IllegalArgumentException.class, () -> FileStorageUtil.validateAllowedUpload(spoofed));
		assertThrows(IllegalArgumentException.class, () -> FileStorageUtil.validateAllowedUpload(javascript));
	}
}
