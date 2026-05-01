package com.yogesh.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.yogesh.model.Engagement;

public interface EngagementRepository extends JpaRepository<Engagement, Long> {

	/**
	 * ✅ FIX: FUNCTION('DATE', e.createdAt) → CAST(e.createdAt AS date)
	 *
	 * Problem: FUNCTION('DATE', ...) MySQL-specific syntax hai. PostgreSQL pe "No
	 * dialect mapping for JDBC type" ya "function DATE unknown" throw hota tha →
	 * Analytics page pe 500 Internal Server Error.
	 *
	 * Solution: JPQL standard CAST(...AS date) use karo jo PostgreSQL pe properly
	 * translate hota hai DATE() function ki tarah.
	 */
	@Query("""
			SELECT CAST(e.createdAt AS date), COUNT(e)
			FROM Engagement e
			WHERE e.createdAt >= :startDate
			GROUP BY CAST(e.createdAt AS date)
			ORDER BY CAST(e.createdAt AS date)
			""")
	List<Object[]> getLast7DaysEngagementStats(@Param("startDate") LocalDateTime startDate);

	@Query("SELECT COUNT(e) FROM Engagement e WHERE e.postId = :postId AND e.type = 'LIKE'")
	long countLikes(@Param("postId") Long postId);

	@Query("SELECT COUNT(e) FROM Engagement e WHERE e.postId = :postId AND e.type = 'COMMENT'")
	long countComments(@Param("postId") Long postId);

	@Query("SELECT COUNT(e) FROM Engagement e WHERE e.postId = :postId AND e.type = 'SHARE'")
	long countShares(@Param("postId") Long postId);

	/**
	 * ✅ FIX: "COUNT(e) > 0" JPQL mein valid boolean expression nahi hai.
	 *
	 * Problem: Hibernate "COUNT(e) > 0" ko boolean return nahi karta reliably —
	 * PostgreSQL pe ClassCastException ya query parse error → /api/post/likes 500.
	 *
	 * Solution: CASE WHEN ... THEN true ELSE false END — proper JPQL boolean.
	 */
	@Query("""
			SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
			FROM Engagement e
			WHERE e.postId = :postId AND e.userId = :userId AND e.type = 'LIKE'
			""")
	boolean existsLikeByUserAndPost(@Param("userId") Long userId, @Param("postId") Long postId);

	@Modifying
	@Transactional
	@Query("DELETE FROM Engagement e WHERE e.userId = :userId AND e.postId = :postId AND e.type = 'LIKE'")
	void deleteLike(@Param("userId") Long userId, @Param("postId") Long postId);

	/**
	 * ✅ FIX: Batch like counts — PostController mein (Long) r[1] cast safe karo.
	 *
	 * COUNT() result Hibernate Long ya BigInteger return kar sakta hai depending on
	 * dialect. JPQL SELECT e.postId, COUNT(e) reliably Object[] return karta hai
	 * jisme r[0]=Long (postId), r[1]=Long (count). PostController mein ((Number)
	 * r[1]).longValue() use karo cast safety ke liye.
	 */
	@Query("""
			SELECT e.postId, COUNT(e)
			FROM Engagement e
			WHERE e.postId IN :postIds AND e.type = 'LIKE'
			GROUP BY e.postId
			""")
	List<Object[]> countLikesByPostIds(@Param("postIds") List<Long> postIds);

	/**
	 * ✅ NEW: Total unread count ek query mein — getTotalUnread() N+1 fix.
	 * DirectMessageService.getTotalUnread() pehle per-partner loop karta tha.
	 */
	@Query("""
			SELECT COUNT(e)
			FROM Engagement e
			WHERE e.postId IN :postIds
			""")
	long countAllByPostIds(@Param("postIds") List<Long> postIds);

	// NEW: Daily engagement stats (generalized — admin heatmap ke liye)
	@Query("""
			SELECT CAST(e.createdAt AS date), COUNT(e)
			FROM Engagement e
			WHERE e.createdAt >= :startDate
			GROUP BY CAST(e.createdAt AS date)
			ORDER BY CAST(e.createdAt AS date)
			""")
	List<Object[]> getDailyEngagementStats(@Param("startDate") LocalDateTime startDate);

	// NEW: Hourly distribution (heatmap ke liye)
	@Query("""
			SELECT HOUR(e.createdAt), COUNT(e)
			FROM Engagement e
			GROUP BY HOUR(e.createdAt)
			ORDER BY HOUR(e.createdAt)
			""")
	List<Object[]> getHourlyDistribution();

	// NEW: Top engaged posts
	@Query("""
			SELECT e.postId, COUNT(e)
			FROM Engagement e
			WHERE e.createdAt >= :since
			GROUP BY e.postId
			ORDER BY COUNT(e) DESC
			""")
	List<Object[]> getTopEngagedPosts(@Param("since") LocalDateTime since, Pageable pageable);

	// NEW: User engagement stats
	@Query("SELECT COUNT(e) FROM Engagement e WHERE e.userId = :userId AND e.type = :type")
	long countByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

	// NEW: Recent engagement for mobile feed (batch)
	@Query("""
			SELECT e.postId, COUNT(CASE WHEN e.type = 'LIKE' THEN 1 END),
			       COUNT(CASE WHEN e.type = 'COMMENT' THEN 1 END)
			FROM Engagement e
			WHERE e.postId IN :postIds
			GROUP BY e.postId
			""")
	List<Object[]> getEngagementSummary(@Param("postIds") List<Long> postIds);

	// Admin analytics: engagement by type in date range
	@Query("""
			SELECT e.type, COUNT(e)
			FROM Engagement e
			WHERE e.createdAt >= :startDate
			GROUP BY e.type
			ORDER BY COUNT(e) DESC
			""")
	List<Object[]> countByTypeInRange(@Param("startDate") LocalDateTime startDate);

	// Admin: all time by type
	@Query("SELECT e.type, COUNT(e) FROM Engagement e GROUP BY e.type ORDER BY COUNT(e) DESC")
	List<Object[]> countByType();

	// Admin: top posts by engagement
	@Query("""
			SELECT e.postId, COUNT(e)
			FROM Engagement e
			GROUP BY e.postId
			ORDER BY COUNT(e) DESC
			""")
	List<Object[]> getTopPostsByEngagement(Pageable pageable);

	// Admin: avg watch time per day
	@Query("""
			SELECT CAST(e.createdAt AS date), AVG(e.watchTime)
			FROM Engagement e
			WHERE e.createdAt >= :startDate AND e.watchTime IS NOT NULL
			GROUP BY CAST(e.createdAt AS date)
			ORDER BY CAST(e.createdAt AS date)
			""")
	List<Object[]> getAvgWatchTimePerDay(@Param("startDate") LocalDateTime startDate);
}