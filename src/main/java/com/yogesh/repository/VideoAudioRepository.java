package com.yogesh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yogesh.model.VideoAudioTrack;

public interface VideoAudioRepository extends JpaRepository<VideoAudioTrack, Long> {

	List<VideoAudioTrack> findByPostId(Long postId);

}