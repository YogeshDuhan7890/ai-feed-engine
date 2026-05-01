package com.yogesh.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
public class FFmpegUtil {

	private static void run(ProcessBuilder builder) throws Exception {

		builder.redirectErrorStream(true);

		Process process = builder.start();

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		StringBuilder output = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			output.append(line).append(System.lineSeparator());
		}

		int exit = process.waitFor();

		if (exit != 0) {
			String message = output.toString().trim();
			log.error("FFmpeg failed with exit code {}: {}", exit, message);
			throw new RuntimeException("FFmpeg failed: " + (message.isBlank() ? "Unknown error" : message));
		}
	}

	public static void extractAudio(String video, String output) throws Exception {

		ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-y", "-i", video, "-vn", "-acodec", "mp3", output);

		run(builder);
	}

	public static void generateThumbnail(String video, String output) throws Exception {

		ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-y", "-i", video, "-ss", "00:00:01", "-vframes", "1",
				output);

		run(builder);
	}

	public static void convertToHLS(String video, String playlist) throws Exception {

		ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-y", "-i", video, "-c", "copy", "-start_number", "0",
				"-hls_time", "10", "-hls_list_size", "0", "-f", "hls", playlist);

		run(builder);
	}

	/**
	 * Multi-bitrate HLS:
	 *   {outputDir}/360p/index.m3u8
	 *   {outputDir}/720p/index.m3u8
	 *   {outputDir}/1080p/index.m3u8
	 *   {outputDir}/master.m3u8
	 */
	public static void convertToMultiBitrateHLS(String video, String outputDir) throws Exception {
		if (video == null || video.isBlank()) {
			throw new IllegalArgumentException("video path empty");
		}
		if (outputDir == null || outputDir.isBlank()) {
			throw new IllegalArgumentException("outputDir empty");
		}

		while (outputDir.endsWith("/") || outputDir.endsWith("\\")) {
			outputDir = outputDir.substring(0, outputDir.length() - 1);
		}

		Files.createDirectories(Path.of(outputDir));

		String d360 = outputDir + "/360p";
		String d720 = outputDir + "/720p";
		String d1080 = outputDir + "/1080p";

		Files.createDirectories(Path.of(d360));
		Files.createDirectories(Path.of(d720));
		Files.createDirectories(Path.of(d1080));

		// Bitrates: tweak later if needed.
		generateVariant(video, d360, 360, "800k", "800k", "1600k");
		generateVariant(video, d720, 720, "1500k", "1500k", "3000k");
		generateVariant(video, d1080, 1080, "3000k", "3000k", "6000k");

		// Master playlist (simple VOD master)
		String master = buildMasterPlaylist();
		Files.writeString(Path.of(outputDir, "master.m3u8"), master);
	}

	private static void generateVariant(String video, String variantDir, int height, String bitrate, String maxrate,
			String bufsize) throws Exception {
		String playlist = variantDir + "/index.m3u8";
		String segPattern = variantDir + "/seg_%03d.ts";

		ProcessBuilder builder = new ProcessBuilder(
				"ffmpeg", "-y", "-i", video,
				"-map", "0:v:0",
				"-map", "0:a?",
				"-vf", "scale=-2:" + height,
				"-c:v", "libx264",
				"-preset", "veryfast",
				"-crf", "20",
				"-b:v", bitrate,
				"-maxrate", maxrate,
				"-bufsize", bufsize,
				"-pix_fmt", "yuv420p",
				"-g", "60",
				"-keyint_min", "60",
				"-sc_threshold", "0",
				"-c:a", "aac",
				"-b:a", "128k",
				"-hls_time", "10",
				"-hls_playlist_type", "vod",
				"-hls_flags", "independent_segments",
				"-hls_segment_filename", segPattern,
				playlist);

		run(builder);
	}

	private static String buildMasterPlaylist() {
		// Width is unknown because we scale with -2; using typical target widths for compatibility.
		return ""
				+ "#EXTM3U\n"
				+ "#EXT-X-VERSION:3\n"
				+ "#EXT-X-INDEPENDENT-SEGMENTS\n"
				+ "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,NAME=\"360p\"\n"
				+ "360p/index.m3u8\n"
				+ "#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,NAME=\"720p\"\n"
				+ "720p/index.m3u8\n"
				+ "#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,NAME=\"1080p\"\n"
				+ "1080p/index.m3u8\n";
	}

}
