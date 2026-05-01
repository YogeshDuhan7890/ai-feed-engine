package com.yogesh.logincontroller;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.yogesh.admin.service.AbTestService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AbTestAdminController {

    private final AbTestService abTestService;
    private final StringRedisTemplate redisTemplate;

    // 🎯 Assign variant to user
    @GetMapping("/assign")
    public Map<String, Object> assignVariant(
            @RequestParam String testId,
            @RequestParam String userId) {

        String variant = abTestService.assignVariant(testId, userId);
        abTestService.trackExposure(testId, userId, variant);
        return Map.of("testId", testId, "userId", userId, "variant", variant);
    }
    
 

 	@GetMapping("/admin/ab-tests")
 	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
 	public List<Map<String, Object>> getAbTests() {
 		List<Map<String, Object>> tests = new ArrayList<>();
 		try {
 			List<String> testIds = redisTemplate.opsForList().range("admin:ab:tests", 0, -1);
 			if (testIds == null)
 				return tests;
 			for (String id : testIds) {
 				String data = redisTemplate.opsForValue().get("admin:ab:test:" + id);
 				if (data != null) {
 					Map<String, Object> t = new LinkedHashMap<>();
 					t.put("id", id);
 					t.put("data", data);
 					// Stats
 					Long varA = redisTemplate.opsForValue().get("admin:ab:" + id + ":a") != null
 							? Long.parseLong(redisTemplate.opsForValue().get("admin:ab:" + id + ":a"))
 							: 0L;
 					Long varB = redisTemplate.opsForValue().get("admin:ab:" + id + ":b") != null
 							? Long.parseLong(redisTemplate.opsForValue().get("admin:ab:" + id + ":b"))
 							: 0L;
 					t.put("variantA", varA);
 					t.put("variantB", varB);
 					t.put("total", varA + varB);
					t.put("results", abTestService.getResults(id));
					t.put("winner", redisTemplate.opsForValue().get("admin:ab:test:" + id + ":winner"));
 					tests.add(t);
 				}
 			}
 		} catch (Exception e) {
 			/* ignore */ }
 		return tests;
 	}

 	@PostMapping("/admin/ab-tests")
 	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
 	public Map<String, Object> createAbTest(@RequestBody Map<String, String> body) {
 		try {
 			String id = body.get("id");
 			String name = body.get("name");
 			String desc = body.get("description");
 			String varA = body.get("variantA");
 			String varB = body.get("variantB");
 			String split = body.getOrDefault("split", "50");
 			if (id == null || name == null)
 				return Map.of("success", false, "message", "ID and name required");
 			String data = "{" + "\"name\":\"" + name.replace("\"", "") + "\"," + "\"description\":\""
 					+ (desc != null ? desc.replace("\"", "") : "") + "\"," + "\"variantA\":\""
 					+ (varA != null ? varA.replace("\"", "") : "") + "\"," + "\"variantB\":\""
 					+ (varB != null ? varB.replace("\"", "") : "") + "\"," + "\"split\":" + split + ","
 					+ "\"status\":\"active\"," + "\"created\":\""
 					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\"" + "}";
 			redisTemplate.opsForValue().set("admin:ab:test:" + id, data);
 			redisTemplate.opsForList().rightPush("admin:ab:tests", id);
 			audit("AB_TEST_CREATE", "Created A/B test: " + id + " name=" + name);
 			return Map.of("success", true, "message", "A/B test '" + name + "' created");
 		} catch (Exception e) {
 			return Map.of("success", false, "message", e.getMessage());
 		}
 	}

 	@DeleteMapping("/api/admin/ab-tests/{id}")
 	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
 	public Map<String, Object> deleteAbTest(@PathVariable String id) {
 		try {
 			redisTemplate.delete("admin:ab:test:" + id);
 			redisTemplate.opsForList().remove("admin:ab:tests", 1, id);
 			audit("AB_TEST_DELETE", "Deleted A/B test: " + id);
 			return Map.of("success", true);
 		} catch (Exception e) {
 			return Map.of("success", false, "message", e.getMessage());
 		}
 	}

	@PostMapping("/ab/convert")
	public Map<String, Object> trackConversion(
			@RequestParam String testId,
			@RequestParam String userId,
			@RequestParam(required = false) String variant) {
		String v = variant;
		if (v == null || v.isBlank()) {
			v = abTestService.assignVariant(testId, userId);
		}
		abTestService.trackConversion(testId, userId, v);
		return Map.of("success", true, "testId", testId, "userId", userId, "variant", v);
	}

	@GetMapping("/admin/ab-tests/{id}/results")
	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Object> getResults(@PathVariable String id) {
		return abTestService.getResults(id);
	}

	@PostMapping("/admin/ab-tests/{id}/winner")
	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Object> pickWinner(@PathVariable String id,
			@RequestParam(defaultValue = "5") double minLiftPct,
			@RequestParam(defaultValue = "100") long minSamplePerVariant) {
		return abTestService.chooseWinner(id, minLiftPct, minSamplePerVariant);
	}
 	
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
 	
}
