package com.yogesh.repository;

import com.yogesh.model.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPollIdOrderByIdAsc(Long pollId);
}

