package com.yogesh.scheduler;

import com.yogesh.model.Post;
import com.yogesh.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPostScheduler {

	private final PostRepository postRepository;

	@Scheduled(fixedRate = 60_000)
	@Transactional
	public void publishDueScheduledPosts() {
		LocalDateTime now = LocalDateTime.now();
		try {
			List<Post> due = postRepository
					.findTop100ByScheduledAtLessThanEqualAndIsDeletedFalse(now);
			if (due == null || due.isEmpty()) {
				return;
			}

			for (Post p : due) {
				if (p.getScheduledAt() == null) {
					continue;
				}
				String st = p.getStatus();
				// Only transition SCHEDULED/unknown → PUBLISHED
				if (st == null || st.isBlank() || "SCHEDULED".equalsIgnoreCase(st)) {
					// Make the post eligible in feed immediately and align ordering with due time.
					p.setStatus("PUBLISHED");
					p.setCreatedAt(p.getScheduledAt());
					postRepository.save(p);
				}
			}

			log.info("Published scheduled posts: count={}", due.size());
		} catch (Exception e) {
			log.warn("Scheduled publish failed: {}", e.getMessage());
		}
	}
}

