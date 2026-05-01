package com.yogesh.repository;

import com.yogesh.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	// Top-level comments only (parentId IS NULL)
	List<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtDesc(Long postId);

	// Replies for a comment
	List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

	// All comments for a post (legacy)
	List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);

	long countByPostId(Long postId);

	@Transactional
	@Modifying
	@Query("UPDATE Comment c SET c.isDeleted = true WHERE c.postId = :postId")
	void deleteByPostId(@Param("postId") Long postId);

	@Transactional
	@Modifying
	@Query("UPDATE Comment c SET c.isDeleted = true WHERE c.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	// Increment like count
	@Modifying
	@Transactional
	@Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id AND (c.isDeleted = false OR c.isDeleted IS NULL)")
	void incrementLike(@Param("id") Long id);

	@Modifying
	@Transactional
	@Query("UPDATE Comment c SET c.likeCount = GREATEST(c.likeCount - 1, 0) WHERE c.id = :id AND (c.isDeleted = false OR c.isDeleted IS NULL)")
	void decrementLike(@Param("id") Long id);
}
