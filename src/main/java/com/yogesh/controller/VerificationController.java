package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.model.VerificationRequest;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;
    private final UserRepository userRepository;

    /** GET /api/verification/me */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        User me = meUser(auth);
        VerificationRequest req = verificationService.getMy(me.getId());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("isVerified", me.isVerified());
        res.put("request", req);
        return res;
    }

    /** POST /api/verification/request */
    @PostMapping("/request")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body, Authentication auth) {
        User me = meUser(auth);
        return verificationService.submit(me.getId(), body);
    }

    private User meUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}

