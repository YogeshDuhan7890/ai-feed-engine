package com.yogesh.repository;

import com.yogesh.model.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

	boolean existsByStoryIdAndViewerId(Long storyId, Long viewerId);

	@Query("SELECT sv.storyId FROM StoryView sv WHERE sv.viewerId = :viewerId")
	Set<Long> findViewedStoryIdsByViewer(@Param("viewerId") Long viewerId);

	List<StoryView> findByStoryId(Long storyId);

	long countByStoryId(Long storyId);

	// N+1 FIX: batch fetch viewed IDs for a viewer
	@Query("SELECT sv.storyId FROM StoryView sv WHERE sv.viewerId = :viewerId AND sv.storyId IN :storyIds")
	Set<Long> findViewedStoryIds(@Param("viewerId") Long viewerId, @Param("storyIds") List<Long> storyIds);
}