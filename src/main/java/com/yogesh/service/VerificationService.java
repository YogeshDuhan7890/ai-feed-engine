package com.yogesh.service;

import com.yogesh.model.User;
import com.yogesh.model.VerificationRequest;
import com.yogesh.repository.UserRepository;
import com.yogesh.repository.VerificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ModerationService moderationService;

    @Transactional
    public Map<String, Object> submit(Long userId, Map<String, Object> body) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.isVerified()) {
            return Map.of("success", false, "message", "Aap already verified ho");
        }

        VerificationRequest existing = verificationRequestRepository.findByUserId(userId).orElse(null);
        if (existing != null && "PENDING".equalsIgnoreCase(existing.getStatus())) {
            return Map.of("success", false, "message", "Verification request already pending");
        }

        VerificationRequest req = existing != null ? existing : new VerificationRequest();
        req.setUserId(userId);
        req.setStatus("PENDING");
        req.setDecidedAt(null);
        req.setDecidedBy(null);
        req.setDecisionReason(null);

        String fullName = getStr(body, "fullName");
        String category = getStr(body, "category");
        String govtIdType = getStr(body, "govtIdType");
        String govtIdLast4 = getStr(body, "govtIdLast4");
        String socialLinks = getStr(body, "socialLinks");
        String notes = getStr(body, "notes");

        if (fullName != null && !fullName.isBlank()) moderationService.assertAllowed(fullName, "Full name");
        if (socialLinks != null && !socialLinks.isBlank()) moderationService.assertAllowed(socialLinks, "Social links");
        if (notes != null && !notes.isBlank()) moderationService.assertAllowed(notes, "Notes");

        req.setFullName(trimTo(fullName, 120));
        req.setCategory(trimTo(category, 60));
        req.setGovtIdType(trimTo(govtIdType, 40));
        req.setGovtIdLast4(trimTo(govtIdLast4, 10));
        req.setSocialLinks(trimTo(socialLinks, 1200));
        req.setNotes(trimTo(notes, 1200));
        if (req.getCreatedAt() == null) req.setCreatedAt(LocalDateTime.now());

        VerificationRequest saved = verificationRequestRepository.save(req);
        return Map.of("success", true, "requestId", saved.getId(), "status", saved.getStatus());
    }

    public VerificationRequest getMy(Long userId) {
        return verificationRequestRepository.findByUserId(userId).orElse(null);
    }

    public List<VerificationRequest> listPending(int limit) {
        return verificationRequestRepository.findByStatus("PENDING", PageRequest.of(0, Math.max(1, Math.min(200, limit))));
    }

    @Transactional
    public Map<String, Object> approve(Long requestId, String adminEmail, String reason) {
        VerificationRequest req = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            return Map.of("success", false, "message", "Request already processed");
        }

        User user = userRepository.findById(req.getUserId()).orElseThrow();
        user.setVerified(true);
        userRepository.save(user);

        req.setStatus("APPROVED");
        req.setDecidedAt(LocalDateTime.now());
        req.setDecidedBy(adminEmail);
        req.setDecisionReason(trimTo(reason, 800));
        verificationRequestRepository.save(req);

        try {
            emailService.sendVerificationApprovedEmail(user.getEmail(), user.getName());
        } catch (Exception ignored) {}

        return Map.of("success", true, "status", "APPROVED");
    }

    @Transactional
    public Map<String, Object> reject(Long requestId, String adminEmail, String reason) {
        VerificationRequest req = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            return Map.of("success", false, "message", "Request already processed");
        }

        User user = userRepository.findById(req.getUserId()).orElseThrow();

        req.setStatus("REJECTED");
        req.setDecidedAt(LocalDateTime.now());
        req.setDecidedBy(adminEmail);
        req.setDecisionReason(trimTo(reason, 800));
        verificationRequestRepository.save(req);

        try {
            emailService.sendVerificationRejectedEmail(user.getEmail(), user.getName(), reason);
        } catch (Exception ignored) {}

        return Map.of("success", true, "status", "REJECTED");
    }

    private String getStr(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}

