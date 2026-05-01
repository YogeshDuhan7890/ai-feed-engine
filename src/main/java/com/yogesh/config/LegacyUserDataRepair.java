package com.yogesh.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyUserDataRepair implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int updated = jdbcTemplate.update("update users set private_account = false where private_account is null");
			if (updated > 0) {
				log.info("Legacy user repair applied: {} rows updated for private_account", updated);
			}
			backfillIsDeleted("users");
			backfillIsDeleted("posts");
			backfillIsDeleted("comments");
			backfillIsDeleted("stories");
		} catch (Exception e) {
			log.warn("Legacy user repair skipped: {}", e.getMessage());
		}
	}

	private void backfillIsDeleted(String tableName) {
		try {
			int updated = jdbcTemplate.update("update " + tableName + " set is_deleted = false where is_deleted is null");
			if (updated > 0) {
				log.info("Legacy data repair applied: {} rows updated for {}.is_deleted", updated, tableName);
			}
		} catch (Exception e) {
			log.warn("Legacy is_deleted repair skipped for {}: {}", tableName, e.getMessage());
		}
	}
}
