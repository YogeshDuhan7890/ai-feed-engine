// ========== PasswordResetTokenRepository.java ==========
package com.yogesh.repository;

import com.yogesh.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
	Optional<PasswordResetToken> findByToken(String token);

	@Modifying
	@Transactional
	@Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}