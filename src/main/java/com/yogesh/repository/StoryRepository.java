// ===== StoryRepository.java =====
package com.yogesh.repository;

import com.yogesh.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

	// Active (non-expired) stories of a user
	List<Story> findByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime now);

	// Active stories of multiple users (for feed ring)
	@Query("SELECT s FROM Story s WHERE s.userId IN :userIds AND s.expiresAt > :now AND (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.createdAt DESC")
	List<Story> findActiveStoriesByUserIds(@Param("userIds") List<Long> userIds, @Param("now") LocalDateTime now);

	@Query("SELECT s FROM Story s WHERE s.expiresAt > :now AND (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.createdAt DESC")
	List<Story> findActiveStories(@Param("now") LocalDateTime now);

	// Cleanup expired stories (scheduled job ke liye)
	@Modifying
	@Query("UPDATE Story s SET s.isDeleted = true WHERE s.expiresAt <= :now")
	void deleteExpiredStories(@Param("now") LocalDateTime now);

	boolean existsByUserIdAndExpiresAtAfter(Long userId, LocalDateTime now);

	@Modifying
	@Query("UPDATE Story s SET s.isDeleted = true WHERE s.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
