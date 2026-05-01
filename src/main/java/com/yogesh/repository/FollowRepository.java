package com.yogesh.repository;

import com.yogesh.model.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

	@Query("""
			SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
			FROM Follow f
			WHERE f.followerId = :followerId
			  AND f.followingId = :followingId
			  AND f.status = 'ACCEPTED'
			""")
	boolean existsByFollowerIdAndFollowingId(@Param("followerId") Long followerId,
			@Param("followingId") Long followingId);

	@Query("""
			SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
			FROM Follow f
			WHERE f.followerId = :followerId
			  AND f.followingId = :followingId
			""")
	boolean existsAnyByFollowerIdAndFollowingId(@Param("followerId") Long followerId,
			@Param("followingId") Long followingId);

	@Query("""
			SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
			FROM Follow f
			WHERE f.followerId = :followerId
			  AND f.followingId = :followingId
			  AND f.status = 'PENDING'
			""")
	boolean existsPendingByFollowerIdAndFollowingId(@Param("followerId") Long followerId,
			@Param("followingId") Long followingId);

	Optional<Follow> findFirstByFollowerIdAndFollowingId(Long followerId, Long followingId);

	Optional<Follow> findFirstByFollowerIdAndFollowingIdAndStatus(Long followerId, Long followingId, String status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Follow f
			SET f.status = 'ACCEPTED'
			WHERE f.followerId = :followerId
			  AND f.followingId = :followingId
			  AND f.status = 'PENDING'
			""")
	int acceptPendingFollow(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			DELETE FROM Follow f
			WHERE f.followerId = :followerId
			  AND f.followingId = :followingId
			  AND f.status = :status
			""")
	int deleteByFollowerIdAndFollowingIdAndStatus(@Param("followerId") Long followerId,
			@Param("followingId") Long followingId,
			@Param("status") String status);

	List<Follow> findByFollowerIdOrFollowingId(Long followerId, Long followingId);

	void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

	void deleteByFollowerIdOrFollowingId(Long followerId, Long followingId);

	@Query("SELECT COUNT(f) FROM Follow f WHERE f.followingId = :followingId AND f.status = 'ACCEPTED'")
	long countByFollowingId(@Param("followingId") Long followingId);

	@Query("SELECT COUNT(f) FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACCEPTED'")
	long countByFollowerId(@Param("followerId") Long followerId);

	@Query("SELECT COUNT(f) FROM Follow f WHERE f.followingId = :followingId AND f.status = 'PENDING'")
	long countPendingByFollowingId(@Param("followingId") Long followingId);

	@Query("SELECT f FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACCEPTED'")
	List<Follow> findByFollowerId(@Param("followerId") Long followerId);

	@Query("SELECT f FROM Follow f WHERE f.followingId = :followingId AND f.status = 'ACCEPTED'")
	List<Follow> findByFollowingId(@Param("followingId") Long followingId);

	@Query("""
			SELECT f FROM Follow f
			WHERE f.followingId = :followingId
			  AND f.status = 'PENDING'
			ORDER BY f.createdAt DESC
			""")
	List<Follow> findPendingByFollowingId(@Param("followingId") Long followingId);

	// ── OPTIMIZED: Batch follower IDs — FanoutService N+1 fix ──────────
	// Pehle: followRepository.findByFollowingId() → List<Follow> → stream map
	// Ab: seedha Set<Long> ek hi query mein
	@Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId AND f.status = 'ACCEPTED'")
	Set<Long> findFollowerIdsByFollowingId(@Param("userId") Long userId);

	// Batch: multiple users ke following IDs ek saath
	@Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId AND f.status = 'ACCEPTED'")
	Set<Long> findFollowingIdsByFollowerId(@Param("userId") Long userId);

	// Check if current user follows any of given userIds (batch)
	@Query("""
			SELECT f.followingId FROM Follow f
			WHERE f.followerId = :userId
			  AND f.followingId IN :userIds
			  AND f.status = 'ACCEPTED'
			""")
	Set<Long> findFollowingIdsInList(@Param("userId") Long userId, @Param("userIds") List<Long> userIds);

	// Follower growth by day (analytics)
	@Query("""
			SELECT CAST(f.createdAt AS date), COUNT(f)
			FROM Follow f
			WHERE f.followingId = :userId AND f.createdAt >= :since AND f.status = 'ACCEPTED'
			GROUP BY CAST(f.createdAt AS date)
			ORDER BY CAST(f.createdAt AS date)
			""")
	List<Object[]> getFollowerGrowthByDay(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);

	// Follower + following counts ek saath (analytics ke liye)
	@Query("SELECT COUNT(f) FROM Follow f WHERE f.followerId = :userId AND f.status = 'ACCEPTED'")
	long countFollowing(@Param("userId") Long userId);

	@Query("SELECT COUNT(f) FROM Follow f WHERE f.followingId = :userId AND f.status = 'ACCEPTED'")
	long countFollowers(@Param("userId") Long userId);
}
