package com.yogesh.repository;

import com.yogesh.model.DirectMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

	// ── Full conversation ──────────────────────────────────────────────
	@Query("""
			SELECT m FROM DirectMessage m
			WHERE (m.senderId = :userA AND m.receiverId = :userB)
			   OR (m.senderId = :userB AND m.receiverId = :userA)
			ORDER BY m.createdAt ASC
			""")
	List<DirectMessage> findConversation(@Param("userA") Long userA, @Param("userB") Long userB);

	// Paginated conversation (for large chats)
	@Query("""
			SELECT m FROM DirectMessage m
			WHERE (m.senderId = :userA AND m.receiverId = :userB)
			   OR (m.senderId = :userB AND m.receiverId = :userA)
			ORDER BY m.createdAt DESC
			""")
	List<DirectMessage> findConversationPaged(@Param("userA") Long userA, @Param("userB") Long userB,
			Pageable pageable);

	// ── Inbox partners ─────────────────────────────────────────────────
	@Query("""
			SELECT DISTINCT CASE
			    WHEN m.senderId = :userId THEN m.receiverId
			    ELSE m.senderId
			END
			FROM DirectMessage m
			WHERE m.senderId = :userId OR m.receiverId = :userId
			""")
	List<Long> findConversationPartnerIds(@Param("userId") Long userId);

	// ── FIX: Latest message per partner — ONE query instead of N ──────
	// Pehle: loop mein findLatestMessage() — N+1
	// Ab: ek query mein sab latest messages
	@Query("""
			SELECT m FROM DirectMessage m
			WHERE m.id IN (
			    SELECT MAX(m2.id)
			    FROM DirectMessage m2
			    WHERE (m2.senderId = :userId OR m2.receiverId = :userId)
			    GROUP BY CASE
			        WHEN m2.senderId = :userId THEN m2.receiverId
			        ELSE m2.senderId
			    END
			)
			ORDER BY m.createdAt DESC
			""")
	List<DirectMessage> findLatestMessagePerPartner(@Param("userId") Long userId);

	// Legacy — single conversation latest (keep for compat)
	@Query("""
			SELECT m FROM DirectMessage m
			WHERE (m.senderId = :userA AND m.receiverId = :userB)
			   OR (m.senderId = :userB AND m.receiverId = :userA)
			ORDER BY m.createdAt DESC
			""")
	List<DirectMessage> findLatestMessage(@Param("userA") Long userA, @Param("userB") Long userB);

	// ── Mark read ──────────────────────────────────────────────────────
	@Modifying
	@Transactional
	@Query("""
			UPDATE DirectMessage m
			SET m.read = true
			WHERE m.senderId = :senderId
			  AND m.receiverId = :receiverId
			  AND m.read = false
			""")
	void markConversationRead(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);

	// ── Unread counts ──────────────────────────────────────────────────
	@Query("""
			SELECT COUNT(m) FROM DirectMessage m
			WHERE m.receiverId = :userId AND m.read = false
			""")
	long countTotalUnreadForUser(@Param("userId") Long userId);

	long countBySenderIdAndReceiverIdAndReadFalse(Long senderId, Long receiverId);

	// ── Delete conversation ────────────────────────────────────────────
	@Modifying
	@Transactional
	@Query("""
			DELETE FROM DirectMessage m
			WHERE (m.senderId = :userA AND m.receiverId = :userB)
			   OR (m.senderId = :userB AND m.receiverId = :userA)
			""")
	void deleteConversation(@Param("userA") Long userA, @Param("userB") Long userB);

	@Modifying
	@Transactional
	@Query("""
			DELETE FROM DirectMessage m
			WHERE m.senderId = :userId OR m.receiverId = :userId
			""")
	void deleteByUserId(@Param("userId") Long userId);
}
