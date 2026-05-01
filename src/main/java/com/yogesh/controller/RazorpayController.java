package com.yogesh.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.MonetizationService;
import com.yogesh.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class RazorpayController {

	private static final String ORDER_CONTEXT_PREFIX = "payment:razorpay:order:";
	private static final Duration ORDER_CONTEXT_TTL = Duration.ofHours(12);
	private static final Duration VERIFIED_CONTEXT_TTL = Duration.ofDays(3);

	private final RazorpayService razorpayService;
	private final MonetizationService monetizationService;
	private final UserRepository userRepository;
	private final StringRedisTemplate redisTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@GetMapping("/config")
	public Map<String, Object> config() {
		return Map.of("configured", razorpayService.isConfigured(),
				"keyId", razorpayService.getKeyId() != null ? razorpayService.getKeyId() : "", "currency", "INR");
	}

	@PostMapping("/create-order")
	public Map<String, Object> createOrder(@RequestBody Map<String, Object> body, Authentication auth) {
		User me = currentUser(auth);
		try {
			BigDecimal amount = parseAmount(body.get("amount"));
			Long creatorId = parseOptionalLong(body.get("creatorId"));
			Long postId = parseOptionalLong(body.get("postId"));
			String notes = body.getOrDefault("notes", "AI Feed tip").toString().trim();

			if (creatorId != null) {
				if (creatorId.equals(me.getId())) {
					return Map.of("success", false, "error", "Khud ko tip nahi bhej sakte");
				}
				if (!userRepository.existsById(creatorId)) {
					return Map.of("success", false, "error", "Creator nahi mila");
				}
			}

			String receipt = "tip_" + me.getId() + "_" + System.currentTimeMillis();
			Map<String, Object> order = razorpayService.createOrder(amount, "INR", receipt, notes);
			if (Boolean.FALSE.equals(order.get("success"))) {
				return order;
			}

			String orderId = asString(order.get("id"));
			if (orderId == null) {
				return Map.of("success", false, "error", "Order create hua but order id missing hai");
			}

			Map<String, Object> context = new LinkedHashMap<>();
			context.put("payerUserId", me.getId());
			context.put("creatorId", creatorId);
			context.put("postId", postId);
			context.put("amount", amount.toPlainString());
			context.put("currency", "INR");
			context.put("notes", notes);
			context.put("status", "CREATED");
			context.put("createdAt", System.currentTimeMillis());
			writeOrderContext(orderId, context, ORDER_CONTEXT_TTL);

			return order;
		} catch (Exception e) {
			return Map.of("success", false, "error", "Invalid request: " + e.getMessage());
		}
	}

	@PostMapping("/verify")
	public Map<String, Object> verifyPayment(@RequestBody Map<String, String> body, Authentication auth) {
		User me = currentUser(auth);

		String orderId = body.get("razorpay_order_id");
		String paymentId = body.get("razorpay_payment_id");
		String signature = body.get("razorpay_signature");

		if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature)) {
			return Map.of("success", false, "message", "Missing fields");
		}
		if (monetizationService.hasProcessedExternalTransaction(paymentId)) {
			return Map.of("success", true, "message", "Payment already processed", "paymentId", paymentId);
		}

		Map<String, Object> context = readOrderContext(orderId);
		if (context.isEmpty()) {
			return Map.of("success", false, "message", "Order context missing ya expire ho gaya");
		}

		Long payerUserId = parseOptionalLong(context.get("payerUserId"));
		if (payerUserId == null || !payerUserId.equals(me.getId())) {
			return Map.of("success", false, "message", "Order aapke account ka nahi hai");
		}

		String verifiedPaymentId = asString(context.get("verifiedPaymentId"));
		if (paymentId.equals(verifiedPaymentId)) {
			return Map.of("success", true, "message", "Payment already processed", "paymentId", paymentId);
		}
		if ("VERIFIED".equals(asString(context.get("status"))) && !isBlank(verifiedPaymentId)
				&& !paymentId.equals(verifiedPaymentId)) {
			return Map.of("success", false, "message", "Order pehle hi kisi aur payment ke saath verify ho chuka hai");
		}

		if (!razorpayService.verifyPayment(orderId, paymentId, signature)) {
			return Map.of("success", false, "message", "Invalid payment signature");
		}

		Map<String, Object> payment = razorpayService.getPayment(paymentId);
		if (Boolean.FALSE.equals(payment.get("success"))) {
			return Map.of("success", false, "message", payment.getOrDefault("error", "Payment details nahi mile"));
		}
		if (!orderId.equals(asString(payment.get("order_id")))) {
			return Map.of("success", false, "message", "Payment order mismatch");
		}

		BigDecimal expectedAmount = new BigDecimal(context.get("amount").toString());
		Long actualAmountPaise = parseOptionalLong(payment.get("amount"));
		long expectedAmountPaise = expectedAmount.movePointRight(2).longValue();
		if (actualAmountPaise == null || actualAmountPaise != expectedAmountPaise) {
			return Map.of("success", false, "message", "Payment amount mismatch");
		}

		String paymentStatus = asString(payment.get("status"));
		if (!isBlank(paymentStatus)) {
			String normalized = paymentStatus.toLowerCase(Locale.ROOT);
			if (!normalized.equals("captured") && !normalized.equals("authorized")) {
				return Map.of("success", false, "message", "Payment state invalid: " + paymentStatus);
			}
		}

		Long creatorId = parseOptionalLong(context.get("creatorId"));
		Long postId = parseOptionalLong(context.get("postId"));
		boolean credited = false;
		if (creatorId != null) {
			BigDecimal creatorAmount = expectedAmount.multiply(new BigDecimal("0.80")).setScale(2,
					RoundingMode.HALF_UP);
			credited = monetizationService.recordTipEarning(creatorId, postId, creatorAmount, paymentId);
		}

		context.put("status", "VERIFIED");
		context.put("verifiedAt", System.currentTimeMillis());
		context.put("verifiedPaymentId", paymentId);
		writeOrderContext(orderId, context, VERIFIED_CONTEXT_TTL);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("message", creatorId != null && !credited ? "Payment already processed" : "Payment verified");
		response.put("paymentId", paymentId);
		response.put("credited", creatorId != null && credited);
		return response;
	}

	@PostMapping("/process-withdrawal/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Object> processWithdrawal(@PathVariable Long id, Authentication auth) {
		User me = currentUser(auth);
		if (!"ADMIN".equals(me.getRole())) {
			return Map.of("success", false, "message", "Unauthorized");
		}
		return razorpayService.processWithdrawal(id);
	}

	@PostMapping("/webhook")
	public Map<String, String> webhook(@RequestBody String payload,
			@RequestHeader(value = "X-Razorpay-Signature", defaultValue = "") String signature) {
		log.info("Razorpay webhook received; signaturePresent={}", !signature.isBlank());
		if (payload.contains("\"payment.captured\"")) {
			log.info("Payment captured webhook processed");
		} else if (payload.contains("\"payout.processed\"")) {
			log.info("Payout processed webhook received");
		}
		return Map.of("status", "ok");
	}

	private User currentUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}

	private BigDecimal parseAmount(Object raw) {
		if (raw == null) {
			throw new IllegalArgumentException("Amount required hai");
		}

		BigDecimal amount = new BigDecimal(raw.toString()).setScale(2, RoundingMode.HALF_UP);
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Amount positive hona chahiye");
		}
		return amount;
	}

	private Long parseOptionalLong(Object raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.toString().trim();
		if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
			return null;
		}
		return Long.parseLong(value);
	}

	private String asString(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : text;
	}

	private void writeOrderContext(String orderId, Map<String, Object> context, Duration ttl) {
		try {
			redisTemplate.opsForValue().set(ORDER_CONTEXT_PREFIX + orderId, objectMapper.writeValueAsString(context), ttl);
		} catch (Exception e) {
			throw new IllegalStateException("Order context store nahi ho paaya", e);
		}
	}

	private Map<String, Object> readOrderContext(String orderId) {
		try {
			String raw = redisTemplate.opsForValue().get(ORDER_CONTEXT_PREFIX + orderId);
			if (raw == null || raw.isBlank()) {
				return Map.of();
			}
			return objectMapper.readValue(raw, new TypeReference<>() {
			});
		} catch (Exception e) {
			log.warn("Failed to read Razorpay order context for {}", orderId, e);
			return Map.of();
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
