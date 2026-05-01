package com.yogesh.logincontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogesh.model.Comment;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.*;
import com.yogesh.service.PushNotificationService;
import com.yogesh.util.RedisKeys;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import java.sql.Connection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class AdminController {

	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final EngagementRepository engagementRepository;
	private final CommentRepository commentRepository;
	private final StringRedisTemplate redisTemplate;
	private final DataSource dataSource;
	private final PushNotificationService pushNotificationService;

	// ========================
	// DASHBOARD
	// ========================
	@GetMapping("/admin/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("users", userRepository.count());
		model.addAttribute("posts", postRepository.count());
		model.addAttribute("engagements", engagementRepository.count());

		LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
		List<Object[]> stats = engagementRepository.getLast7DaysEngagementStats(sevenDaysAgo);

		List<String> labels = new ArrayList<>();
		List<Long> values = new ArrayList<>();
		for (Object[] row : stats) {
			labels.add(row[0].toString());
			values.add((Long) row[1]);
		}
		model.addAttribute("chartLabels", labels);
		model.addAttribute("chartValues", values);

		// Admin notifications (unread reports count)
		long pendingReports = countPendingReports();
		model.addAttribute("pendingReports", pendingReports);

		return "admin-dashboard";
	}

	// ========================
	// USER MANAGEMENT
	// ========================
	@GetMapping("/admin/users")
	public String manageUsers(Model model) {
		model.addAttribute("users",
				userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
		return "admin-users";
	}

	// AJAX Block
	@PostMapping("/admin/users/blockAjax/{id}")
	@ResponseBody
	public Map<String, Object> blockUserAjax(@PathVariable Long id) {
		try {
			User user = userRepository.findById(id).orElseThrow();
			user.setEnabled(false);
			userRepository.save(user);
			audit("USER_BLOCK", "Blocked user id=" + id + " name=" + user.getName());
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/user/sessions")
	@ResponseBody
	public List<Map<String, Object>> getUserSessions(Authentication auth, HttpServletRequest request) {

		String email = auth.getName();

		String userSessionKey = "user:sessions:" + email;

		Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionKey);

		List<Map<String, Object>> result = new ArrayList<>();

		if (sessionIds == null)
			return result;

		String currentSessionId = request.getSession().getId();

		for (String sessionId : sessionIds) {

			String data = redisTemplate.opsForValue().get("session:data:" + sessionId);

			if (data == null)
				continue;

			try {
				Map<String, Object> session = new ObjectMapper().readValue(data, Map.class);

				// ✅ current session identify
				session.put("current", sessionId.equals(currentSessionId));

				result.add(session);

			} catch (Exception ignored) {
			}
		}

		return result;
	}

	@DeleteMapping("/api/user/sessions/{sessionId}")
	@ResponseBody
	public Map<String, Object> logoutSession(@PathVariable String sessionId, Authentication auth) {

		String email = auth.getName();

		String userSessionKey = "user:sessions:" + email;

		redisTemplate.opsForSet().remove(userSessionKey, sessionId);
		redisTemplate.opsForSet().remove("admin:active:sessions", sessionId);

		redisTemplate.delete("session:data:" + sessionId);

		return Map.of("success", true, "message", "Session logout ho gaya");
	}

	@DeleteMapping("/api/user/sessions/others")
	@ResponseBody
	public Map<String, Object> logoutOtherSessions(Authentication auth, HttpServletRequest request) {

		String email = auth.getName();

		String currentSessionId = request.getSession().getId();

		String userSessionKey = "user:sessions:" + email;

		Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionKey);

		if (sessionIds != null) {
			for (String sessionId : sessionIds) {

				if (!sessionId.equals(currentSessionId)) {

					redisTemplate.opsForSet().remove(userSessionKey, sessionId);
					redisTemplate.opsForSet().remove("admin:active:sessions", sessionId);
					redisTemplate.delete("session:data:" + sessionId);
				}
			}
		}

		return Map.of("success", true, "message", "Other sessions logout ho gaye");
	}
	
	
	@GetMapping("/api/user/alerts")
	@ResponseBody
	public List<Map<String, Object>> getAlerts(Authentication auth) {

	    String email = auth.getName();

	    List<String> raw = redisTemplate.opsForList()
	            .range("user:alerts:" + email, 0, 20);

	    List<Map<String, Object>> result = new ArrayList<>();

	    if (raw == null) return result;

	    for (String s : raw) {
	        try {
	            result.add(new ObjectMapper().readValue(s, Map.class));
	        } catch (Exception ignored) {}
	    }

	    return result;
	}

	// AJAX Unblock
	@PostMapping("/admin/users/unblockAjax/{id}")
	@ResponseBody
	public Map<String, Object> unblockUserAjax(@PathVariable Long id) {
		try {
			User user = userRepository.findById(id).orElseThrow();
			user.setEnabled(true);
			userRepository.save(user);
			audit("USER_UNBLOCK", "Unblocked user id=" + id + " name=" + user.getName());
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// Traditional block (keep for fallback)
	@PostMapping("/admin/users/block/{id}")
	public String blockUser(@PathVariable Long id) {
		User user = userRepository.findById(id).orElseThrow();
		user.setEnabled(false);
		userRepository.save(user);
		return "redirect:/admin/users";
	}

	@PostMapping("/admin/users/unblock/{id}")
	public String unblockUser(@PathVariable Long id) {
		User user = userRepository.findById(id).orElseThrow();
		user.setEnabled(true);
		userRepository.save(user);
		return "redirect:/admin/users";
	}

	@PostMapping("/admin/users/promote/{id}")
	public String promoteUser(@PathVariable Long id) {
		User user = userRepository.findById(id).orElseThrow();
		user.setRole("ADMIN");
		userRepository.save(user);
		return "redirect:/admin/users";
	}

	// ========================
	// DELETE USER (+ all their posts)
	// ========================
	@PostMapping("/admin/users/delete/{id}")
	@ResponseBody
	public Map<String, Object> deleteUser(@PathVariable Long id) {
		try {
			User user = userRepository.findById(id).orElseThrow();
			// Delete all posts first
			List<Post> userPosts = postRepository.findByUserId(id);
			for (Post post : userPosts) {
				// Safe: findByPostId + deleteAll (deleteByPostId ki zaroorat nahi)
				commentRepository.deleteAll(commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId()));
				postRepository.delete(post);
			}
			userRepository.delete(user);
			audit("USER_DELETE", "Deleted user id=" + id + " name=" + user.getName());
			return Map.of("success", true, "message", "User aur uski saari videos delete ho gayi");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// TEMPORARY BAN
	// ========================
	@PostMapping("/admin/users/tempban/{id}")
	@ResponseBody
	public Map<String, Object> tempBanUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		try {
			User user = userRepository.findById(id).orElseThrow();
			int days = (int) body.getOrDefault("days", 7);
			user.setEnabled(false);
			userRepository.save(user);
			// Store ban expiry in Redis
			String banKey = "admin:ban:" + id;
			redisTemplate.opsForValue().set(banKey, String.valueOf(days), days, TimeUnit.DAYS);
			audit("USER_TEMPBAN", "TempBan user id=" + id + " name=" + user.getName() + " days=" + days);
			return Map.of("success", true, "message", days + " din ke liye ban ho gaya");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// WARNING EMAIL (sends to Redis queue — email service consume karega)
	// ========================
	@PostMapping("/admin/users/warn/{id}")
	@ResponseBody
	public Map<String, Object> warnUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		try {
			User user = userRepository.findById(id).orElseThrow();
			String reason = (String) body.getOrDefault("reason", "Terms of Service violation");
			// Push to Redis queue for email service
			String warningData = String.format("{\"userId\":%d,\"email\":\"%s\",\"reason\":\"%s\",\"time\":\"%s\"}",
					user.getId(), user.getEmail(), reason, LocalDateTime.now());
			redisTemplate.opsForList().leftPush("admin:warning:queue", warningData);
			// Also store warning in Redis for activity log
			redisTemplate.opsForList().leftPush("admin:warnings:user:" + id, warningData);
			return Map.of("success", true, "message", "Warning " + user.getName() + " ko bhej di gayi");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// USER ACTIVITY
	// ========================
	@GetMapping("/api/admin/users/{id}/activity")
	@ResponseBody
	public Map<String, Object> getUserActivity(@PathVariable Long id) {
		Map<String, Object> activity = new HashMap<>();
		try {
			User user = userRepository.findById(id).orElseThrow();
			activity.put("name", user.getName());
			activity.put("email", user.getEmail());
			activity.put("role", user.getRole());
			activity.put("enabled", user.isEnabled());

			// Posts count
			long postCount = postRepository.countByUserId(id);
			activity.put("totalPosts", postCount);

			// Recent posts
			List<Post> posts = postRepository.findByUserId(id);
			List<Map<String, Object>> recentPosts = new ArrayList<>();
			for (Post p : posts.stream().limit(10).collect(Collectors.toList())) {
				Map<String, Object> pm = new HashMap<>();
				pm.put("id", p.getId());
				pm.put("content", p.getContent());
				pm.put("createdAt", p.getCreatedAt());
				pm.put("videoUrl", p.getVideoUrl());
				long likes = 0;
				try {
					likes = engagementRepository.countLikes(p.getId());
				} catch (Exception ignored) {
				}
				pm.put("likes", likes);
				recentPosts.add(pm);
			}
			activity.put("recentPosts", recentPosts);

			// Warnings history
			List<String> warnings = redisTemplate.opsForList().range("admin:warnings:user:" + id, 0, -1);
			activity.put("warnings", warnings != null ? warnings : new ArrayList<>());

			// Ban status
			String banKey = "admin:ban:" + id;
			String banDays = redisTemplate.opsForValue().get(banKey);
			activity.put("isBanned", banDays != null);
			activity.put("banDays", banDays);

		} catch (Exception e) {
			activity.put("error", e.getMessage());
		}
		return activity;
	}

	// ========================
	// USER VIDEOS MODAL
	// ========================
	@GetMapping("/api/admin/users/{id}/videos")
	@ResponseBody
	public List<Map<String, Object>> getUserVideos(@PathVariable Long id) {
		List<Post> posts = postRepository.findByUserId(id);
		List<Map<String, Object>> result = new ArrayList<>();
		for (Post p : posts) {
			Map<String, Object> m = new HashMap<>();
			m.put("id", p.getId());
			m.put("content", p.getContent() != null ? p.getContent() : "");
			m.put("videoUrl", p.getVideoUrl());
			m.put("createdAt", p.getCreatedAt());
			m.put("tags", p.getTags());
			long likes = 0, comments = 0, shares = 0;
			try {
				likes = engagementRepository.countLikes(p.getId());
				comments = engagementRepository.countComments(p.getId());
				shares = engagementRepository.countShares(p.getId());
			} catch (Exception ignored) {
			}
			m.put("likes", likes);
			m.put("comments", comments);
			m.put("shares", shares);
			// Check if hidden
			String hiddenKey = "admin:hidden:post:" + p.getId();
			m.put("hidden", Boolean.TRUE.equals(redisTemplate.hasKey(hiddenKey)));
			result.add(m);
		}
		return result;
	}

	// ========================
	// MONITOR PAGE
	// ========================
	@GetMapping("/admin/monitor")
	public String systemMonitor(Model model) {
		try {
			Long keyCount = redisTemplate.getConnectionFactory().getConnection().dbSize();
			model.addAttribute("redisKeys", keyCount);
		} catch (Exception e) {
			model.addAttribute("redisKeys", -1);
		}
		return "admin-monitor";
	}

	// ========================
	// DELETE POST
	// ========================
	@PostMapping("/admin/posts/delete/{id}")
	@ResponseBody
	public Map<String, Object> adminDeletePost(@PathVariable Long id) {
		try {
			// Safe delete — deleteByPostId nahi, findByPostId + deleteAll use karo
			commentRepository.deleteAll(commentRepository.findByPostIdOrderByCreatedAtDesc(id));
			postRepository.deleteById(id);
			redisTemplate.delete("admin:hidden:post:" + id);
			redisTemplate.opsForSet().remove(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(id));
			audit("POST_DELETE", "Deleted post id=" + id);
			return Map.of("success", true, "message", "Post delete ho gaya");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// VIDEO HIDE / UNHIDE
	// ========================
	@PostMapping("/admin/posts/hide/{id}")
	@ResponseBody
	public Map<String, Object> hidePost(@PathVariable Long id) {
		try {
			postRepository.findById(id).orElseThrow();
			redisTemplate.opsForValue().set("admin:hidden:post:" + id, "1");
			redisTemplate.opsForSet().add("admin:hidden:posts", String.valueOf(id));
			audit("POST_HIDE", "Hidden post id=" + id);
			return Map.of("success", true, "message", "Video hide ho gayi");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/admin/posts/unhide/{id}")
	@ResponseBody
	public Map<String, Object> unhidePost(@PathVariable Long id) {
		try {
			redisTemplate.delete("admin:hidden:post:" + id);
			redisTemplate.opsForSet().remove("admin:hidden:posts", String.valueOf(id));
			audit("POST_UNHIDE", "Unhidden post id=" + id);
			return Map.of("success", true, "message", "Video unhide ho gayi");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// VIDEO TAGS MANAGE
	// ========================
	@PostMapping("/admin/posts/tags/{id}")
	@ResponseBody
	public Map<String, Object> updatePostTags(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		try {
			Post post = postRepository.findById(id).orElseThrow();
			String tags = (String) body.getOrDefault("tags", "");
			post.setTags(tags);
			postRepository.save(post);
			return Map.of("success", true, "message", "Tags update ho gaye");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// COMMENTS MODERATION
	// ========================
	@GetMapping("/admin/comments")
	public String commentsPage(Model model) {
		return "admin-comments";
	}

	@GetMapping("/api/admin/comments")
	@ResponseBody
	public List<Map<String, Object>> getAllComments() {
		List<Comment> comments = commentRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
				.getContent();
		// Fetch user names batch
		Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
		Map<Long, String> userNames = new HashMap<>();
		userRepository.findAllById(userIds).forEach(u -> userNames.put(u.getId(), u.getName()));

		List<Map<String, Object>> result = new ArrayList<>();
		for (Comment c : comments) {
			Map<String, Object> m = new HashMap<>();
			m.put("id", c.getId());
			m.put("postId", c.getPostId());
			m.put("userId", c.getUserId());
			m.put("userName", userNames.getOrDefault(c.getUserId(), "User#" + c.getUserId()));
			m.put("text", c.getText());
			m.put("createdAt", c.getCreatedAt());
			result.add(m);
		}
		// Sort newest first
		result.sort((a, b) -> {
			LocalDateTime ta = (LocalDateTime) a.get("createdAt");
			LocalDateTime tb = (LocalDateTime) b.get("createdAt");
			if (ta == null || tb == null)
				return 0;
			return tb.compareTo(ta);
		});
		return result;
	}

	@PostMapping("/admin/comments/delete/{id}")
	@ResponseBody
	public Map<String, Object> deleteComment(@PathVariable Long id) {
		try {
			commentRepository.deleteById(id);
			audit("COMMENT_DELETE", "Deleted comment id=" + id);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// REPORT SYSTEM
	// ========================
	@GetMapping("/admin/reports")
	public String reportsPage(Model model) {
		return "admin-reports";
	}

	@PostMapping("/api/admin/report")
	@ResponseBody
	public Map<String, Object> submitReport(@RequestBody Map<String, Object> body) {
		try {
			String type = (String) body.getOrDefault("type", "post"); // post / user
			String targetId = body.get("targetId").toString();
			String reason = (String) body.getOrDefault("reason", "");
			String reporterId = body.getOrDefault("reporterId", "anonymous").toString();

			String reportData = String.format(
					"{\"type\":\"%s\",\"targetId\":\"%s\",\"reason\":\"%s\",\"reporterId\":\"%s\",\"status\":\"pending\",\"time\":\"%s\"}",
					type, targetId, reason, reporterId, LocalDateTime.now());

			redisTemplate.opsForList().leftPush("admin:reports:pending", reportData);
			return Map.of("success", true, "message", "Report submit ho gaya");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/admin/reports")
	@ResponseBody
	public List<String> getReports(@RequestParam(defaultValue = "pending") String status) {
		String key = "admin:reports:" + status;
		List<String> reports = redisTemplate.opsForList().range(key, 0, -1);
		return reports != null ? reports : new ArrayList<>();
	}

	@PostMapping("/api/admin/reports/resolve/{index}")
	@ResponseBody
	public Map<String, Object> resolveReport(@PathVariable int index) {
		try {
			// Simple: move to resolved list
			List<String> pending = redisTemplate.opsForList().range("admin:reports:pending", 0, -1);
			if (pending != null && index < pending.size()) {
				String report = pending.get(index);
				redisTemplate.opsForList().leftPush("admin:reports:resolved", report);
				redisTemplate.opsForList().remove("admin:reports:pending", 1, report);
			}
			audit("REPORT_RESOLVE", "Resolved report index=" + index);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// ADMIN NOTIFICATIONS
	// ========================
	@GetMapping("/api/admin/notifications")
	@ResponseBody
	public Map<String, Object> getAdminNotifications() {
		Map<String, Object> notifs = new HashMap<>();
		long pendingReports = countPendingReports();
		notifs.put("pendingReports", pendingReports);

		// New users today
		LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
		long newUsersToday = 0;
		try {
			// Simple count — if createdAt exists on User, use it; else fallback
			newUsersToday = userRepository.count(); // placeholder — override with real query if createdAt exists
		} catch (Exception ignored) {
		}
		notifs.put("newUsersToday", 0); // fallback — update when User.createdAt is added

		try {
			newUsersToday = userRepository.countByCreatedAtAfter(todayStart);
		} catch (Exception ignored) {
		}
		notifs.put("newUsersToday", newUsersToday);

		// Blocked users count
		long blockedUsers = userRepository.countByEnabledFalse();
		notifs.put("blockedUsers", blockedUsers);

		// Hidden posts (set-based: O(1))
		Long hiddenCount = redisTemplate.opsForSet().size(RedisKeys.ADMIN_HIDDEN_POSTS_SET);
		notifs.put("hiddenPosts", hiddenCount != null ? hiddenCount : 0);

		// Pending bans
		Set<String> banKeys = scanKeys("admin:ban:*");
		notifs.put("activeTempBans", banKeys != null ? banKeys.size() : 0);

		return notifs;
	}

	// ========================
	// ALL POSTS API
	// ========================
	@GetMapping("/api/admin/posts")
	@ResponseBody
	public List<Map<String, Object>> getAllPosts() {
		List<Post> posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent();

		Set<Long> userIds = new HashSet<>();
		for (Post p : posts) {
			if (p.getUserId() != null)
				userIds.add(p.getUserId());
		}
		Map<Long, String> userNames = new HashMap<>();
		if (!userIds.isEmpty()) {
			userRepository.findAllById(userIds).forEach(u -> userNames.put(u.getId(), u.getName()));
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Post p : posts) {
			Map<String, Object> m = new HashMap<>();
			m.put("id", p.getId());
			m.put("content", p.getContent());
			m.put("userId", p.getUserId());
			m.put("userName", userNames.getOrDefault(p.getUserId(), "User#" + p.getUserId()));
			m.put("videoUrl", p.getVideoUrl());
			m.put("createdAt", p.getCreatedAt());
			m.put("tags", p.getTags());
			long likes = 0, comments = 0, shares = 0;
			try {
				likes = engagementRepository.countLikes(p.getId());
				comments = engagementRepository.countComments(p.getId());
				shares = engagementRepository.countShares(p.getId());
			} catch (Exception ignored) {
			}
			m.put("likes", likes);
			m.put("comments", comments);
			m.put("shares", shares);
			// Hidden status (set-based)
			boolean hidden = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeys.ADMIN_HIDDEN_POSTS_SET,
					String.valueOf(p.getId())));
			m.put("hidden", hidden);
			result.add(m);
		}
		return result;
	}

	// ========================
	// SYSTEM STATS API (Enhanced)
	// ========================
	@GetMapping("/api/admin/system")
	@ResponseBody
	public Map<String, Object> getSystemStats() {
		Map<String, Object> stats = new LinkedHashMap<>();

		// JVM Memory
		MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
		long heapUsed = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
		long heapMax = mem.getHeapMemoryUsage().getMax() / (1024 * 1024);
		long nonHeap = mem.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
		stats.put("heapUsedMB", heapUsed);
		stats.put("heapMaxMB", heapMax);
		stats.put("heapPercent", heapMax > 0 ? (heapUsed * 100 / heapMax) : 0);
		stats.put("nonHeapUsedMB", nonHeap);

		// GC Stats
		long gcCount = 0, gcTime = 0;
		for (java.lang.management.GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (gc.getCollectionCount() > 0)
				gcCount += gc.getCollectionCount();
			if (gc.getCollectionTime() > 0)
				gcTime += gc.getCollectionTime();
		}
		stats.put("gcCount", gcCount);
		stats.put("gcTimeMs", gcTime);

		// Uptime
		long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
		long uptimeMin = uptimeMs / 60000;
		long uptimeSec = (uptimeMs / 1000) % 60;
		stats.put("uptimeMinutes", uptimeMin);
		stats.put("uptimeSec", uptimeSec);
		stats.put("uptimeMs", uptimeMs);
		stats.put("uptimeFormatted", String.format("%dh %dm %ds", uptimeMin / 60, uptimeMin % 60, uptimeSec));

		// OS / CPU
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		double cpuLoad = os.getSystemLoadAverage();
		stats.put("cpuLoad", Math.round(cpuLoad * 100.0) / 100.0);
		stats.put("cpuLoadPercent",
				cpuLoad >= 0 ? Math.min(100, (int) (cpuLoad / os.getAvailableProcessors() * 100)) : -1);
		stats.put("availableProcessors", os.getAvailableProcessors());
		stats.put("osName", os.getName() + " " + os.getArch());

		// JVM Process CPU (if available)
		try {
			if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
				double processCpu = sunOs.getProcessCpuLoad() * 100;
				stats.put("processCpuPercent", Math.round(processCpu * 10.0) / 10.0);
				long totalMem = sunOs.getTotalMemorySize() / (1024 * 1024);
				long freeMem = sunOs.getFreeMemorySize() / (1024 * 1024);
				stats.put("systemTotalMemMB", totalMem);
				stats.put("systemFreeMemMB", freeMem);
				stats.put("systemMemPercent", totalMem > 0 ? (int) ((totalMem - freeMem) * 100 / totalMem) : 0);
			}
		} catch (Exception ignored) {
		}

		// Threads
		java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		stats.put("threadCount", threads.getThreadCount());
		stats.put("peakThreadCount", threads.getPeakThreadCount());
		stats.put("daemonThreadCount", threads.getDaemonThreadCount());
		stats.put("totalStartedThreads", threads.getTotalStartedThreadCount());

		// Redis
		try {
			long redisKeys = redisTemplate.getConnectionFactory().getConnection().dbSize();
			stats.put("redisKeys", redisKeys);
			stats.put("redisStatus", "UP");
		} catch (Exception e) {
			stats.put("redisKeys", -1);
			stats.put("redisStatus", "DOWN");
		}

		// Database health check
		try (Connection conn = dataSource.getConnection()) {
			long dbStart = System.currentTimeMillis();
			conn.isValid(2);
			long dbPing = System.currentTimeMillis() - dbStart;
			stats.put("dbStatus", "UP");
			stats.put("dbPingMs", dbPing);
			stats.put("dbUrl", conn.getMetaData().getURL().replaceAll("password=[^&]*", "password=***"));
			stats.put("dbProduct",
					conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
		} catch (Exception e) {
			stats.put("dbStatus", "DOWN");
			stats.put("dbPingMs", -1);
			stats.put("dbError", e.getMessage());
		}

		// App stats
		stats.put("totalUsers", userRepository.count());
		stats.put("totalPosts", postRepository.count());
		stats.put("totalEngagements", engagementRepository.count());
		stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

		return stats;
	}

	// ========================
	// DISK SPACE API
	// ========================
	@GetMapping("/api/admin/system/disk")
	@ResponseBody
	public Map<String, Object> getDiskStats() {
		Map<String, Object> disk = new LinkedHashMap<>();
		try {
			java.io.File root = new java.io.File("/");
			long total = root.getTotalSpace() / (1024 * 1024 * 1024);
			long free = root.getFreeSpace() / (1024 * 1024 * 1024);
			long used = total - free;
			disk.put("totalGB", total);
			disk.put("freeGB", free);
			disk.put("usedGB", used);
			disk.put("usedPercent", total > 0 ? (int) (used * 100 / total) : 0);
		} catch (Exception e) {
			disk.put("error", e.getMessage());
		}
		return disk;
	}

	// ========================
	// TRENDING API
	// ========================
	@GetMapping("/api/admin/trending")
	@ResponseBody
	public List<Map<String, Object>> getTrending() {
		List<Map<String, Object>> result = new ArrayList<>();
		try {
			Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> trending = redisTemplate
					.opsForZSet().reverseRangeWithScores(RedisKeys.TRENDING_GLOBAL_ZSET, 0, 9);
			if (trending == null)
				return result;
			for (var entry : trending) {
				Map<String, Object> m = new HashMap<>();
				String postId = entry.getValue();
				m.put("postId", postId);
				m.put("score", entry.getScore() != null ? String.format("%.2f", entry.getScore()) : "0");
				try {
					Post p = postRepository.findById(Long.parseLong(postId)).orElse(null);
					m.put("content", p != null ? p.getContent() : "(deleted)");
					if (p != null && p.getUserId() != null) {
						m.put("userId", p.getUserId());
						String name = userRepository.findById(p.getUserId()).map(User::getName)
								.orElse("User#" + p.getUserId());
						m.put("userName", name);
					} else {
						m.put("userId", null);
						m.put("userName", "-");
					}
				} catch (Exception e) {
					m.put("content", "(error)");
					m.put("userName", "-");
				}
				result.add(m);
			}
		} catch (Exception ignored) {
		}
		return result;
	}

	// ========================
	// LIVE STATS API
	// ========================
	@GetMapping("/api/admin/stats")
	@ResponseBody
	public Map<String, Object> getLiveStats() {
		Map<String, Object> stats = new HashMap<>();
		stats.put("users", userRepository.count());
		stats.put("posts", postRepository.count());
		stats.put("engagements", engagementRepository.count());
		try {
			stats.put("redisKeys", redisTemplate.getConnectionFactory().getConnection().dbSize());
		} catch (Exception e) {
			stats.put("redisKeys", -1);
		}
		LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
		List<Object[]> chartData = engagementRepository.getLast7DaysEngagementStats(sevenDaysAgo);
		List<String> labels = new ArrayList<>();
		List<Long> values = new ArrayList<>();
		for (Object[] row : chartData) {
			labels.add(row[0].toString());
			values.add((Long) row[1]);
		}
		stats.put("chartLabels", labels);
		stats.put("chartValues", values);
		// Add notification counts to live stats
		long pendingReports = countPendingReports();
		stats.put("pendingReports", pendingReports);
		return stats;
	}

	// ========================
	// ANALYTICS PAGE + API
	// ========================
	@GetMapping("/admin/analytics")
	public String analyticsPage(Model model) {
		return "admin-analytics";
	}

	@GetMapping("/api/admin/analytics")
	@ResponseBody
	public Map<String, Object> getAnalyticsData(@RequestParam(defaultValue = "30") int days) {
		Map<String, Object> data = new HashMap<>();
		LocalDateTime startDate = LocalDateTime.now().minusDays(days);

		// 1. Daily engagement trend
		List<Object[]> daily = engagementRepository.getDailyEngagementStats(startDate);
		List<String> dailyLabels = new ArrayList<>();
		List<Long> dailyValues = new ArrayList<>();
		for (Object[] row : daily) {
			dailyLabels.add(row[0].toString());
			dailyValues.add(((Number) row[1]).longValue());
		}
		data.put("dailyLabels", dailyLabels);
		data.put("dailyValues", dailyValues);

		// 2. Engagement by type (Doughnut)
		List<Object[]> byType = engagementRepository.countByTypeInRange(startDate);
		Map<String, Long> typeMap = new LinkedHashMap<>();
		for (Object[] row : byType) {
			typeMap.put((String) row[0], ((Number) row[1]).longValue());
		}
		data.put("engagementByType", typeMap);

		// 3. Hourly distribution (peak hours)
		List<Object[]> hourly = engagementRepository.getHourlyDistribution();
		List<String> hourLabels = new ArrayList<>();
		List<Long> hourValues = new ArrayList<>();
		for (Object[] row : hourly) {
			int hr = ((Number) row[0]).intValue();
			hourLabels.add(String.format("%02d:00", hr));
			hourValues.add(((Number) row[1]).longValue());
		}
		data.put("hourlyLabels", hourLabels);
		data.put("hourlyValues", hourValues);

		// 4. Top 10 posts
		List<Object[]> topPosts = engagementRepository.getTopPostsByEngagement(PageRequest.of(0, 10));
		List<Map<String, Object>> topPostList = new ArrayList<>();
		for (Object[] row : topPosts) {
			Long postId = ((Number) row[0]).longValue();
			Long count = ((Number) row[1]).longValue();
			Map<String, Object> pm = new HashMap<>();
			pm.put("postId", postId);
			pm.put("engagements", count);
			try {
				postRepository.findById(postId).ifPresent(p -> {
					pm.put("content",
							p.getContent() != null ? p.getContent().substring(0, Math.min(40, p.getContent().length()))
									: "");
					if (p.getUserId() != null) {
						userRepository.findById(p.getUserId()).ifPresent(u -> pm.put("userName", u.getName()));
					}
				});
			} catch (Exception ignored) {
			}
			topPostList.add(pm);
		}
		data.put("topPosts", topPostList);

		// 5. Avg watch time per day
		List<Object[]> watchTime = engagementRepository.getAvgWatchTimePerDay(startDate);
		List<String> wtLabels = new ArrayList<>();
		List<Double> wtValues = new ArrayList<>();
		for (Object[] row : watchTime) {
			wtLabels.add(row[0].toString());
			wtValues.add(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
		}
		data.put("watchTimeLabels", wtLabels);
		data.put("watchTimeValues", wtValues);

		// 6. Summary stats
		data.put("totalEngagements", engagementRepository.count());
		data.put("totalUsers", userRepository.count());
		data.put("totalPosts", postRepository.count());
		data.put("days", days);

		return data;
	}

	// ========================
	// EXPORT: USERS CSV
	// ========================
	@GetMapping("/api/admin/export/users")
	public ResponseEntity<byte[]> exportUsers() {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("ID,Name,Email,Role,Status,Total Posts\n");
			List<User> users = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
					.getContent();
			for (User u : users) {
				long postCount = 0;
				try {
					postCount = postRepository.countByUserId(u.getId());
				} catch (Exception ignored) {
				}
				sb.append(u.getId()).append(",");
				sb.append(q(escape(u.getName()))).append(",");
				sb.append(q(escape(u.getEmail()))).append(",");
				sb.append(u.getRole()).append(",");
				sb.append(u.isEnabled() ? "Active" : "Blocked").append(",");
				sb.append(postCount).append("\n");
			}
			byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
			String filename = "users_" + ts + ".csv";
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
					.contentType(MediaType.parseMediaType("text/csv")).body(bytes);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	// ========================
	// EXPORT: POSTS CSV
	// ========================
	@GetMapping("/api/admin/export/posts")
	public ResponseEntity<byte[]> exportPosts() {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("ID,User,Content,Tags,Likes,Comments,Shares,Hidden,Created At\n");
			List<Post> posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
					.getContent();
			Set<Long> userIds = new HashSet<>();
			for (Post p : posts) {
				if (p.getUserId() != null)
					userIds.add(p.getUserId());
			}
			Map<Long, String> userNames = new HashMap<>();
			userRepository.findAllById(userIds).forEach(u -> userNames.put(u.getId(), u.getName()));
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
			for (Post p : posts) {
				long likes = 0, comments = 0, shares = 0;
				try {
					likes = engagementRepository.countLikes(p.getId());
					comments = engagementRepository.countComments(p.getId());
					shares = engagementRepository.countShares(p.getId());
				} catch (Exception ignored) {
				}
				boolean hidden = Boolean.TRUE.equals(
						redisTemplate.opsForSet().isMember(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(p.getId())));
				String createdAt = p.getCreatedAt() != null ? p.getCreatedAt().format(fmt) : "";
				sb.append(p.getId()).append(",");
				sb.append(q(escape(userNames.getOrDefault(p.getUserId(), "")))).append(",");
				sb.append(q(escape(p.getContent()))).append(",");
				sb.append(q(escape(p.getTags()))).append(",");
				sb.append(likes).append(",");
				sb.append(comments).append(",");
				sb.append(shares).append(",");
				sb.append(hidden ? "Yes" : "No").append(",");
				sb.append(q(createdAt)).append("\n");
			}
			byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
			String filename = "posts_" + ts + ".csv";
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
					.contentType(MediaType.parseMediaType("text/csv")).body(bytes);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	// ========================
	// EXPORT: ENGAGEMENTS CSV
	// ========================
	@GetMapping("/api/admin/export/engagements")
	public ResponseEntity<byte[]> exportEngagements(@RequestParam(defaultValue = "30") int days) {
		try {
			StringBuilder sb = new StringBuilder();
			LocalDateTime startDate = LocalDateTime.now().minusDays(days);
			sb.append("Date,Engagements\n");
			List<Object[]> stats = engagementRepository.getDailyEngagementStats(startDate);
			for (Object[] row : stats) {
				sb.append(q(row[0].toString())).append(",");
				sb.append(((Number) row[1]).longValue()).append("\n");
			}
			sb.append("\nType,Count\n");
			List<Object[]> byType = engagementRepository.countByType();
			for (Object[] row : byType) {
				sb.append(row[0]).append(",");
				sb.append(((Number) row[1]).longValue()).append("\n");
			}
			byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
			String filename = "engagements_" + days + "days_" + ts + ".csv";
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
					.contentType(MediaType.parseMediaType("text/csv")).body(bytes);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	// ========================
	// EXPORT: FULL REPORT JSON
	// ========================
	@GetMapping("/api/admin/export/report")
	@ResponseBody
	public Map<String, Object> exportFullReport(@RequestParam(defaultValue = "30") int days) {
		Map<String, Object> report = new LinkedHashMap<>();
		LocalDateTime startDate = LocalDateTime.now().minusDays(days);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		report.put("generatedAt", LocalDateTime.now().format(fmt));
		report.put("periodDays", days);
		report.put("totalUsers", userRepository.count());
		report.put("totalPosts", postRepository.count());
		report.put("totalEngagements", engagementRepository.count());
		long blocked = userRepository.countByEnabledFalse();
		report.put("blockedUsers", blocked);
		Long hiddenCount = redisTemplate.opsForSet().size(RedisKeys.ADMIN_HIDDEN_POSTS_SET);
		report.put("hiddenPosts", hiddenCount != null ? hiddenCount : 0);
		report.put("pendingReports", countPendingReports());
		Map<String, Long> typeMap = new LinkedHashMap<>();
		engagementRepository.countByTypeInRange(startDate)
				.forEach(row -> typeMap.put((String) row[0], ((Number) row[1]).longValue()));
		report.put("engagementByType", typeMap);
		List<Map<String, Object>> top5 = new ArrayList<>();
		engagementRepository.getTopPostsByEngagement(PageRequest.of(0, 5)).forEach(row -> {
			Long postId = ((Number) row[0]).longValue();
			Map<String, Object> pm = new LinkedHashMap<>();
			pm.put("postId", postId);
			pm.put("engagements", ((Number) row[1]).longValue());
			postRepository.findById(postId).ifPresent(p -> {
				pm.put("content", p.getContent());
				if (p.getUserId() != null)
					userRepository.findById(p.getUserId()).ifPresent(u -> pm.put("author", u.getName()));
			});
			top5.add(pm);
		});
		report.put("topPosts", top5);
		return report;
	}

	// ========================
	// EXPORT PAGE
	// ========================
	@GetMapping("/admin/export")
	public String exportPage(Model model) {
		return "admin-export";
	}

	// CSV helpers
	private String escape(String val) {
		if (val == null)
			return "";
		return val.replace("\"", "\"\"").replace("\n", " ").replace("\r", "");
	}

	private String q(String val) {
		return "\"" + val + "\"";
	}

	// ========================
	// ROLE MANAGEMENT PAGE + API
	// ========================
	@GetMapping("/admin/roles")
	public String rolesPage(Model model) {
		return "admin-roles";
	}

	@GetMapping("/api/admin/roles/users")
	@ResponseBody
	public List<Map<String, Object>> getAllUsersWithRoles() {
		List<User> users = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
		List<Map<String, Object>> result = new ArrayList<>();
		for (User u : users) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", u.getId());
			m.put("name", u.getName());
			m.put("email", u.getEmail());
			m.put("role", u.getRole());
			m.put("enabled", u.isEnabled());
			m.put("avatar", u.getAvatar());
			result.add(m);
		}
		return result;
	}

	@PostMapping("/api/admin/roles/assign")
	@ResponseBody
	public Map<String, Object> assignRole(@RequestBody Map<String, Object> body) {
		try {
			Long userId = Long.parseLong(body.get("userId").toString());
			String newRole = (String) body.get("role");

			// Validate role
			List<String> validRoles = List.of("USER", "ADMIN");
			if (!validRoles.contains(newRole)) {
				return Map.of("success", false, "message", "Invalid role: " + newRole);
			}

			User user = userRepository.findById(userId).orElseThrow();
			String oldRole = user.getRole();
			user.setRole(newRole);
			userRepository.save(user);

			// Log role change in Redis
			String logEntry = "{" + "\"userId\":" + userId + "," + "\"name\":\"" + user.getName().replace("\"", "")
					+ "\"," + "\"oldRole\":\"" + oldRole + "\"," + "\"newRole\":\"" + newRole + "\"," + "\"time\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\"" + "}";
			redisTemplate.opsForList().leftPush("admin:role:changelog", logEntry);
			redisTemplate.opsForList().trim("admin:role:changelog", 0, 99);

			audit("ROLE_ASSIGN", "Changed role of user id=" + userId + " name=" + user.getName() + " from=" + oldRole
					+ " to=" + newRole);
			return Map.of("success", true, "message", user.getName() + " ko " + newRole + " role assign ho gaya");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/admin/roles/changelog")
	@ResponseBody
	public List<String> getRoleChangelog() {
		List<String> log = redisTemplate.opsForList().range("admin:role:changelog", 0, 49);
		return log != null ? log : new ArrayList<>();
	}

	@GetMapping("/api/admin/roles/stats")
	@ResponseBody
	public Map<String, Object> getRoleStats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		Map<String, Long> roleCounts = new LinkedHashMap<>();
		roleCounts.put("ADMIN", userRepository.countByRole("ADMIN"));
		roleCounts.put("USER", userRepository.countByRole("USER"));
		stats.put("roleCounts", roleCounts);
		stats.put("totalUsers", userRepository.count());
		List<String> changelog = redisTemplate.opsForList().range("admin:role:changelog", 0, 9);
		stats.put("recentChanges", changelog != null ? changelog : new ArrayList<>());
		return stats;
	}

	// ========================
	// PUSH NOTIFICATIONS PAGE + API
	// ========================
	@GetMapping("/admin/notifications-broadcast")
	public String notificationsBroadcastPage(Model model) {
		return "admin-notifications";
	}

	@GetMapping("/api/admin/push/stats")
	@ResponseBody
	public Map<String, Object> getPushStats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		try {
			long totalSubs = pushNotificationService.getTotalSubscriptions();
			long activeUsers = pushNotificationService.getActiveSubscriberCount();
			stats.put("totalSubscriptions", totalSubs);
			stats.put("activeSubscribers", activeUsers);
			stats.put("vapidConfigured", pushNotificationService.isVapidConfigured());
			stats.put("vapidPublicKey", pushNotificationService.getVapidPublicKey());
			stats.put("vapidSubject", pushNotificationService.getVapidSubject());
			// Broadcast history from Redis
			List<String> history = redisTemplate.opsForList().range("admin:broadcast:history", 0, 19);
			stats.put("broadcastHistory", history != null ? history : new ArrayList<>());
		} catch (Exception e) {
			stats.put("error", e.getMessage());
		}
		return stats;
	}

	@PostMapping("/api/admin/push/broadcast")
	@ResponseBody
	public Map<String, Object> broadcastPush(@RequestBody Map<String, Object> body) {
		try {
			String title = (String) body.getOrDefault("title", "AI Feed");
			String message = (String) body.getOrDefault("message", "");
			String url = (String) body.getOrDefault("url", "/feed");
			String target = (String) body.getOrDefault("target", "all"); // all / user
			Object userIdObj = body.get("userId");

			if (message.isBlank())
				return Map.of("success", false, "message", "Message empty nahi ho sakta");
			if (!pushNotificationService.isVapidConfigured())
				return Map.of("success", false, "message", "Pehle free VAPID keys generate karo");

			int sent = 0;
			if ("user".equals(target) && userIdObj != null) {
				Long userId = Long.parseLong(userIdObj.toString());
				pushNotificationService.sendToUser(userId, title, message, url);
				sent = 1;
			} else {
				sent = pushNotificationService.broadcastToAll(title, message, url);
			}

			// Log to Redis history
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
			String log = String.format(
					"{\"title\":\"%s\",\"message\":\"%s\",\"url\":\"%s\",\"target\":\"%s\",\"userId\":\"%s\",\"sent\":%d,\"time\":\"%s\"}",
					title.replace("\"", "\\\""), message.replace("\"", "\\\""), url, target, sent,
					userIdObj != null ? userIdObj.toString().replace("\"", "") : "",
					LocalDateTime.now().format(fmt));
			redisTemplate.opsForList().leftPush("admin:broadcast:history", log);
			redisTemplate.opsForList().trim("admin:broadcast:history", 0, 49); // keep last 50

			audit("PUSH_BROADCAST", "Broadcast title=" + title + " target=" + target + " sent=" + sent);
			return Map.of("success", true, "sent", sent, "message", sent + " subscribers ko notification bhej di");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@DeleteMapping("/api/admin/push/history")
	@ResponseBody
	public Map<String, Object> clearBroadcastHistory() {
		try {
			redisTemplate.delete("admin:broadcast:history");
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// AUDIT LOG PAGE + API
	// ========================
	@GetMapping("/admin/audit")
	public String auditPage(Model model) {
		return "admin-audit";
	}

	@GetMapping("/api/admin/audit")
	@ResponseBody
	public Map<String, Object> getAuditLog(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size, @RequestParam(defaultValue = "") String search) {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			List<String> all = redisTemplate.opsForList().range("admin:audit:log", 0, -1);
			if (all == null)
				all = new ArrayList<>();

			// Filter by search
			if (!search.isBlank()) {
				String q = search.toLowerCase();
				all = all.stream().filter(e -> e.toLowerCase().contains(q)).collect(Collectors.toList());
			}

			result.put("total", all.size());
			result.put("page", page);
			result.put("size", size);

			// Paginate
			int from = page * size;
			int to = Math.min(from + size, all.size());
			result.put("entries", from < all.size() ? all.subList(from, to) : new ArrayList<>());
		} catch (Exception e) {
			result.put("error", e.getMessage());
			result.put("entries", new ArrayList<>());
		}
		return result;
	}

	@DeleteMapping("/api/admin/audit")
	@ResponseBody
	public Map<String, Object> clearAuditLog() {
		try {
			audit("AUDIT_CLEAR", "Audit log cleared by admin");
			redisTemplate.delete("admin:audit:log");
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/admin/audit/stats")
	@ResponseBody
	public Map<String, Object> getAuditStats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		try {
			List<String> all = redisTemplate.opsForList().range("admin:audit:log", 0, -1);
			if (all == null)
				all = new ArrayList<>();
			stats.put("total", all.size());

			// Count by action type
			Map<String, Long> actionCounts = new LinkedHashMap<>();
			for (String entry : all) {
				try {
					int a = entry.indexOf("\"action\":\"") + 10;
					int b = entry.indexOf("\"", a);
					if (a > 10 && b > a) {
						String action = entry.substring(a, b);
						actionCounts.merge(action, 1L, Long::sum);
					}
				} catch (Exception ignored) {
				}
			}
			stats.put("actionCounts", actionCounts);

			// Last 5 entries
			stats.put("recent", all.subList(0, Math.min(5, all.size())));
		} catch (Exception e) {
			stats.put("error", e.getMessage());
		}
		return stats;
	}

	// ========================
	// AUDIT LOG HELPER
	// ========================
	private void audit(String action, String detail) {
		try {
			String admin = "unknown";
			try {
				Authentication auth = SecurityContextHolder.getContext().getAuthentication();
				if (auth != null && auth.getName() != null)
					admin = auth.getName();
			} catch (Exception ignored) {
			}
			String entry = "{" + "\"time\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\","
					+ "\"admin\":\"" + admin.replace("\"", "") + "\"," + "\"action\":\"" + action.replace("\"", "")
					+ "\"," + "\"detail\":\"" + detail.replace("\"", "").replace("\n", " ") + "\"" + "}";
			redisTemplate.opsForList().leftPush("admin:audit:log", entry);
			redisTemplate.opsForList().trim("admin:audit:log", 0, 499); // keep last 500
		} catch (Exception e) {
			// Audit fail should never crash the main action
		}
	}

	// ========================
	// HELPER
	// ========================
	private long countPendingReports() {
		try {
			Long size = redisTemplate.opsForList().size("admin:reports:pending");
			return size != null ? size : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	// ========================
	// VIDEO MANAGEMENT
	// ========================
	@GetMapping("/admin/videos")
	public String videosPage(Model model) {
		return "admin-videos";
	}

	@GetMapping("/api/admin/videos")
	@ResponseBody
	public List<Map<String, Object>> getAllVideos(@RequestParam(defaultValue = "") String tag,
			@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "newest") String sort) {
		List<Post> posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
		Set<Long> uids = posts.stream().filter(p -> p.getUserId() != null).map(Post::getUserId)
				.collect(Collectors.toSet());
		Map<Long, String> names = new HashMap<>();
		userRepository.findAllById(uids).forEach(u -> names.put(u.getId(), u.getName()));
		List<Map<String, Object>> result = new ArrayList<>();
		for (Post p : posts) {
			String t = p.getTags() != null ? p.getTags().toLowerCase() : "";
			String c = p.getContent() != null ? p.getContent().toLowerCase() : "";
			if (!tag.isBlank() && !t.contains(tag.toLowerCase()))
				continue;
			if (!search.isBlank() && !c.contains(search.toLowerCase()) && !t.contains(search.toLowerCase()))
				continue;
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", p.getId());
			m.put("content", p.getContent());
			m.put("tags", p.getTags());
			m.put("videoUrl", p.getVideoUrl());
			m.put("createdAt", p.getCreatedAt());
			m.put("status", p.getStatus());
			m.put("scheduledAt", p.getScheduledAt());
			m.put("userId", p.getUserId());
			m.put("userName", names.getOrDefault(p.getUserId(), "User#" + p.getUserId()));
			boolean hidden = Boolean.TRUE.equals(
					redisTemplate.opsForSet().isMember(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(p.getId())));
			m.put("hidden", hidden);
			long likes = 0, comments = 0;
			try {
				likes = engagementRepository.countLikes(p.getId());
				comments = engagementRepository.countComments(p.getId());
			} catch (Exception ignored) {
			}
			m.put("likes", likes);
			m.put("comments", comments);
			result.add(m);
		}
		// Sort
		if ("likes".equals(sort))
			result.sort((a, b) -> Long.compare((Long) b.get("likes"), (Long) a.get("likes")));
		else if ("oldest".equals(sort))
			result.sort((a, b) -> {
				LocalDateTime d1 = (LocalDateTime) a.get("createdAt");
				LocalDateTime d2 = (LocalDateTime) b.get("createdAt");

				if (d1 == null || d2 == null)
					return 0;

				return d1.compareTo(d2);
			});
		else
			result.sort((a, b) -> {
				LocalDateTime d1 = (LocalDateTime) a.get("createdAt");
				LocalDateTime d2 = (LocalDateTime) b.get("createdAt");

				if (d1 == null || d2 == null)
					return 0;

				return d2.compareTo(d1);
			});
		return result;
	}

	@PostMapping("/api/admin/videos/bulk")
	@ResponseBody
	public Map<String, Object> bulkVideoAction(@RequestBody Map<String, Object> body) {
		try {
			String action = (String) body.get("action");
			List<Integer> ids = (List<Integer>) body.get("ids");
			if (ids == null || ids.isEmpty())
				return Map.of("success", false, "message", "Koi video select nahi ki");
			int count = 0;
			for (Integer rawId : ids) {
				Long id = Long.valueOf(rawId);
				try {
					if ("hide".equals(action)) {
						redisTemplate.opsForValue().set("admin:hidden:post:" + id, "1");
						redisTemplate.opsForSet().add(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(id));
						audit("POST_HIDE", "Bulk hide post id=" + id);
					} else if ("unhide".equals(action)) {
						redisTemplate.delete("admin:hidden:post:" + id);
						redisTemplate.opsForSet().remove(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(id));
						audit("POST_UNHIDE", "Bulk unhide post id=" + id);
					} else if ("delete".equals(action)) {
						commentRepository.deleteAll(commentRepository.findByPostIdOrderByCreatedAtDesc(id));
						postRepository.deleteById(id);
						redisTemplate.opsForSet().remove(RedisKeys.ADMIN_HIDDEN_POSTS_SET, String.valueOf(id));
						audit("POST_DELETE", "Bulk delete post id=" + id);
					}
					count++;
				} catch (Exception ignored) {
				}
			}
			return Map.of("success", true, "message", count + " videos " + action + " ho gayi");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// USER ACTIVITY TIMELINE
	// ========================
	@GetMapping("/admin/user-activity")
	public String userActivityPage(Model model) {
		model.addAttribute("users",
				userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
		return "admin-user-activity";
	}

	@GetMapping("/api/admin/user-activity/{id}")
	@ResponseBody
	public Map<String, Object> getUserActivityTimeline(@PathVariable Long id) {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			User user = userRepository.findById(id).orElseThrow();
			result.put("id", user.getId());
			result.put("name", user.getName());
			result.put("email", user.getEmail());
			result.put("role", user.getRole());
			result.put("enabled", user.isEnabled());
			result.put("avatar", user.getAvatar());
			// Posts
			List<Post> posts = postRepository.findByUserId(id);
			List<Map<String, Object>> postList = new ArrayList<>();
			for (Post p : posts.stream().limit(20).collect(Collectors.toList())) {
				Map<String, Object> pm = new LinkedHashMap<>();
				pm.put("id", p.getId());
				pm.put("content", p.getContent());
				pm.put("createdAt", p.getCreatedAt());
				pm.put("videoUrl", p.getVideoUrl());
				long likes = 0;
				try {
					likes = engagementRepository.countLikes(p.getId());
				} catch (Exception ignored) {
				}
				pm.put("likes", likes);
				postList.add(pm);
			}
			result.put("posts", postList);
			result.put("totalPosts", posts.size());
			// Warnings
			List<String> warnings = redisTemplate.opsForList().range("admin:warnings:user:" + id, 0, -1);
			result.put("warnings", warnings != null ? warnings : new ArrayList<>());
			// Ban status
			String banKey = "admin:ban:" + id;
			String banDays = redisTemplate.opsForValue().get(banKey);
			result.put("isBanned", banDays != null);
			result.put("banDays", banDays);
			// Role changelog
			List<String> roleLog = redisTemplate.opsForList().range("admin:role:changelog", 0, -1);
			List<String> userRoleLog = new ArrayList<>();
			if (roleLog != null) {
				for (String entry : roleLog) {
					if (entry.contains("\"userId\":" + id + ","))
						userRoleLog.add(entry);
				}
			}
			result.put("roleChangelog", userRoleLog);
		} catch (Exception e) {
			result.put("error", e.getMessage());
		}
		return result;
	}

	// ========================
	// BANNED WORDS FILTER
	// ========================
	@GetMapping("/admin/banned-words")
	public String bannedWordsPage(Model model) {
		return "admin-banned-words";
	}

	@GetMapping("/api/admin/banned-words")
	@ResponseBody
	public List<String> getBannedWords() {
		List<String> words = redisTemplate.opsForList().range("admin:banned:words", 0, -1);
		return words != null ? words : new ArrayList<>();
	}

	@PostMapping("/api/admin/banned-words")
	@ResponseBody
	public Map<String, Object> addBannedWord(@RequestBody Map<String, String> body) {
		try {
			String word = body.get("word");
			if (word == null || word.isBlank())
				return Map.of("success", false, "message", "Word empty nahi ho sakta");
			word = word.toLowerCase().trim();
			List<String> existing = redisTemplate.opsForList().range("admin:banned:words", 0, -1);
			if (existing != null && existing.contains(word))
				return Map.of("success", false, "message", "Ye word pehle se hai");
			redisTemplate.opsForList().rightPush("admin:banned:words", word);
			redisTemplate.opsForSet().add(RedisKeys.ADMIN_BANNED_WORDS_SET, word);
			audit("BANNED_WORD_ADD", "Added banned word: " + word);
			return Map.of("success", true, "message", "'" + word + "' banned list mein add ho gaya");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@DeleteMapping("/api/admin/banned-words/{word}")
	@ResponseBody
	public Map<String, Object> removeBannedWord(@PathVariable String word) {
		try {
			redisTemplate.opsForList().remove("admin:banned:words", 1, word);
			redisTemplate.opsForSet().remove(RedisKeys.ADMIN_BANNED_WORDS_SET, word);
			audit("BANNED_WORD_REMOVE", "Removed banned word: " + word);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/api/admin/banned-words/scan")
	@ResponseBody
	public Map<String, Object> scanContentForBannedWords() {
		try {
			List<String> words = redisTemplate.opsForList().range("admin:banned:words", 0, -1);
			if (words == null || words.isEmpty())
				return Map.of("success", true, "violations", new ArrayList<>(), "message",
						"Koi banned word set nahi hai");
			List<Post> posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
					.getContent();
			List<Map<String, Object>> violations = new ArrayList<>();
			for (Post p : posts) {
				String content = (p.getContent() != null ? p.getContent() : "").toLowerCase();
				List<String> found = words.stream().filter(content::contains).collect(Collectors.toList());
				if (!found.isEmpty()) {
					Map<String, Object> v = new LinkedHashMap<>();
					v.put("postId", p.getId());
					v.put("content", p.getContent());
					v.put("userId", p.getUserId());
					v.put("foundWords", found);
					violations.add(v);
				}
			}
			return Map.of("success", true, "violations", violations, "count", violations.size());
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// IP BAN SYSTEM
	// ========================
	@GetMapping("/admin/ip-ban")
	public String ipBanPage(Model model) {
		return "admin-ip-ban";
	}

	@GetMapping("/api/admin/ip-ban")
	@ResponseBody
	public List<Map<String, String>> getBannedIPs() {
		List<String> ips = redisTemplate.opsForList().range("admin:banned:ips", 0, -1);
		if (ips == null)
			return List.of();
		List<Map<String, String>> result = new ArrayList<>();
		for (String entry : ips) {
			String[] parts = entry.split("\\|");
			result.add(Map.of("ip", parts.length > 0 ? parts[0] : "", "reason", parts.length > 1 ? parts[1] : "",
					"time", parts.length > 2 ? parts[2] : ""));
		}

		return result;
	}

	@PostMapping("/api/admin/ip-ban")
	@ResponseBody
	public Map<String, Object> banIP(@RequestBody Map<String, String> body) {

		String ip = body.get("ip");
		String reason = body.getOrDefault("reason", "Manual ban");

		if (ip == null || ip.isBlank())
			return Map.of("success", false, "message", "IP empty nahi ho sakti");

		String ipClean = ip.trim();

		// Duplicate check
		List<String> existing = redisTemplate.opsForList().range("admin:banned:ips", 0, -1);

		if (existing != null && existing.stream().anyMatch(e -> e.startsWith(ip + "|"))) {
			return Map.of("success", false, "message", "Ye IP already banned hai");
		}

		String entry = ipClean + "|" + reason + "|"
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

		redisTemplate.opsForList().rightPush("admin:banned:ips", entry);

		// 🔥 important key for runtime blocking
		redisTemplate.opsForValue().set("admin:ip:banned:" + ipClean, reason);

		return Map.of("success", true, "message", ip + " banned successfully");
	}

	@DeleteMapping("/api/admin/ip-ban/{ip}")
	@ResponseBody
	public Map<String, Object> unbanIP(@PathVariable String ip) {
		try {
			String ipClean = ip.trim(); // ✅ new variable
			List<String> all = redisTemplate.opsForList().range("admin:banned:ips", 0, -1);
			if (all != null) {
				all.stream().filter(e -> e.startsWith(ipClean + "|")) // ✅ safe
						.forEach(e -> redisTemplate.opsForList().remove("admin:banned:ips", 1, e));
			}
			redisTemplate.delete("admin:ip:banned:" + ipClean);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// SEARCH ANALYTICS
	// ========================
	@GetMapping("/admin/search-analytics")
	public String searchAnalyticsPage(Model model) {
		return "admin-search-analytics";
	}

	@GetMapping("/api/admin/search-analytics")
	@ResponseBody
	public Map<String, Object> getSearchAnalytics() {
		Map<String, Object> data = new LinkedHashMap<>();
		try {
			// Top search terms from Redis sorted set
			Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> top = redisTemplate.opsForZSet()
					.reverseRangeWithScores("search:queries", 0, 29);
			List<Map<String, Object>> terms = new ArrayList<>();
			if (top != null) {
				for (var t : top) {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("term", t.getValue());
					m.put("count", t.getScore() != null ? t.getScore().longValue() : 0);
					terms.add(m);
				}
			}
			data.put("topSearches", terms);
			data.put("totalUniqueTerms", redisTemplate.opsForZSet().zCard("search:queries"));
			// Recent searches
			List<String> recent = redisTemplate.opsForList().range("search:recent", 0, 19);
			data.put("recentSearches", recent != null ? recent : new ArrayList<>());
			// Hashtag stats
			Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tags = redisTemplate.opsForZSet()
					.reverseRangeWithScores("trending:hashtags", 0, 9);
			List<Map<String, Object>> hashList = new ArrayList<>();
			if (tags != null) {
				for (var t : tags) {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("tag", t.getValue());
					m.put("score", t.getScore() != null ? t.getScore().longValue() : 0);
					hashList.add(m);
				}
			}
			data.put("trendingHashtags", hashList);
		} catch (Exception e) {
			data.put("error", e.getMessage());
		}
		return data;
	}

	// ========================
	// SCHEDULED TASKS
	// ========================
	@GetMapping("/admin/scheduled-tasks")
	public String scheduledTasksPage(Model model) {
		return "admin-scheduled-tasks";
	}

	@GetMapping("/api/admin/tasks")
	@ResponseBody
	public List<Map<String, Object>> getScheduledTasks() {

		Set<String> keys = scanKeys("task:*:meta");

		List<Map<String, Object>> list = new ArrayList<>();

		if (keys == null)
			return list;

		ObjectMapper mapper = new ObjectMapper();
		for (String metaKey : keys) {
			try {
				String json = redisTemplate.opsForValue().get(metaKey);
				if (json == null)
					continue;
				Map<String, Object> meta = mapper.readValue(json, Map.class);
				String base = metaKey.replace(":meta", "");
				String lastRun = redisTemplate.opsForValue().get(base + ":last_run");
				String status = redisTemplate.opsForValue().get(base + ":status");

				meta.put("lastRun", lastRun != null ? lastRun : "-");
				meta.put("status", status != null ? status : "idle");
				list.add(meta);
			} catch (Exception ignored) {
			}
		}
		return list;
	}

	@PostMapping("/api/admin/tasks/run/{taskId}")
	@ResponseBody
	public Map<String, Object> runTask(@PathVariable String taskId) {

		try {

			String key = "task:" + taskId; // ✅ clean key
			String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

			redisTemplate.opsForValue().set(key + ":status", "running");

			String result = "";

			switch (taskId) {

			case "cleanup:old_sessions":
				result = "Sessions cleanup triggered";
				break;

			case "recalc:trending":
				result = "Trending recalculation done";
				break;

			case "purge:expired_bans":
				Set<String> keys = scanKeys("admin:ip:banned:*");
				result = "Checked " + (keys != null ? keys.size() : 0) + " bans";
				break;

			case "notify:inactive_users":
				result = "Notification job triggered";
				break;

			default:
				result = "Unknown task";
			}

			redisTemplate.opsForValue().set(key + ":last_run", now);
			redisTemplate.opsForValue().set(key + ":status", "completed");
			redisTemplate.opsForValue().set(key + ":last_result", result);

			return Map.of("success", true, "message", result, "time", now);

		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/api/admin/tasks")
	@ResponseBody
	public Map<String, Object> createTask(@RequestBody Map<String, String> body) {
		try {
			String id = body.get("id"); // e.g. "custom:cleanup"
			String name = body.get("name");
			String desc = body.get("description");
			String cron = body.getOrDefault("cron", "manual");

			if (id == null || id.isBlank()) {
				return Map.of("success", false, "message", "Task id required");
			}

			String key = "task:" + id;

			// ❌ duplicate check
			if (Boolean.TRUE.equals(redisTemplate.hasKey(key + ":meta"))) {
				return Map.of("success", false, "message", "Task already exists");
			}

			Map<String, String> meta = new HashMap<>();
			meta.put("id", id);
			meta.put("name", name);
			meta.put("description", desc);
			meta.put("cron", cron);

			// 🔥 store meta as JSON
			String json = new ObjectMapper().writeValueAsString(meta);
			redisTemplate.opsForValue().set(key + ":meta", json);

			// default status
			redisTemplate.opsForValue().set(key + ":status", "idle");

			return Map.of("success", true);

		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// CONTENT MODERATION AI
	// ========================
	@GetMapping("/admin/moderation")
	public String moderationPage(Model model) {
		return "admin-moderation";
	}

	@PostMapping("/api/admin/moderation/scan")
	@ResponseBody
	public Map<String, Object> aiModerationScan(@RequestBody Map<String, Object> body) {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			int limit = (int) body.getOrDefault("limit", 20);
			List<Post> posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
					.getContent().stream().limit(limit).collect(Collectors.toList());
			List<Map<String, Object>> flagged = new ArrayList<>();
			List<String> bannedWords = redisTemplate.opsForList().range("admin:banned:words", 0, -1);
			if (bannedWords == null)
				bannedWords = new ArrayList<>();

			for (Post post : posts) {
				String content2 = post.getContent() != null ? post.getContent().toLowerCase() : "";
				int score = 0;
				List<String> reasons = new ArrayList<>();

				// Banned words check
				for (String word : bannedWords) {
					if (content2.contains(word.toLowerCase())) {
						score += 40;
						reasons.add("Banned word: " + word);
					}
				}
				// Spam patterns
				if (content2.matches(".*(.+)\1{4,}.*")) {
					score += 20;
					reasons.add("Repetitive text");
				}
				if (content2.length() > 0 && content2.chars().filter(c -> c == '!').count() > 5) {
					score += 15;
					reasons.add("Excessive exclamation");
				}
				if (content2.contains("http://") || content2.contains("https://")) {
					score += 10;
					reasons.add("External link");
				}
				if (content2.length() > 500) {
					score += 10;
					reasons.add("Very long content");
				}

				if (score >= 20) {
					Map<String, Object> f = new LinkedHashMap<>();
					f.put("postId", post.getId());
					f.put("content", post.getContent());
					f.put("userId", post.getUserId());
					f.put("score", score);
					f.put("reasons", reasons);
					f.put("severity", score >= 60 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW");
					// User info
					if (post.getUserId() != null) {
						userRepository.findById(post.getUserId()).ifPresent(u -> f.put("userName", u.getName()));
					}
					flagged.add(f);
				}
			}
			flagged.sort((a, b) -> (int) (Integer) b.get("score") - (int) (Integer) a.get("score"));
			result.put("success", true);
			result.put("scanned", posts.size());
			result.put("flagged", flagged);
			result.put("flaggedCount", flagged.size());
			audit("AI_MODERATION_SCAN", "Scanned " + posts.size() + " posts, flagged " + flagged.size());
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	@GetMapping("/api/admin/moderation/stats")
	@ResponseBody
	public Map<String, Object> getModerationStats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		try {
			List<String> history = redisTemplate.opsForList().range("admin:moderation:history", 0, 49);
			stats.put("scanHistory", history != null ? history : new ArrayList<>());
			Long totalFlagged = redisTemplate.opsForValue().get("admin:moderation:totalFlagged") != null
					? Long.parseLong(redisTemplate.opsForValue().get("admin:moderation:totalFlagged"))
					: 0L;
			stats.put("totalFlagged", totalFlagged);
			List<String> bannedWords = redisTemplate.opsForList().range("admin:banned:words", 0, -1);
			stats.put("activeBannedWords", bannedWords != null ? bannedWords.size() : 0);
		} catch (Exception e) {
			stats.put("error", e.getMessage());
		}
		return stats;
	}

	// ========================
	// USER ENGAGEMENT HEATMAP
	// ========================
	@GetMapping("/admin/heatmap")
	public String heatmapPage(Model model) {
		return "admin-heatmap";
	}

	@GetMapping("/api/admin/heatmap")
	@ResponseBody
	public Map<String, Object> getEngagementHeatmap(@RequestParam(defaultValue = "30") int days) {
		Map<String, Object> data = new LinkedHashMap<>();
		try {
			LocalDateTime startDate = LocalDateTime.now().minusDays(days);

			// Hour x DayOfWeek matrix (7 days x 24 hours)
			int[][] matrix = new int[7][24];
			List<Object[]> hourly = engagementRepository.getHourlyDistribution();
			// Fill daily stats
			List<Object[]> daily = engagementRepository.getDailyEngagementStats(startDate);
			data.put("dailyLabels", daily.stream().map(r -> r[0].toString()).collect(Collectors.toList()));
			data.put("dailyValues", daily.stream().map(r -> ((Number) r[1]).longValue()).collect(Collectors.toList()));

			// Hourly by day (0=Mon..6=Sun)
			List<Object[]> allEngagements = engagementRepository
					.getDailyEngagementStats(LocalDateTime.now().minusDays(Math.min(days, 90)));

			// Build 24-hour distribution
			List<Long> hourDist = new ArrayList<>();
			for (Object[] row : hourly) {
				hourDist.add(((Number) row[1]).longValue());
			}
			// Pad to 24 hours
			while (hourDist.size() < 24)
				hourDist.add(0L);
			data.put("hourlyDist", hourDist);

			// Peak hour
			int peakHour = 0;
			long peakVal = 0;
			for (int h = 0; h < hourDist.size(); h++) {
				if (hourDist.get(h) > peakVal) {
					peakVal = hourDist.get(h);
					peakHour = h;
				}
			}
			data.put("peakHour", peakHour);
			data.put("peakHourLabel", String.format("%02d:00-%02d:00", peakHour, (peakHour + 1) % 24));

			// Total stats
			long total = hourDist.stream().mapToLong(Long::longValue).sum();
			data.put("totalEngagements", total);
			data.put("days", days);
		} catch (Exception e) {
			data.put("error", e.getMessage());
		}
		return data;
	}

	// ========================
	// WEBSOCKET ADMIN ALERTS
	// ========================
	@GetMapping("/api/admin/alerts/recent")
	@ResponseBody
	public List<Map<String, Object>> getRecentAlerts() {
		List<Map<String, Object>> alerts = new ArrayList<>();
		try {
			// Pending reports
			long pendingReports = countPendingReports();
			if (pendingReports > 0) {
				Map<String, Object> a = new LinkedHashMap<>();
				a.put("type", "REPORT");
				a.put("message", pendingReports + " pending reports");
				a.put("severity", pendingReports > 5 ? "HIGH" : "MEDIUM");
				a.put("count", pendingReports);
				alerts.add(a);
			}
			// Blocked users
			long blocked = userRepository.countByEnabledFalse();
			if (blocked > 0) {
				Map<String, Object> a = new LinkedHashMap<>();
				a.put("type", "BLOCKED_USERS");
				a.put("message", blocked + " users blocked");
				a.put("severity", "LOW");
				a.put("count", blocked);
				alerts.add(a);
			}
			// Hidden posts
			Long hiddenCount = redisTemplate.opsForSet().size(RedisKeys.ADMIN_HIDDEN_POSTS_SET);
			if (hiddenCount != null && hiddenCount > 0) {
				Map<String, Object> a = new LinkedHashMap<>();
				a.put("type", "HIDDEN_POSTS");
				a.put("message", hiddenCount + " posts hidden");
				a.put("severity", "LOW");
				a.put("count", hiddenCount);
				alerts.add(a);
			}
			// Recent audit actions
			List<String> recentAudit = redisTemplate.opsForList().range("admin:audit:log", 0, 4);
			if (recentAudit != null && !recentAudit.isEmpty()) {
				Map<String, Object> a = new LinkedHashMap<>();
				a.put("type", "RECENT_ACTIONS");
				a.put("message", "Last action: see audit log");
				a.put("severity", "INFO");
				a.put("recent", recentAudit.get(0));
				alerts.add(a);
			}
		} catch (Exception e) {
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("type", "ERROR");
			err.put("message", e.getMessage());
			err.put("severity", "LOW");
			alerts.add(err);
		}
		return alerts;
	}

	// ========================
	// 1. PLATFORM SETTINGS (Feature Flags)
	// ========================
	@GetMapping("/admin/settings")
	public String settingsPage(Model model) {
		return "admin-settings";
	}

	@GetMapping("/api/admin/settings")
	@ResponseBody
	public Map<String, Object> getSettings() {
		Map<String, Object> settings = new LinkedHashMap<>();
		String[] keys = { "feature:registration", "feature:video_upload", "feature:comments", "feature:dm",
				"feature:stories", "feature:analytics_public", "feature:push_notifications", "feature:search",
				"feature:follow", "maintenance:mode", "maintenance:message", "limit:max_upload_size_mb",
				"limit:max_bio_length", "limit:max_comment_length", "ui:show_trending", "ui:show_suggestions",
				"ui:dark_mode_default" };
		String[] defaults = { "true", "true", "true", "true", "true", "false", "true", "true", "true", "false",
				"Platform is under maintenance. Back soon!", "100", "150", "500", "true", "true", "false" };
		for (int i = 0; i < keys.length; i++) {
			String val = redisTemplate.opsForValue().get("admin:settings:" + keys[i]);
			settings.put(keys[i], val != null ? val : defaults[i]);
		}
		return settings;
	}

	@PostMapping("/api/admin/settings")
	@ResponseBody
	public Map<String, Object> updateSetting(@RequestBody Map<String, String> body) {
		try {
			String key = body.get("key");
			String value = body.get("value");
			if (key == null || value == null)
				return Map.of("success", false, "message", "Key/value missing");
			redisTemplate.opsForValue().set("admin:settings:" + key, value);
			audit("SETTING_CHANGE", "Changed " + key + " = " + value);
			return Map.of("success", true, "message", key + " updated");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/api/admin/settings/reset")
	@ResponseBody
	public Map<String, Object> resetSettings() {
		try {
			Set<String> keys = scanKeys("admin:settings:*");
			if (keys != null)
				redisTemplate.delete(keys);
			audit("SETTINGS_RESET", "All settings reset to defaults");
			return Map.of("success", true, "message", "Settings reset to defaults");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// 2. CUSTOM ALERTS & THRESHOLDS
	// ========================
	@GetMapping("/admin/alert-config")
	public String alertConfigPage(Model model) {
		return "admin-alert-config";
	}

	@GetMapping("/api/admin/alert-config")
	@ResponseBody
	public Map<String, Object> getAlertConfig() {
		Map<String, Object> config = new LinkedHashMap<>();
		String[] thresholdKeys = { "threshold:heap_warn", "threshold:heap_crit", "threshold:cpu_warn",
				"threshold:reports_warn", "threshold:db_ping_warn", "threshold:threads_warn" };
		String[] thresholdDefaults = { "75", "90", "80", "5", "200", "200" };
		for (int i = 0; i < thresholdKeys.length; i++) {
			String val = redisTemplate.opsForValue().get("admin:alert:" + thresholdKeys[i]);
			config.put(thresholdKeys[i], val != null ? val : thresholdDefaults[i]);
		}
		// Alert channels
		String emailAlert = redisTemplate.opsForValue().get("admin:alert:email");
		config.put("alert:email", emailAlert != null ? emailAlert : "");
		String alertsEnabled = redisTemplate.opsForValue().get("admin:alert:enabled");
		config.put("alert:enabled", alertsEnabled != null ? alertsEnabled : "true");
		return config;
	}

	@PostMapping("/api/admin/alert-config")
	@ResponseBody
	public Map<String, Object> updateAlertConfig(@RequestBody Map<String, String> body) {
		try {
			body.forEach((k, v) -> redisTemplate.opsForValue().set("admin:alert:" + k, v));
			audit("ALERT_CONFIG", "Updated alert config: " + body.keySet());
			return Map.of("success", true, "message", "Alert config updated");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// 3. A/B TESTING MANAGER
	// ========================
	@GetMapping("/admin/ab-testing")
	public String abTestingPage(Model model) {
		return "admin-ab-testing";
	}

	// ========================
	// 4. BULK USER ACTIONS
	// ========================
	@PostMapping("/api/admin/users/bulk")
	@ResponseBody
	public Map<String, Object> bulkUserAction(@RequestBody Map<String, Object> body) {
		try {
			String action = (String) body.get("action");
			List<Integer> ids = (List<Integer>) body.get("ids");
			if (ids == null || ids.isEmpty())
				return Map.of("success", false, "message", "Koi user select nahi kiya");
			int success = 0;
			for (Integer rawId : ids) {
				Long id = Long.valueOf(rawId);
				try {
					User user = userRepository.findById(id).orElse(null);
					if (user == null)
						continue;
					switch (action) {
					case "block":
						user.setEnabled(false);
						userRepository.save(user);
						audit("USER_BLOCK", "Bulk block user id=" + id);
						break;
					case "unblock":
						user.setEnabled(true);
						userRepository.save(user);
						audit("USER_UNBLOCK", "Bulk unblock user id=" + id);
						break;
					case "delete":
						List<Post> posts = postRepository.findByUserId(id);
						for (Post p : posts) {
							commentRepository.deleteAll(commentRepository.findByPostIdOrderByCreatedAtDesc(p.getId()));
							postRepository.delete(p);
						}
						userRepository.delete(user);
						audit("USER_DELETE", "Bulk delete user id=" + id);
						break;
					case "promote":
						user.setRole("ADMIN");
						userRepository.save(user);
						audit("ROLE_ASSIGN", "Bulk promote user id=" + id);
						break;
					case "demote":
						user.setRole("USER");
						userRepository.save(user);
						audit("ROLE_ASSIGN", "Bulk demote user id=" + id);
						break;
					}
					success++;
				} catch (Exception ignored) {
				}
			}
			return Map.of("success", true, "message", success + " users " + action + " ho gaye", "count", success);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// 5. ADMIN LOGIN HISTORY
	// ========================
	@GetMapping("/admin/login-history")
	public String loginHistoryPage(Model model) {
		return "admin-login-history";
	}

	@GetMapping("/api/admin/login-history")
	@ResponseBody
	public Map<String, Object> getLoginHistory() {

		Map<String, Object> response = new HashMap<>();

		try {
			String key = "admin:login:history";

			List<String> entries = redisTemplate.opsForList().range(key, 0, -1);
			if (entries == null)
				entries = new ArrayList<>();

			// ✅ FIX: active sessions from SET (not string size)
			Long activeSessions = redisTemplate.opsForSet().size("admin:active:sessions");

			response.put("entries", entries);
			response.put("total", entries.size());
			response.put("activeSessions", activeSessions != null ? activeSessions : 0);

		} catch (Exception e) {
			response.put("error", "Failed to load login history");
		}

		return response;
	}

	@PostMapping("/api/admin/login-history/record")
	@ResponseBody
	public Map<String, Object> recordLogin(@RequestBody Map<String, String> body) {
		try {
			String email = body.getOrDefault("email", "unknown");
			String ip = body.getOrDefault("ip", "unknown");
			String ua = body.getOrDefault("userAgent", "unknown");
			String entry = "{" + "\"time\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\","
					+ "\"email\":\"" + email.replace("\"", "") + "\"," + "\"ip\":\"" + ip.replace("\"", "") + "\","
					+ "\"userAgent\":\"" + ua.replace("\"", "").substring(0, Math.min(80, ua.length())) + "\"" + "}";
			redisTemplate.opsForList().leftPush("admin:login:history", entry);
			redisTemplate.opsForList().trim("admin:login:history", 0, 199);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	// ========================
	// 6. SUPPORT CHAT PANEL
	// ========================
	@GetMapping("/admin/support")
	public String supportPage(Model model) {
		return "admin-support";
	}

	@GetMapping("/api/admin/support/tickets")
	@ResponseBody
	public List<Map<String, Object>> getSupportTickets() {
		List<Map<String, Object>> tickets = new ArrayList<>();
		try {
			List<String> ticketIds = redisTemplate.opsForList().range("admin:support:tickets", 0, -1);
			if (ticketIds == null)
				return tickets;
			for (String id : ticketIds) {
				String data = redisTemplate.opsForValue().get("admin:support:ticket:" + id);
				if (data != null) {
					Map<String, Object> t = new LinkedHashMap<>();
					t.put("id", id);
					t.put("data", data);
					List<String> msgs = redisTemplate.opsForList().range("admin:support:msgs:" + id, 0, -1);
					t.put("messageCount", msgs != null ? msgs.size() : 0);
					t.put("messages",
							msgs != null ? msgs.subList(Math.max(0, msgs.size() - 3), msgs.size()) : new ArrayList<>());
					tickets.add(t);
				}
			}
		} catch (Exception e) {
			/* ignore */ }
		return tickets;
	}

	@PostMapping("/api/admin/support/ticket")
	@ResponseBody
	public Map<String, Object> createSupportTicket(@RequestBody Map<String, String> body) {
		try {
			String userId = body.get("userId");
			String subject = body.get("subject");
			String message = body.get("message");
			if (userId == null || subject == null)
				return Map.of("success", false, "message", "userId and subject required");
			String id = "ticket-" + System.currentTimeMillis();
			String data = "{" + "\"id\":\"" + id + "\"," + "\"userId\":" + userId + "," + "\"subject\":\""
					+ subject.replace("\"", "") + "\"," + "\"status\":\"open\"," + "\"created\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\"" + "}";
			redisTemplate.opsForValue().set("admin:support:ticket:" + id, data);
			redisTemplate.opsForList().leftPush("admin:support:tickets", id);
			if (message != null) {
				String msg = "{" + "\"from\":\"user\"," + "\"userId\":" + userId + "," + "\"text\":\""
						+ message.replace("\"", "") + "\"," + "\"time\":\""
						+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "\"" + "}";
				redisTemplate.opsForList().rightPush("admin:support:msgs:" + id, msg);
			}
			return Map.of("success", true, "ticketId", id);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/api/admin/support/reply/{ticketId}")
	@ResponseBody
	public Map<String, Object> replyToTicket(@PathVariable String ticketId, @RequestBody Map<String, String> body) {
		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			String adminEmail = auth != null ? auth.getName() : "admin";
			String text = body.get("text");
			if (text == null || text.isBlank())
				return Map.of("success", false, "message", "Reply empty nahi ho sakti");
			String msg = "{" + "\"from\":\"admin\"," + "\"admin\":\"" + adminEmail.replace("\"", "") + "\","
					+ "\"text\":\"" + text.replace("\"", "") + "\"," + "\"time\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "\"" + "}";
			redisTemplate.opsForList().rightPush("admin:support:msgs:" + ticketId, msg);
			audit("SUPPORT_REPLY", "Replied to ticket " + ticketId);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@PostMapping("/api/admin/support/close/{ticketId}")
	@ResponseBody
	public Map<String, Object> closeTicket(@PathVariable String ticketId) {
		try {
			String data = redisTemplate.opsForValue().get("admin:support:ticket:" + ticketId);
			if (data != null) {
				data = data.replace("\"status\":\"open\"", "\"status\":\"closed\"");
				redisTemplate.opsForValue().set("admin:support:ticket:" + ticketId, data);
			}
			audit("SUPPORT_CLOSE", "Closed ticket " + ticketId);
			return Map.of("success", true);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/admin/support/messages/{ticketId}")
	@ResponseBody
	public List<String> getTicketMessages(@PathVariable String ticketId) {
		List<String> msgs = redisTemplate.opsForList().range("admin:support:msgs:" + ticketId, 0, -1);
		return msgs != null ? msgs : new ArrayList<>();
	}

	// ========================
	// 7. REAL AI MODERATION (Claude API via frontend)
	// ========================
	@PostMapping("/api/admin/moderation/ai-scan")
	@ResponseBody
	public Map<String, Object> aiScanPost(@RequestBody Map<String, Object> body) {
		// This endpoint receives AI analysis result from frontend (Claude API called
		// client-side)
		// and stores the decision in Redis
		try {
			Object postIdObj = body.get("postId");
			String decision = (String) body.getOrDefault("decision", "clean");
			String reason = (String) body.getOrDefault("reason", "");
			int score = body.get("score") != null ? (int) body.get("score") : 0;
			if (postIdObj == null)
				return Map.of("success", false, "message", "postId required");
			Long postId = Long.parseLong(postIdObj.toString());

			// Store AI verdict in Redis
			String verdict = "{" + "\"postId\":" + postId + "," + "\"decision\":\"" + decision + "\"," + "\"reason\":\""
					+ reason.replace("\"", "") + "\"," + "\"score\":" + score + "," + "\"time\":\""
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\"" + "}";
			redisTemplate.opsForValue().set("admin:ai:verdict:" + postId, verdict);

			// Auto-action based on decision
			if ("flag".equals(decision) && score >= 80) {
				redisTemplate.opsForValue().set("admin:hidden:post:" + postId, "1");
				audit("AI_AUTO_HIDE", "AI auto-hid post id=" + postId + " score=" + score);
			}

			// Track total flagged
			redisTemplate.opsForList().leftPush("admin:moderation:history", verdict);
			redisTemplate.opsForList().trim("admin:moderation:history", 0, 99);

			return Map.of("success", true, "action", score >= 80 ? "auto_hidden" : "flagged");
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	@GetMapping("/api/admin/moderation/ai-key-check")
	@ResponseBody
	public Map<String, Object> checkAiKey() {
		// Returns whether AI moderation is available (key configured in settings)
		String enabled = redisTemplate.opsForValue().get(RedisKeys.ADMIN_AI_MODERATION_FEATURE);
		return Map.of("enabled", "true".equals(enabled), "message",
				"true".equals(enabled) ? "AI moderation active" : "Set API key in settings to enable");
	}

	@PostMapping("/api/admin/moderation/ai-toggle")
	@ResponseBody
	public Map<String, Object> setAiModeration(@RequestBody Map<String, Object> body) {
		try {
			Object enabledObj = body.get("enabled");
			if (enabledObj == null) {
				return Map.of("success", false, "message", "enabled required");
			}
			boolean enabled = Boolean.parseBoolean(String.valueOf(enabledObj));
			redisTemplate.opsForValue().set(RedisKeys.ADMIN_AI_MODERATION_FEATURE, String.valueOf(enabled));
			audit("AI_MODERATION_TOGGLE", "AI moderation " + (enabled ? "enabled" : "disabled"));
			return Map.of("success", true, "enabled", enabled);
		} catch (Exception e) {
			return Map.of("success", false, "message", e.getMessage());
		}
	}

	private Set<String> scanKeys(String pattern) {
		try {
			org.springframework.data.redis.core.RedisCallback<Set<String>> callback = connection -> {
				Set<String> keys = new HashSet<>();
				try (Cursor<byte[]> cursor = connection.scan(
						ScanOptions.scanOptions().match(pattern).count(1000).build())) {
					while (cursor.hasNext()) {
						byte[] raw = cursor.next();
						if (raw != null && raw.length > 0) {
							keys.add(new String(raw, StandardCharsets.UTF_8));
						}
					}
				}
				return keys;
			};
			return redisTemplate.execute(callback);
		} catch (Exception e) {
			return Collections.emptySet();
		}
	}

}
