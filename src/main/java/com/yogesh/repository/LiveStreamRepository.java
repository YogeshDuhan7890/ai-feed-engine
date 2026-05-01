package com.yogesh.repository;

import com.yogesh.model.LiveStream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

	Optional<LiveStream> findByHostIdAndStatus(Long hostId, String status);

	Optional<LiveStream> findByStreamKey(String streamKey);

	List<LiveStream> findByStatusOrderByViewerCountDesc(String status);

	@Modifying
	@Transactional
	@Query("UPDATE LiveStream s SET s.viewerCount = s.viewerCount + 1 WHERE s.streamKey = :key")
	void incrementViewerCount(@Param("key") String streamKey);

	@Modifying
	@Transactional
	@Query("UPDATE LiveStream s SET s.viewerCount = GREATEST(s.viewerCount - 1, 0) WHERE s.streamKey = :key")
	void decrementViewerCount(@Param("key") String streamKey);
}