package com.yogesh.repository;

import com.yogesh.model.EmailOtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface EmailOtpTokenRepository extends JpaRepository<EmailOtpToken, Long> {

	// Latest unused OTP dhundo email + purpose ke liye
	Optional<EmailOtpToken> findTopByEmailAndPurposeAndUsedFalseOrderByIdDesc(String email, String purpose);

	Optional<EmailOtpToken> findTopByEmailAndPurposeOrderByIdDesc(String email, String purpose);

	@Modifying
	@Transactional
	@Query("DELETE FROM EmailOtpToken t WHERE t.email = :email AND t.purpose = :purpose")
	void deleteByEmailAndPurpose(@Param("email") String email, @Param("purpose") String purpose);
}
