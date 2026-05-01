package com.yogesh.repository;

import com.yogesh.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

	// Duplicate check — same reporter + target
	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, String targetType, Long targetId);

	// Admin: all pending reports
	Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

	// Reports on a specific user (admin)
	List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

	// Count pending reports
	long countByStatus(String status);

	// Count reports on a target
	long countByTargetTypeAndTargetId(String targetType, Long targetId);
}