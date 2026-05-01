package com.yogesh.repository;

import com.yogesh.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.Set;
import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

	boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

	void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

	void deleteByBlockerIdOrBlockedId(Long blockerId, Long blockedId);

	// Block list — newest first
	List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

	// Jinhe maine bloc
	@org.springframework.data.jpa.repository.Query("SELECT b.blockedId FROM Block b WHERE b.blockerId = :userId "
			+ "UNION SELECT b.blockerId FROM Block b WHERE b.blockedId = :userId")
	Set<Long> findBlockedAndBlockerIds(@Param("userId") Long userId);
}
