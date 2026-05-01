package com.yogesh.service;

import com.yogesh.model.PushSubscription;
import com.yogesh.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

	private static final String VAPID_PUBLIC_KEY = "push:vapid:public";
	private static final String VAPID_PRIVATE_KEY = "push:vapid:private";
	private static final String VAPID_SUBJECT_KEY = "push:vapid:subject";

	private final PushSubscriptionRepository subscriptionRepository;
	private final StringRedisTemplate redisTemplate;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Value("${push.vapid.public-key:}")
	private String vapidPublicKey;

	@Value("${push.vapid.private-key:}")
	private String vapidPrivateKey;

	@Value("${push.vapid.subject:mailto:admin@aifeed.com}")
	private String vapidSubject;

	// ── Subscribe ─────────────────────────────────────────────────
	@Transactional
	public void subscribe(Long userId, String endpoint, String p256dh, String authKey) {
		if (subscriptionRepository.existsByUserIdAndEndpoint(userId, endpoint))
			return;
		PushSubscription sub = new PushSubscription();
		sub.setUserId(userId);
		sub.setEndpoint(endpoint);
		sub.setP256dh(p256dh);
		sub.setAuthKey(authKey);
		subscriptionRepository.save(sub);
		log.info("Push subscription saved: userId={}", userId);
	}

	// ── Unsubscribe ───────────────────────────────────────────────
	@Transactional
	public void unsubscribe(String endpoint) {
		subscriptionRepository.deleteByEndpoint(endpoint);
	}

	// ── Send to specific user ─────────────────────────────────────
	public void sendToUser(Long userId, String title, String body, String url) {
		List<PushSubscription> subs = subscriptionRepository.findByUserId(userId);
		for (PushSubscription sub : subs) {
			try {
				sendPush(sub, title, body, url);
			} catch (Exception e) {
				log.warn("Push failed userId={}: {}", userId, e.getMessage());
			}
		}
	}

	// ── Notification helpers ──────────────────────────────────────
	public void notifyNewFollower(Long followedUserId, String followerName) {
		sendToUser(followedUserId, "New Follower! 🎉", followerName + " ne aapko follow kiya", "/profile");
	}

	public void notifyLike(Long postOwnerId, String likerName, Long postId) {
		sendToUser(postOwnerId, "Naya Like ❤️", likerName + " ne aapki video like ki", "/feed");
	}

	public void notifyComment(Long postOwnerId, String commenterName, Long postId) {
		sendToUser(postOwnerId, "Naya Comment 💬", commenterName + " ne comment kiya", "/feed");
	}

	public void notifyMessage(Long receiverId, String senderName) {
		sendToUser(receiverId, "Naya Message ✉️", senderName + " ne message bheja", "/messages");
	}

	// ── Broadcast to all ─────────────────────────────────────────
	public int broadcastToAll(String title, String body, String url) {
		// Paginated broadcast — sabko ek saath load na karo
		List<PushSubscription> all = subscriptionRepository
				.findAll(org.springframework.data.domain.PageRequest.of(0, 500)).getContent();
		int count = 0;
		for (PushSubscription sub : all) {
			try {
				sendPush(sub, title, body, url);
				count++;
			} catch (Exception e) {
				log.warn("Broadcast fail userId={}: {}", sub.getUserId(), e.getMessage());
			}
		}
		log.info("Broadcast sent to {} subscribers", count);
		return count;
	}

	// ── Config helpers ────────────────────────────────────────────
	public boolean isVapidConfigured() {
		return !resolvePrivateKey().isBlank() && !resolvePublicKey().isBlank();
	}

	public String getVapidPublicKey() {
		return resolvePublicKey();
	}

	public String getVapidSubject() {
		return resolveSubject();
	}

	@Transactional
	public Map<String, String> saveRuntimeVapidKeys(String publicKey, String privateKey, String subject) {
		String normalizedPublic = publicKey == null ? "" : publicKey.trim();
		String normalizedPrivate = privateKey == null ? "" : privateKey.trim();
		String normalizedSubject = (subject == null || subject.isBlank()) ? resolveSubject() : subject.trim();
		if (normalizedPublic.isBlank() || normalizedPrivate.isBlank()) {
			throw new IllegalArgumentException("VAPID public/private key required");
		}
		redisTemplate.opsForValue().set(VAPID_PUBLIC_KEY, normalizedPublic);
		redisTemplate.opsForValue().set(VAPID_PRIVATE_KEY, normalizedPrivate);
		redisTemplate.opsForValue().set(VAPID_SUBJECT_KEY, normalizedSubject);
		return Map.of("publicKey", normalizedPublic, "privateKey", normalizedPrivate, "subject", normalizedSubject);
	}

	public long getTotalSubscriptions() {
		return subscriptionRepository.count();
	}

	public long getActiveSubscriberCount() {
		return subscriptionRepository.countDistinctUserIds();
	}

	// ══════════════════════════════════════════════════════════════
	// CORE: Send Web Push Notification (RFC 8291 — VAPID + AESGCM)
	// ══════════════════════════════════════════════════════════════
	private void sendPush(PushSubscription sub, String title, String body, String url) {
		if (!isVapidConfigured()) {
			log.warn("VAPID keys nahi hain — push skip. application.yml mein configure karo.");
			return;
		}
		try {
			String publicKey = resolvePublicKey();
			String payload = buildPayload(title, body, url);
			String jwt = buildVapidJwt(sub.getEndpoint());
			String authHeader = "vapid t=" + jwt + ",k=" + publicKey;

			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(sub.getEndpoint()))
					.header("Authorization", authHeader).header("Content-Type", "application/octet-stream")
					.header("TTL", "86400").POST(HttpRequest.BodyPublishers
							.ofByteArray(encryptPayload(payload, sub.getP256dh(), sub.getAuthKey())))
					.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

			if (resp.statusCode() == 410 || resp.statusCode() == 404) {
				// Subscription expired — delete it
				log.info("Push subscription expired ({}), removing userId={}", resp.statusCode(), sub.getUserId());
				subscriptionRepository.deleteByEndpoint(sub.getEndpoint());
			} else if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
				log.debug("Push sent userId={} status={}", sub.getUserId(), resp.statusCode());
			} else {
				log.warn("Push failed status={} userId={}", resp.statusCode(), sub.getUserId());
			}
		} catch (Exception e) {
			log.error("Push send error userId={}: {}", sub.getUserId(), e.getMessage());
		}
	}

	// ── Build JSON payload ────────────────────────────────────────
	private String buildPayload(String title, String body, String url) {
		return String.format(
				"{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\",\"icon\":\"/images/icon-192.png\",\"badge\":\"/images/badge-72.png\"}",
				esc(title), esc(body), esc(url));
	}

	// ── Build VAPID JWT ───────────────────────────────────────────
	private String buildVapidJwt(String endpoint) throws Exception {
		URI uri = URI.create(endpoint);
		String audience = uri.getScheme() + "://" + uri.getHost();
		long exp = System.currentTimeMillis() / 1000 + 43200; // 12 hours
		String subject = resolveSubject();

		String header = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
		String claims = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(String.format("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}", audience, exp, subject)
						.getBytes(StandardCharsets.UTF_8));

		String signingInput = header + "." + claims;
		byte[] sig = signES256(signingInput.getBytes(StandardCharsets.UTF_8));
		return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
	}

	// ── ES256 signing ─────────────────────────────────────────────
	private byte[] signES256(byte[] data) throws Exception {
		byte[] keyBytes = Base64.getUrlDecoder().decode(resolvePrivateKey());
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("EC");
		PrivateKey pk = kf.generatePrivate(spec);
		Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
		sig.initSign(pk);
		sig.update(data);
		return sig.sign();
	}

	// ── Encrypt payload (AESGCM per RFC 8291) ────────────────────
	private byte[] encryptPayload(String payload, String p256dh, String authKey) throws Exception {
		byte[] salt = new byte[16];
		new SecureRandom().nextBytes(salt);
		byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

		// Recipient public key
		byte[] recipientPub = Base64.getUrlDecoder().decode(p256dh);
		byte[] authBytes = Base64.getUrlDecoder().decode(authKey);

		// Generate ephemeral key pair
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair ephemeral = kpg.generateKeyPair();
		byte[] ephemeralPub = ((java.security.interfaces.ECPublicKey) ephemeral.getPublic()).getEncoded();

		// ECDH shared secret
		KeyFactory kf = KeyFactory.getInstance("EC");
		ECPublicKeySpec pubSpec = new ECPublicKeySpec(
				((java.security.interfaces.ECPublicKey) kf.generatePublic(new X509EncodedKeySpec(recipientPub))).getW(),
				((java.security.interfaces.ECPublicKey) kf.generatePublic(new X509EncodedKeySpec(recipientPub)))
						.getParams());
		PublicKey recipientKey = kf.generatePublic(pubSpec);
		KeyAgreement ka = KeyAgreement.getInstance("ECDH");
		ka.init(ephemeral.getPrivate());
		ka.doPhase(recipientKey, true);
		byte[] sharedSecret = ka.generateSecret();

		// HKDF — derive keys
		byte[] prk = hkdf(authBytes, sharedSecret, "Content-Encoding: auth\0".getBytes(), 32);
		byte[] context = buildContext(recipientPub, ephemeralPub);
		byte[] cek = hkdf(salt, prk, buildInfo("aesgcm", context), 16);
		byte[] nonce = hkdf(salt, prk, buildInfo("nonce", context), 12);

		// AES-GCM encrypt
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		// Pad payload (2 bytes pad length + padding + payload)
		byte[] padded = new byte[2 + payloadBytes.length];
		System.arraycopy(payloadBytes, 0, padded, 2, payloadBytes.length);
		byte[] encrypted = cipher.doFinal(padded);

		// Build output: salt(16) + record_size(4) + key_id_len(1) + ephemeral_pub +
		// encrypted
		byte[] output = new byte[16 + 4 + 1 + ephemeralPub.length + encrypted.length];
		System.arraycopy(salt, 0, output, 0, 16);
		output[16] = 0;
		output[17] = 0;
		output[18] = 16;
		output[19] = 0; // record size = 4096
		output[20] = (byte) ephemeralPub.length;
		System.arraycopy(ephemeralPub, 0, output, 21, ephemeralPub.length);
		System.arraycopy(encrypted, 0, output, 21 + ephemeralPub.length, encrypted.length);
		return output;
	}

	private byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int len) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(salt, "HmacSHA256"));
		byte[] prk = mac.doFinal(ikm);
		mac.init(new SecretKeySpec(prk, "HmacSHA256"));
		mac.update(info);
		mac.update((byte) 1);
		byte[] okm = mac.doFinal();
		return Arrays.copyOf(okm, len);
	}

	private byte[] buildContext(byte[] recipientPub, byte[] senderPub) {
		// P-256 ECDH context per RFC 8291
		// "P-256\0" + len(recipientPub) + recipientPub + len(senderPub) + senderPub
		byte[] label = "P-256\0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] ctx = new byte[label.length + 2 + recipientPub.length + 2 + senderPub.length];
		int pos = 0;
		System.arraycopy(label, 0, ctx, pos, label.length);
		pos += label.length;
		ctx[pos++] = (byte) (recipientPub.length >> 8);
		ctx[pos++] = (byte) (recipientPub.length);
		System.arraycopy(recipientPub, 0, ctx, pos, recipientPub.length);
		pos += recipientPub.length;
		ctx[pos++] = (byte) (senderPub.length >> 8);
		ctx[pos++] = (byte) (senderPub.length);
		System.arraycopy(senderPub, 0, ctx, pos, senderPub.length);
		return ctx;
	}

	private byte[] buildInfo(String type, byte[] context) {
		byte[] typeBytes = ("Content-Encoding: " + type + "\0P-256\0").getBytes(StandardCharsets.UTF_8);
		byte[] info = new byte[typeBytes.length + context.length];
		System.arraycopy(typeBytes, 0, info, 0, typeBytes.length);
		System.arraycopy(context, 0, info, typeBytes.length, context.length);
		return info;
	}

	private String esc(String s) {
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String resolvePublicKey() {
		try {
			String runtime = redisTemplate.opsForValue().get(VAPID_PUBLIC_KEY);
			if (runtime != null && !runtime.isBlank()) {
				return runtime.trim();
			}
		} catch (Exception ignored) {
		}
		return vapidPublicKey == null ? "" : vapidPublicKey.trim();
	}

	private String resolvePrivateKey() {
		try {
			String runtime = redisTemplate.opsForValue().get(VAPID_PRIVATE_KEY);
			if (runtime != null && !runtime.isBlank()) {
				return runtime.trim();
			}
		} catch (Exception ignored) {
		}
		return vapidPrivateKey == null ? "" : vapidPrivateKey.trim();
	}

	private String resolveSubject() {
		try {
			String runtime = redisTemplate.opsForValue().get(VAPID_SUBJECT_KEY);
			if (runtime != null && !runtime.isBlank()) {
				return runtime.trim();
			}
		} catch (Exception ignored) {
		}
		return vapidSubject == null || vapidSubject.isBlank() ? "mailto:admin@aifeed.com" : vapidSubject.trim();
	}
}
