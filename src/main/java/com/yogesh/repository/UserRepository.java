package com.yogesh.repository;

import com.yogesh.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByEmailIgnoreCase(String email);

	Optional<User> findByUsernameIgnoreCase(String username);

	// Search — paginated + limited
	@Query("""
			SELECT u FROM User u WHERE
			(u.isDeleted = false OR u.isDeleted IS NULL)
			AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
			ORDER BY u.name
			""")
	List<User> searchByNameOrEmail(@Param("query") String query, Pageable pageable);

	// Legacy
	@Query("SELECT u FROM User u WHERE (u.isDeleted = false OR u.isDeleted IS NULL) AND ("
			+ "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
			+ "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
	List<User> searchByNameOrEmail(@Param("query") String query);

	List<User> findByNameContainingIgnoreCase(String name);

	// Batch fetch — N+1 fix
	@Query("SELECT u FROM User u WHERE u.id IN :ids AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	List<User> findAllByIds(@Param("ids") Set<Long> ids);

	// Suggested users
	@Query("""
			SELECT u FROM User u
			WHERE u.id != :userId
			AND (u.isDeleted = false OR u.isDeleted IS NULL)
			AND u.id NOT IN (
			    SELECT f.followingId FROM Follow f WHERE f.followerId = :userId
			)
			ORDER BY u.id DESC
			""")
	List<User> findSuggestedUsers(@Param("userId") Long userId, Pageable pageable);

	boolean existsByEmail(String email);

	@Query("SELECT u.id FROM User u WHERE u.isDeleted = false OR u.isDeleted IS NULL")
	List<Long> findAllIds();

	// Admin stats — efficient count queries (no findAll needed)
	@Query("SELECT COUNT(u) FROM User u WHERE u.enabled = false AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	long countByEnabledFalse();

	@Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	long countByRole(@Param("role") String role);

	// Paginated user list for admin
	@Query("SELECT u FROM User u WHERE u.isDeleted = false OR u.isDeleted IS NULL ORDER BY u.id DESC")
	List<User> findAllPaginated(Pageable pageable);

	boolean existsByUsername(String username);

	@Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false OR u.isDeleted IS NULL")
	long countAll();

	@Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	long countByCreatedAtAfter(@Param("since") LocalDateTime since);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE User u SET u.followers = u.followers + 1 WHERE u.id = :userId AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	int incrementFollowers(@Param("userId") Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE User u SET u.following = u.following + 1 WHERE u.id = :userId AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	int incrementFollowing(@Param("userId") Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE User u SET u.followers = CASE WHEN u.followers > 0 THEN u.followers - 1 ELSE 0 END WHERE u.id = :userId AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	int decrementFollowers(@Param("userId") Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE User u SET u.following = CASE WHEN u.following > 0 THEN u.following - 1 ELSE 0 END WHERE u.id = :userId AND (u.isDeleted = false OR u.isDeleted IS NULL)")
	int decrementFollowing(@Param("userId") Long userId);
}
