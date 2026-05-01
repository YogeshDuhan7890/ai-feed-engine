package com.yogesh.service;

import com.yogesh.model.Report;
import com.yogesh.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;

	private static final List<String> VALID_REASONS = List.of("SPAM", "HATE", "VIOLENCE", "NUDITY", "HARASSMENT",
			"MISINFORMATION", "FAKE_ACCOUNT", "OTHER");

	// ── Submit report ─────────────────────────────────────────────────────────
	@Transactional
	public Map<String, Object> report(Long reporterId, String targetType, Long targetId, String reason,
			String description) {

		// Validate target type
		if (!"USER".equals(targetType) && !"POST".equals(targetType))
			throw new IllegalArgumentException("Invalid target type");

		// Validate reason
		String upperReason = reason != null ? reason.toUpperCase() : "OTHER";
		if (!VALID_REASONS.contains(upperReason))
			upperReason = "OTHER";

		// Duplicate check
		if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
			return Map.of("success", false, "message", "Pehle se report kar chuke ho");
		}

		Report r = new Report();
		r.setReporterId(reporterId);
		r.setTargetType(targetType);
		r.setTargetId(targetId);
		r.setReason(upperReason);
		r.setDescription(description != null ? description.trim() : null);
		reportRepository.save(r);

		log.info("Report submitted: reporter={} type={} target={} reason={}", reporterId, targetType, targetId,
				upperReason);

		return Map.of("success", true, "message", "Report submit ho gayi. Hum review karenge.");
	}

	// ── Admin: get pending reports ────────────────────────────────────────────
	public List<Report> getPendingReports(int page) {
		return reportRepository.findByStatusOrderByCreatedAtDesc("PENDING", PageRequest.of(page, 20)).getContent();
	}

	// ── Admin: resolve report ─────────────────────────────────────────────────
	@Transactional
	public Map<String, String> resolveReport(Long reportId, String status) {
		reportRepository.findById(reportId).ifPresent(r -> {
			r.setStatus(status); // RESOLVED or DISMISSED
			reportRepository.save(r);
		});
		return Map.of("status", status);
	}

	// ── Count pending (admin badge) ───────────────────────────────────────────
	public long countPending() {
		return reportRepository.countByStatus("PENDING");
	}
	
	public List<Report> getResolvedReports(int page) {
	    return reportRepository
	        .findByStatusOrderByCreatedAtDesc("RESOLVED", PageRequest.of(page, 20))
	        .getContent();
	}
}