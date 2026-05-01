package com.yogesh.repository;

import com.yogesh.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

	// ── Basic queries ──────────────────────────────────────────────
	@Query("SELECT p.createdAt FROM Post p WHERE p.id = :postId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	LocalDateTime getCreatedTime(@Param("postId") Long postId);

	List<Post> findByUserId(Long userId);

	long countByUserId(Long userId);

	@Query("""
			SELECT COUNT(p) FROM Post p
			WHERE p.userId = :userId
			  AND (p.isDeleted = false OR p.isDeleted IS NULL)
			  AND p.videoUrl IS NOT NULL
			  AND (p.scheduledAt IS NULL OR p.scheduledAt <= :now)
			  AND (p.status IS NULL OR UPPER(p.status) <> 'SCHEDULED' OR p.scheduledAt <= :now)
			""")
	long countVisibleByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

	long countByUserIdAndIdIn(Long userId, Collection<Long> ids);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE Post p SET p.isDeleted = true WHERE p.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	// ── Feed queries — paginated (no more top200!) ─────────────────
	// Pehle: findTop200ByOrderByCreatedAtDesc() — 200 rows load always
	// Ab: paginated — sirf zaroori rows
	Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

	// First page fast (index scan)
	@Query("SELECT p FROM Post p WHERE (p.isDeleted = false OR p.isDeleted IS NULL) AND p.videoUrl IS NOT NULL ORDER BY p.createdAt DESC")
	List<Post> findLatestVideos(Pageable pageable);

	// ── Search — optimized LIKE with early exit ────────────────────
	// Pehle: LOWER(CONCAT('%',:query,'%')) — full table scan
	// Ab: limit results + both fields indexed
	@Query("""
			SELECT p FROM Post p WHERE
			(LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(p.tags) LIKE LOWER(CONCAT('%', :query, '%')))
			AND (p.isDeleted = false OR p.isDeleted IS NULL)
			AND p.videoUrl IS NOT NULL
			ORDER BY p.createdAt DESC
			""")
	List<Post> searchByContentOrTags(@Param("query") String query, Pageable pageable);

	// Legacy (backward compat)
	@Query("""
			SELECT p FROM Post p WHERE
			(p.isDeleted = false OR p.isDeleted IS NULL)
			AND (LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(p.tags) LIKE LOWER(CONCAT('%', :query, '%')))
			""")
	List<Post> searchByContentOrTags(@Param("query") String query);

	// ── User videos paginated ─────────────────────────────────────
	Page<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	// ── Pinning (profile) ─────────────────────────────────────────
	List<Post> findByUserIdOrderByIsPinnedDescCreatedAtDesc(Long userId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE Post p SET p.isPinned = false WHERE p.userId = :userId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	void clearPinnedByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE Post p SET p.isPinned = true WHERE p.id = :postId AND p.userId = :userId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	int pinPost(@org.springframework.data.repository.query.Param("postId") Long postId,
			@org.springframework.data.repository.query.Param("userId") Long userId);

	@Query("SELECT p FROM Post p WHERE p.userId = :userId AND (p.isDeleted = false OR p.isDeleted IS NULL) AND p.videoUrl IS NOT NULL ORDER BY p.createdAt DESC")
	List<Post> findVideosByUserId(@Param("userId") Long userId);

	// ── Cursor-based pagination (infinite scroll) ─────────────────
	// Better than OFFSET for large datasets
	@Query("SELECT p FROM Post p WHERE p.id < :cursorId AND (p.isDeleted = false OR p.isDeleted IS NULL) AND p.videoUrl IS NOT NULL ORDER BY p.id DESC")
	List<Post> findBeforeCursor(@Param("cursorId") Long cursorId, Pageable pageable);

	// Following feed — posts from specific users
	@Query("SELECT p FROM Post p WHERE p.userId IN :userIds AND (p.isDeleted = false OR p.isDeleted IS NULL) AND p.videoUrl IS NOT NULL ORDER BY p.createdAt DESC")
	List<Post> findByUserIdIn(@Param("userIds") List<Long> userIds, Pageable pageable);

	// Following feed with cursor
	@Query("SELECT p FROM Post p WHERE p.userId IN :userIds AND p.id < :cursorId AND (p.isDeleted = false OR p.isDeleted IS NULL) AND p.videoUrl IS NOT NULL ORDER BY p.id DESC")
	List<Post> findByUserIdInBeforeCursor(@Param("userIds") List<Long> userIds, @Param("cursorId") Long cursorId,
			Pageable pageable);

	// ── Scheduling ───────────────────────────────────────────────
	List<Post> findTop100ByScheduledAtLessThanEqualAndIsDeletedFalse(LocalDateTime scheduledAt);

	// ── Batch fetch by IDs (feed from Redis keys) ─────────────────
	// Pehle: Redis se IDs lo → N queries → N+1 problem
	// Ab: ek hi IN query
	@Query("SELECT p FROM Post p WHERE p.id IN :ids AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	List<Post> findAllByIds(@Param("ids") List<Long> ids);

	// ── Analytics ────────────────────────────────────────────────
	@Query("SELECT COUNT(p) FROM Post p WHERE p.createdAt >= :since AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	long countSince(@Param("since") LocalDateTime since);

	@Query("SELECT p FROM Post p WHERE p.createdAt >= :since AND (p.isDeleted = false OR p.isDeleted IS NULL) ORDER BY p.id DESC")
	List<Post> findRecentSince(@Param("since") LocalDateTime since);

	// ── Top posts by user (profile page) ─────────────────────────
	@org.springframework.data.jpa.repository.Modifying
	@Query("UPDATE Post p SET p.likeCount = COALESCE(p.likeCount, 0) + 1 WHERE p.id = :postId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	int incrementLikeCount(@Param("postId") Long postId);

	@org.springframework.data.jpa.repository.Modifying
	@Query("UPDATE Post p SET p.likeCount = CASE WHEN COALESCE(p.likeCount, 0) > 0 THEN COALESCE(p.likeCount, 0) - 1 ELSE 0 END WHERE p.id = :postId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	int decrementLikeCount(@Param("postId") Long postId);

	@org.springframework.data.jpa.repository.Modifying
	@Query("UPDATE Post p SET p.viewCount = COALESCE(p.viewCount, 0) + 1 WHERE p.id = :postId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	int incrementViewCount(@Param("postId") Long postId);

	@Query("SELECT p.id, COALESCE(p.likeCount, 0), COALESCE(p.viewCount, 0) FROM Post p WHERE p.id IN :postIds AND (p.isDeleted = false OR p.isDeleted IS NULL)")
	List<Object[]> findCountersByPostIds(@Param("postIds") List<Long> postIds);

	List<Post> findTop200ByOrderByCreatedAtDesc(); // legacy support
}
