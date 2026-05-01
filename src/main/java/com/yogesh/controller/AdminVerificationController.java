package com.yogesh.controller;

import com.yogesh.model.VerificationRequest;
import com.yogesh.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/verification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    private final VerificationService verificationService;

    /** GET /api/admin/verification/requests?limit=50 */
    @GetMapping("/requests")
    public List<VerificationRequest> pending(@RequestParam(defaultValue = "50") int limit) {
        return verificationService.listPending(limit);
    }

    /** POST /api/admin/verification/{requestId}/approve */
    @PostMapping("/{requestId}/approve")
    public Map<String, Object> approve(@PathVariable Long requestId, @RequestBody(required = false) Map<String, Object> body,
                                       Authentication auth) {
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        return verificationService.approve(requestId, auth != null ? auth.getName() : "admin", reason);
    }

    /** POST /api/admin/verification/{requestId}/reject */
    @PostMapping("/{requestId}/reject")
    public Map<String, Object> reject(@PathVariable Long requestId, @RequestBody(required = false) Map<String, Object> body,
                                      Authentication auth) {
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        return verificationService.reject(requestId, auth != null ? auth.getName() : "admin", reason);
    }
}

