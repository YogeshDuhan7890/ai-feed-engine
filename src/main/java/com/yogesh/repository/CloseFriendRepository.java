package com.yogesh.repository;

import com.yogesh.model.CloseFriend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CloseFriendRepository extends JpaRepository<CloseFriend, Long> {

    boolean existsByUserIdAndFriendUserId(Long userId, Long friendUserId);

    void deleteByUserIdAndFriendUserId(Long userId, Long friendUserId);

    List<CloseFriend> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT cf.friendUserId FROM CloseFriend cf WHERE cf.userId = :userId")
    Set<Long> findFriendIds(@Param("userId") Long userId);
}

