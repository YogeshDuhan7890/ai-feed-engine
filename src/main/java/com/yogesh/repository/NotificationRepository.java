package com.yogesh.repository;

import com.yogesh.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	// ── Paginated — instead of loading ALL notifications ──
	List<Notification> findByToUserIdOrderByCreatedAtDesc(Long toUserId, Pageable pageable);

	// Legacy (keep for backward compat)
	List<Notification> findByToUserIdOrderByCreatedAtDesc(Long toUserId);

	// Unread count — uses idx_notifications_unread
	long countByToUserIdAndReadFalse(Long toUserId);

	// Bulk mark all read
	@Modifying
	@Transactional
	@Query("UPDATE Notification n SET n.read = true WHERE n.toUserId = :userId AND n.read = false")
	int markAllReadByUserId(@Param("userId") Long userId);

	// Mark single read
	@Modifying
	@Transactional
	@Query("UPDATE Notification n SET n.read = true WHERE n.id = :id")
	void markReadById(@Param("id") Long id);

	// Auto-cleanup old notifications (30 days se purani)
	@Modifying
	@Transactional
	@Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

	// Duplicate check — same type+actor+post combination
	@Query("""
			SELECT COUNT(n) > 0 FROM Notification n
			WHERE n.toUserId = :toUser
			  AND n.fromUserId = :fromUser
			  AND n.type = :type
			  AND n.postId = :postId
			  AND n.createdAt > :since
			""")
	boolean existsRecentDuplicate(@Param("toUser") Long toUserId, @Param("fromUser") Long fromUserId,
			@Param("type") String type, @Param("postId") Long postId, @Param("since") LocalDateTime since);

	@Transactional
	void deleteByToUserIdOrFromUserId(Long toUserId, Long fromUserId);
}
