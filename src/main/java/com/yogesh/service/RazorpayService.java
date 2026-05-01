package com.yogesh.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogesh.model.WithdrawalRequest;
import com.yogesh.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

	private static final String BASE_URL = "https://api.razorpay.com/v1";

	private final WithdrawalRequestRepository withdrawalRepo;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${razorpay.key-id:}")
	private String keyId;

	@Value("${razorpay.key-secret:}")
	private String keySecret;

	@Value("${razorpay.account-id:}")
	private String accountId;

	public Map<String, Object> createOrder(BigDecimal amountInRupees, String currency, String receiptId, String notes) {
		if (!isConfigured()) {
			return errorMap("Razorpay configure nahi hai");
		}
		if (amountInRupees == null || amountInRupees.compareTo(BigDecimal.ZERO) <= 0) {
			return errorMap("Amount invalid hai");
		}

		try {
			long amountPaise = amountInRupees.movePointRight(2).longValueExact();
			String payload = objectMapper.writeValueAsString(Map.of("amount", amountPaise, "currency", currency,
					"receipt", receiptId, "notes", Map.of("info", notes == null ? "" : notes)));

			HttpResponse<String> response = post("/orders", payload);
			if (response.statusCode() != 200) {
				log.error("Order create failed: {} {}", response.statusCode(), response.body());
				return errorMap("Order create nahi hua: " + response.statusCode());
			}

			Map<String, Object> result = parseJson(response.body());
			result.put("keyId", keyId);
			return result;
		} catch (ArithmeticException e) {
			return errorMap("Amount do decimal places tak hi allowed hai");
		} catch (Exception e) {
			log.error("Razorpay order error", e);
			return errorMap(e.getMessage());
		}
	}

	public boolean verifyPayment(String orderId, String paymentId, String signature) {
		if (!isConfigured() || isBlank(orderId) || isBlank(paymentId) || isBlank(signature)) {
			return false;
		}

		try {
			String data = orderId + "|" + paymentId;
			String expectedSignature = hmacSHA256(data, keySecret);
			return expectedSignature.equals(signature);
		} catch (Exception e) {
			log.error("Payment verification failed", e);
			return false;
		}
	}

	public Map<String, Object> processWithdrawal(Long withdrawalRequestId) {
		if (!isConfigured()) {
			log.warn("Razorpay not configured; withdrawal cannot be processed");
			return errorMap("Payment gateway configure nahi hai");
		}

		WithdrawalRequest request = withdrawalRepo.findById(withdrawalRequestId).orElse(null);
		if (request == null) {
			return errorMap("Withdrawal request nahi mila");
		}
		if (!"PENDING".equals(request.getStatus())) {
			return errorMap("Already processed");
		}

		try {
			long amountPaise = request.getAmount().movePointRight(2).longValueExact();
			Map<String, Object> payload = Map.of("account_number", accountId, "fund_account",
					Map.of("account_type", "vpa", "vpa", Map.of("address", request.getUpiId()), "contact",
							Map.of("name", "Creator", "type", "vendor")),
					"amount", amountPaise, "currency", "INR", "mode", "UPI", "purpose", "payout",
					"queue_if_low_balance", true, "narration", "AI Feed Creator Earnings", "reference_id",
					"WITHDRAW_" + request.getId());

			HttpResponse<String> response = post("/payouts", objectMapper.writeValueAsString(payload));
			if (response.statusCode() != 200 && response.statusCode() != 201) {
				log.error("Payout failed: {} {}", response.statusCode(), response.body());
				return errorMap("Payout failed: " + response.statusCode());
			}

			Map<String, Object> result = parseJson(response.body());
			String razorpayId = String.valueOf(result.get("id"));

			request.setStatus("PROCESSING");
			request.setProcessedAt(LocalDateTime.now());
			withdrawalRepo.save(request);

			return Map.of("success", true, "razorpayId", razorpayId, "message",
					"Payout initiated - 24 hours mein UPI mein aayega");
		} catch (Exception e) {
			log.error("Payout error", e);
			return errorMap(e.getMessage());
		}
	}

	public boolean verifyWebhookSignature(String payload, String signature, String secret) {
		if (isBlank(payload) || isBlank(signature) || isBlank(secret)) {
			return false;
		}
		try {
			return hmacSHA256(payload, secret).equals(signature);
		} catch (Exception e) {
			return false;
		}
	}

	public Map<String, Object> getPayment(String paymentId) {
		if (!isConfigured()) {
			return errorMap("Not configured");
		}
		if (isBlank(paymentId)) {
			return errorMap("Payment id missing hai");
		}

		try {
			HttpResponse<String> response = get("/payments/" + paymentId);
			if (response.statusCode() != 200) {
				log.error("Payment fetch failed: {} {}", response.statusCode(), response.body());
				return errorMap("Payment fetch failed: " + response.statusCode());
			}
			return parseJson(response.body());
		} catch (Exception e) {
			log.error("Payment fetch error", e);
			return errorMap(e.getMessage());
		}
	}

	public boolean isConfigured() {
		return !isBlank(keyId) && !isBlank(keySecret);
	}

	public String getKeyId() {
		return keyId;
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return httpClient.send(HttpRequest.newBuilder().uri(URI.create(BASE_URL + path))
				.header("Authorization", "Basic " + basicAuth()).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return httpClient.send(HttpRequest.newBuilder().uri(URI.create(BASE_URL + path))
				.header("Authorization", "Basic " + basicAuth()).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private String basicAuth() {
		return Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
	}

	private String hmacSHA256(String data, String secret) throws Exception {
		javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
		mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		StringBuilder builder = new StringBuilder();
		for (byte value : bytes) {
			builder.append(String.format("%02x", value));
		}
		return builder.toString();
	}

	private Map<String, Object> parseJson(String json) {
		if (isBlank(json)) {
			return new LinkedHashMap<>();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			log.warn("JSON parse failed, returning raw payload");
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("raw", json);
			return fallback;
		}
	}

	private Map<String, Object> errorMap(String message) {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("success", false);
		error.put("error", message);
		return error;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
