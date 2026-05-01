package com.yogesh.repository;

import com.yogesh.model.CreatorWallet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorWalletRepository extends JpaRepository<CreatorWallet, Long> {

	Optional<CreatorWallet> findByUserId(Long userId);

	// Leaderboard — top earners
	List<CreatorWallet> findAllByOrderByTotalEarningsDesc(Pageable pageable);

	// Check if monetization enabled
	boolean existsByUserIdAndMonetizationEnabledTrue(Long userId);
}