package com.yogesh.controller;

import com.yogesh.model.CreatorWallet;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.CreatorWalletRepository;
import com.yogesh.repository.EarningTransactionRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.MonetizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monetization")
@RequiredArgsConstructor
public class MonetizationController {

    private final MonetizationService          monetizationService;
    private final UserRepository               userRepository;
    private final EarningTransactionRepository txRepo;
    private final CreatorWalletRepository      walletRepo;
    private final PostRepository               postRepository;

    // ── Dashboard ──────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(Authentication auth) {
        return monetizationService.getDashboard(getUser(auth).getId());
    }

    // ── Enable monetization ────────────────────────────────────────────────
    @PostMapping("/enable")
    public Map<String, Object> enable(@RequestBody Map<String, String> body,
                                       Authentication auth) {
        String upiId = body.getOrDefault("upiId", "").trim();
        if (upiId.isBlank()) return Map.of("success", false, "message", "UPI ID daalo");
        return monetizationService.enableMonetization(getUser(auth).getId(), upiId);
    }

    // ── Update UPI ID ──────────────────────────────────────────────────────
    @PostMapping("/upi")
    public Map<String, Object> updateUpi(@RequestBody Map<String, String> body,
                                          Authentication auth) {
        String upiId = body.getOrDefault("upiId", "").trim();
        if (upiId.isBlank()) return Map.of("success", false, "message", "UPI ID daalo");
        Long userId = getUser(auth).getId();
        CreatorWallet wallet = monetizationService.getOrCreateWallet(userId);
        wallet.setUpiId(upiId);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepo.save(wallet);
        return Map.of("success", true, "message", "UPI ID update ho gayi ✅");
    }

    // ── Withdrawal request ─────────────────────────────────────────────────
    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body,
                                         Authentication auth) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            return monetizationService.requestWithdrawal(getUser(auth).getId(), amount);
        } catch (Exception e) {
            return Map.of("success", false, "message", "Invalid amount");
        }
    }

    // ── Per-post earnings breakdown ────────────────────────────────────────
    @GetMapping("/earnings/posts")
    public List<Map<String, Object>> earningsByPost(Authentication auth) {
        Long userId = getUser(auth).getId();
        List<Object[]> rows = txRepo.sumEarningsByPost(userId, PageRequest.of(0, 20));

        if (rows.isEmpty()) return List.of();

        // Batch fetch posts
        List<Long> postIds = rows.stream()
            .map(r -> ((Number) r[0]).longValue())
            .collect(Collectors.toList());
        Map<Long, Post> postMap = postRepository.findAllByIds(postIds).stream()
            .collect(Collectors.toMap(Post::getId, p -> p));

        return rows.stream().map(r -> {
            Long postId = ((Number) r[0]).longValue();
            BigDecimal earnings = (BigDecimal) r[1];
            Post p = postMap.get(postId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("postId",    postId);
            m.put("earnings",  earnings);
            m.put("caption",   p != null && p.getContent() != null
                ? p.getContent().substring(0, Math.min(50, p.getContent().length())) : "");
            m.put("videoUrl",  p != null ? p.getVideoUrl() : null);
            m.put("createdAt", p != null ? p.getCreatedAt() : null);
            return m;
        }).collect(Collectors.toList());
    }

    // ── Daily earnings chart data (last 30 days) ───────────────────────────
    @GetMapping("/earnings/chart")
    public Map<String, Object> earningsChart(
            @RequestParam(defaultValue = "30") int days,
            Authentication auth) {
        Long userId = getUser(auth).getId();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = txRepo.getDailyEarnings(userId, since);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Object[] row : rows) {
            labels.add(row[0].toString());
            values.add(row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
        }

        return Map.of(
            "labels", labels,
            "values", values,
            "days",   days,
            "total",  values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    // ── Leaderboard — top earning creators ────────────────────────────────
    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard() {
        List<CreatorWallet> top = walletRepo
            .findAllByOrderByTotalEarningsDesc(PageRequest.of(0, 10));

        Set<Long> userIds = top.stream().map(CreatorWallet::getUserId)
            .collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (CreatorWallet w : top) {
            User u = userMap.get(w.getUserId());
            if (u == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank",         rank++);
            m.put("userId",       w.getUserId());
            m.put("name",         u.getName());
            m.put("avatar",       u.getAvatar());
            m.put("tier",         w.getTier());
            m.put("totalEarnings", w.getTotalEarnings());
            m.put("videoCount",   postRepository.countByUserId(w.getUserId()));
            result.add(m);
        }
        return result;
    }

    // ── Rate info ──────────────────────────────────────────────────────────
    @GetMapping("/rates")
    public Map<String, Object> getRates() {
        return Map.of(
            "viewsPer1000", "₹10.00",
            "perLike",      "₹0.05",
            "perShare",     "₹0.20",
            "minWithdrawal","₹100",
            "tiers", List.of(
                Map.of("name","RISING", "threshold","₹500",  "bonus","₹50"),
                Map.of("name","PRO",    "threshold","₹2000", "bonus","₹200"),
                Map.of("name","ELITE",  "threshold","₹10000","bonus","₹1000")
            )
        );
    }

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}