package com.yogesh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.yogesh.util.FileStorageUtil;

import jakarta.annotation.PostConstruct;

/*
 * BUG FIX: @EnableMethodSecurity REMOVE kiya yahan se
 * Yeh SecurityConfig.java mein pehle se hai — duplicate annotation
 * cause karta hai: "A duplicate @EnableMethodSecurity annotation was detected"
 * warning in Spring 6.x, aur kuch cases mein bean conflicts.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
public class AiFeedEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiFeedEngineApplication.class, args);
	}

	/**
	 * Create required folders on startup
	 */
	@PostConstruct
	public void init() {
		FileStorageUtil.createDirectories();
		System.out.println("✅ Upload folders initialized");
	}

}

