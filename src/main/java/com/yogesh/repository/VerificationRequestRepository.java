package com.yogesh.repository;

import com.yogesh.model.VerificationRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    Optional<VerificationRequest> findByUserId(Long userId);

    @Query("SELECT r FROM VerificationRequest r WHERE r.status = :status ORDER BY r.createdAt DESC")
    List<VerificationRequest> findByStatus(@Param("status") String status, Pageable pageable);
}

