package com.yogesh.util;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * VAPID Key Generator
 * 
 * Ek baar run karo: java VapidKeyGenerator
 * 
 * Output mein jo keys aayein unhe application-dev.yml mein daalo: push: vapid:
 * public-key: <PUBLIC_KEY> private-key: <PRIVATE_KEY> subject:
 * mailto:your@email.com
 */
public class VapidKeyGenerator {

	public static void main(String[] args) throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
		keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
		KeyPair keyPair = keyGen.generateKeyPair();

		String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded());
		String privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPrivate().getEncoded());

		System.out.println("=== VAPID Keys Generated ===");
		System.out.println("application-dev.yml mein yeh daalo:\n");
		System.out.println("push:");
		System.out.println("  vapid:");
		System.out.println("    public-key: " + publicKey);
		System.out.println("    private-key: " + privateKey);
		System.out.println("    subject: mailto:your@email.com");
		System.out.println("\npushNotifications.js automatically publicKey fetch kar leta hai /api/push/vapid-key se.");
	}

	/** Programmatically call karo to get keys as Map */
	public static java.util.Map<String, String> generate() throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
		keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
		KeyPair keyPair = keyGen.generateKeyPair();
		return java.util.Map.of("publicKey",
				Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded()), "privateKey",
				Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPrivate().getEncoded()));
	}
}