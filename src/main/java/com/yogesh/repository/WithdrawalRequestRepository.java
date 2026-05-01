package com.yogesh.repository;

import com.yogesh.model.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
	List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

	List<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(String status);

	boolean existsByUserIdAndStatus(Long userId, String status);
}