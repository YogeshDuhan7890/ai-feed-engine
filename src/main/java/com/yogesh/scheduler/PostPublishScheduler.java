package com.yogesh.scheduler;

import com.yogesh.model.Post;
import com.yogesh.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled publisher for posts with a future {@code scheduledAt} time.
 *
 * Upload ke time agar user future datetime set karta hai toh post
 * {@code status = "SCHEDULED"} ke saath save hoti hai.
 * Ye scheduler due posts ko periodically {@code PUBLISHED} me flip karta hai.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostPublishScheduler {

    private final PostRepository postRepository;

    /**
     * Har 30 second me check karo ki kaun si posts due ho chuki hain.
     *
     * Strategy:
     * - Top 100 posts jinka scheduledAt <= now hai load karo
     * - Sirf unhe publish karo jinki status "SCHEDULED" hai
     * - Status "PUBLISHED" set karo, aur createdAt ko change nahi karte
     */
    @Scheduled(fixedDelay = 30_000)
    public void publishDuePosts() {
        LocalDateTime now = LocalDateTime.now();
        try {
            List<Post> due = postRepository.findTop100ByScheduledAtLessThanEqualAndIsDeletedFalse(now);
            if (due == null || due.isEmpty()) {
                return;
            }

            int updated = 0;
            List<Post> changed = new ArrayList<>();
            for (Post post : due) {
                if (post == null) {
                    continue;
                }
                String status = post.getStatus();
                if (status != null && !"SCHEDULED".equalsIgnoreCase(status)) {
                    continue;
                }

                // Flip to PUBLISHED; scheduledAt ko as-is rehne do (audit ke liye useful hai)
                post.setStatus("PUBLISHED");
                changed.add(post);
                updated++;
            }

            if (updated > 0) {
                postRepository.saveAll(changed);
                log.info("PostPublishScheduler: {} scheduled posts published (<= {}).", updated, now);
            }
        } catch (Exception e) {
            log.warn("PostPublishScheduler failed: {}", e.getMessage());
        }
    }
}

