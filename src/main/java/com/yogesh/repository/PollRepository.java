package com.yogesh.repository;

import com.yogesh.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    Optional<Poll> findByPostId(Long postId);
    List<Poll> findByPostIdIn(List<Long> postIds);
}

