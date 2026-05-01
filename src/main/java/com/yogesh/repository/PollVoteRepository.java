package com.yogesh.repository;

import com.yogesh.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    Optional<PollVote> findByPollIdAndUserId(Long pollId, Long userId);

    @Query("SELECT v.optionId, COUNT(v) FROM PollVote v WHERE v.pollId = :pollId GROUP BY v.optionId")
    List<Object[]> countByOption(@Param("pollId") Long pollId);
}

