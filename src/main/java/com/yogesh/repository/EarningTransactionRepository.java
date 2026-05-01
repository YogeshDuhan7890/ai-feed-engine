// ─── EarningTransactionRepository.java ──────────────────────────────────────
package com.yogesh.repository;

import com.yogesh.model.EarningTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EarningTransactionRepository extends JpaRepository<EarningTransaction, Long> {
	boolean existsByExternalTxnId(String externalTxnId);

	List<EarningTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	List<EarningTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

	@Query("SELECT COALESCE(SUM(e.amount), 0) FROM EarningTransaction e WHERE e.userId = :userId AND e.type != 'WITHDRAWAL'")
	BigDecimal sumEarningsByUserId(Long userId);

	@Query("SELECT COALESCE(SUM(e.amount), 0) FROM EarningTransaction e WHERE e.userId = :userId AND e.type = 'WITHDRAWAL'")
	BigDecimal sumWithdrawalsByUserId(Long userId);

	long countByUserIdAndType(Long userId, String type);

	// Earnings grouped by post
	@Query("SELECT e.postId, SUM(e.amount) FROM EarningTransaction e "
			+ "WHERE e.userId = :userId AND e.postId IS NOT NULL " + "GROUP BY e.postId ORDER BY SUM(e.amount) DESC")
	List<Object[]> sumEarningsByPost(@org.springframework.data.repository.query.Param("userId") Long userId,
			Pageable pageable);

	// Daily earnings for chart
	@Query("SELECT CAST(e.createdAt AS date), SUM(e.amount) FROM EarningTransaction e "
			+ "WHERE e.userId = :userId AND e.createdAt >= :since AND e.type != 'WITHDRAWAL' "
			+ "GROUP BY CAST(e.createdAt AS date) ORDER BY CAST(e.createdAt AS date)")
	List<Object[]> getDailyEarnings(@org.springframework.data.repository.query.Param("userId") Long userId,
			@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
