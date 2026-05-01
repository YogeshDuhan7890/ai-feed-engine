package com.yogesh.repository;

import com.yogesh.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	List<PushSubscription> findByUserId(Long userId);

	Optional<PushSubscription> findByEndpoint(String endpoint);

	void deleteByEndpoint(String endpoint);

	void deleteByUserId(Long userId);

	boolean existsByUserIdAndEndpoint(Long userId, String endpoint);

	@Query("SELECT COUNT(DISTINCT p.userId) FROM PushSubscription p")
	long countDistinctUserIds();
}
