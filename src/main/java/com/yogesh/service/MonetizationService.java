package com.yogesh.service;

import com.yogesh.model.CreatorWallet;
import com.yogesh.model.EarningTransaction;
import com.yogesh.model.WithdrawalRequest;
import com.yogesh.repository.CreatorWalletRepository;
import com.yogesh.repository.EarningTransactionRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonetizationService {

	private static final BigDecimal RATE_PER_1000_VIEWS = new BigDecimal("10.00");
	private static final BigDecimal RATE_PER_LIKE = new BigDecimal("0.05");
	private static final BigDecimal RATE_PER_SHARE = new BigDecimal("0.20");
	private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("100.00");

	private static final BigDecimal TIER_RISING = new BigDecimal("500");
	private static final BigDecimal TIER_PRO = new BigDecimal("2000");
	private static final BigDecimal TIER_ELITE = new BigDecimal("10000");

	private final CreatorWalletRepository walletRepo;
	private final EarningTransactionRepository txRepo;
	private final WithdrawalRequestRepository withdrawalRepo;
	private final PostRepository postRepo;

	public CreatorWallet getOrCreateWallet(Long userId) {
		return walletRepo.findByUserId(userId).orElseGet(() -> {
			CreatorWallet wallet = new CreatorWallet();
			wallet.setUserId(userId);
			return walletRepo.save(wallet);
		});
	}

	@Transactional
	public Map<String, Object> enableMonetization(Long userId, String upiId) {
		CreatorWallet wallet = getOrCreateWallet(userId);
		long videoCount = postRepo.countByUserId(userId);
		if (videoCount < 10) {
			return Map.of("success", false, "message",
					"Monetization ke liye kam se kam 10 videos chahiye. Aapke paas: " + videoCount);
		}

		wallet.setMonetizationEnabled(true);
		wallet.setUpiId(upiId);
		wallet.setUpdatedAt(LocalDateTime.now());
		walletRepo.save(wallet);

		log.info("Monetization enabled for userId={}", userId);
		return Map.of("success", true, "message", "Monetization enable ho gayi! Ab kamai shuru karo");
	}

	@Transactional
	public void recordViewEarning(Long postId, Long creatorId) {
		CreatorWallet wallet = walletRepo.findByUserId(creatorId).orElse(null);
		if (wallet == null || !wallet.isMonetizationEnabled()) {
			return;
		}

		BigDecimal earning = RATE_PER_1000_VIEWS.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
		addEarning(wallet, creatorId, postId, "VIEW_REWARD", earning, "Video view reward - Rs " + earning, null);
	}

	@Transactional
	public void recordLikeEarning(Long postId, Long creatorId) {
		CreatorWallet wallet = walletRepo.findByUserId(creatorId).orElse(null);
		if (wallet == null || !wallet.isMonetizationEnabled()) {
			return;
		}

		addEarning(wallet, creatorId, postId, "LIKE_BONUS", RATE_PER_LIKE, "Like bonus - Rs " + RATE_PER_LIKE, null);
	}

	@Transactional
	public void recordShareEarning(Long postId, Long creatorId) {
		CreatorWallet wallet = walletRepo.findByUserId(creatorId).orElse(null);
		if (wallet == null || !wallet.isMonetizationEnabled()) {
			return;
		}

		addEarning(wallet, creatorId, postId, "SHARE_BONUS", RATE_PER_SHARE, "Share bonus - Rs " + RATE_PER_SHARE,
				null);
	}

	@Transactional
	public void checkMilestones(Long userId) {
		CreatorWallet wallet = walletRepo.findByUserId(userId).orElse(null);
		if (wallet == null || !wallet.isMonetizationEnabled()) {
			return;
		}

		BigDecimal total = wallet.getTotalEarnings();
		String newTier = calculateTier(total);
		if (newTier.equals(wallet.getTier())) {
			return;
		}

		wallet.setTier(newTier);
		walletRepo.save(wallet);

		BigDecimal bonus = switch (newTier) {
		case "RISING" -> new BigDecimal("50");
		case "PRO" -> new BigDecimal("200");
		case "ELITE" -> new BigDecimal("1000");
		default -> BigDecimal.ZERO;
		};

		if (bonus.compareTo(BigDecimal.ZERO) > 0) {
			addEarning(wallet, userId, null, "MILESTONE_BONUS", bonus, newTier + " tier bonus! Rs " + bonus, null);
			log.info("Milestone bonus: userId={} tier={} bonus={}", userId, newTier, bonus);
		}
	}

	@Transactional
	public Map<String, Object> requestWithdrawal(Long userId, BigDecimal amount) {
		CreatorWallet wallet = walletRepo.findByUserId(userId).orElse(null);
		if (wallet == null) {
			return Map.of("success", false, "message", "Wallet nahi mila");
		}
		if (!wallet.isMonetizationEnabled()) {
			return Map.of("success", false, "message", "Monetization enable nahi hai");
		}
		if (wallet.getUpiId() == null || wallet.getUpiId().isBlank()) {
			return Map.of("success", false, "message", "UPI ID set nahi ki - settings mein jaao");
		}
		if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
			return Map.of("success", false, "message", "Minimum withdrawal Rs " + MIN_WITHDRAWAL + " hai");
		}
		if (wallet.getAvailableBalance().compareTo(amount) < 0) {
			return Map.of("success", false, "message",
					"Insufficient balance. Available: Rs " + wallet.getAvailableBalance());
		}
		if (withdrawalRepo.existsByUserIdAndStatus(userId, "PENDING")) {
			return Map.of("success", false, "message", "Ek withdrawal request pehle se pending hai");
		}

		WithdrawalRequest request = new WithdrawalRequest();
		request.setUserId(userId);
		request.setAmount(amount);
		request.setUpiId(wallet.getUpiId());
		withdrawalRepo.save(request);

		wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
		wallet.setUpdatedAt(LocalDateTime.now());
		walletRepo.save(wallet);

		EarningTransaction tx = new EarningTransaction();
		tx.setUserId(userId);
		tx.setType("WITHDRAWAL");
		tx.setAmount(amount.negate());
		tx.setDescription("Withdrawal request - Rs " + amount + " to " + wallet.getUpiId());
		txRepo.save(tx);

		log.info("Withdrawal requested: userId={} amount={}", userId, amount);
		return Map.of("success", true, "message", "Withdrawal request bhej di! 2-3 working days mein process hogi.",
				"requestId", request.getId());
	}

	public Map<String, Object> getDashboard(Long userId) {
		CreatorWallet wallet = getOrCreateWallet(userId);
		List<EarningTransaction> recent = txRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20));
		List<WithdrawalRequest> withdrawals = withdrawalRepo.findByUserIdOrderByCreatedAtDesc(userId);
		long videoCount = postRepo.countByUserId(userId);

		Map<String, Object> dashboard = new LinkedHashMap<>();
		dashboard.put("monetizationEnabled", wallet.isMonetizationEnabled());
		dashboard.put("tier", wallet.getTier());
		dashboard.put("totalEarnings", wallet.getTotalEarnings());
		dashboard.put("availableBalance", wallet.getAvailableBalance());
		dashboard.put("totalWithdrawn", wallet.getTotalWithdrawn());
		dashboard.put("upiId", wallet.getUpiId());
		dashboard.put("videoCount", videoCount);
		dashboard.put("canEnable", !wallet.isMonetizationEnabled() && videoCount >= 10);
		dashboard.put("videosNeeded", Math.max(0, 10 - videoCount));
		dashboard.put("minWithdrawal", MIN_WITHDRAWAL);
		dashboard.put("recentTransactions", recent.stream().map(tx -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", tx.getId());
			item.put("type", tx.getType());
			item.put("amount", tx.getAmount());
			item.put("description", tx.getDescription());
			item.put("createdAt", tx.getCreatedAt());
			return item;
		}).toList());
		dashboard.put("withdrawals", withdrawals.stream().map(withdrawal -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", withdrawal.getId());
			item.put("amount", withdrawal.getAmount());
			item.put("status", withdrawal.getStatus());
			item.put("upiId", withdrawal.getUpiId());
			item.put("createdAt", withdrawal.getCreatedAt());
			item.put("processedAt", withdrawal.getProcessedAt());
			return item;
		}).toList());
		dashboard.put("tierProgress", getTierProgress(wallet.getTotalEarnings(), wallet.getTier()));
		return dashboard;
	}

	public boolean hasProcessedExternalTransaction(String externalTxnId) {
		return externalTxnId != null && !externalTxnId.isBlank() && txRepo.existsByExternalTxnId(externalTxnId);
	}

	@Transactional
	public boolean recordTipEarning(Long creatorId, Long postId, BigDecimal amount, String paymentId) {
		if (creatorId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || paymentId == null
				|| paymentId.isBlank()) {
			return false;
		}
		if (txRepo.existsByExternalTxnId(paymentId)) {
			return false;
		}

		CreatorWallet wallet = getOrCreateWallet(creatorId);
		try {
			String description = "Tip received via Razorpay - Rs " + amount + " (ID: " + paymentId + ")";
			addEarning(wallet, creatorId, postId, "TIP_RECEIVED", amount, description, paymentId);
			log.info("Tip credited: creatorId={} amount={} paymentId={}", creatorId, amount, paymentId);
			return true;
		} catch (DataIntegrityViolationException e) {
			log.warn("Duplicate tip payment ignored: {}", paymentId);
			return false;
		}
	}

	private void addEarning(CreatorWallet wallet, Long userId, Long postId, String type, BigDecimal amount,
			String description, String externalTxnId) {
		EarningTransaction tx = new EarningTransaction();
		tx.setUserId(userId);
		tx.setPostId(postId);
		tx.setType(type);
		tx.setAmount(amount);
		tx.setDescription(description);
		tx.setExternalTxnId(externalTxnId);
		txRepo.save(tx);

		wallet.setTotalEarnings(wallet.getTotalEarnings().add(amount));
		wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
		wallet.setUpdatedAt(LocalDateTime.now());
		walletRepo.save(wallet);

		checkMilestones(userId);
	}

	private String calculateTier(BigDecimal total) {
		if (total.compareTo(TIER_ELITE) >= 0) {
			return "ELITE";
		}
		if (total.compareTo(TIER_PRO) >= 0) {
			return "PRO";
		}
		if (total.compareTo(TIER_RISING) >= 0) {
			return "RISING";
		}
		return "NONE";
	}

	private Map<String, Object> getTierProgress(BigDecimal total, String currentTier) {
		String normalizedTier = currentTier == null ? "NONE" : currentTier;
		Map<String, Object> progress = new LinkedHashMap<>();
		BigDecimal nextThreshold = switch (normalizedTier) {
		case "NONE" -> TIER_RISING;
		case "RISING" -> TIER_PRO;
		case "PRO" -> TIER_ELITE;
		default -> TIER_ELITE;
		};
		BigDecimal previousThreshold = switch (normalizedTier) {
		case "NONE" -> BigDecimal.ZERO;
		case "RISING" -> TIER_RISING;
		case "PRO" -> TIER_PRO;
		default -> TIER_ELITE;
		};

		progress.put("current", normalizedTier);
		progress.put("next", normalizedTier.equals("ELITE") ? "ELITE" : switch (normalizedTier) {
		case "NONE" -> "RISING";
		case "RISING" -> "PRO";
		case "PRO" -> "ELITE";
		default -> "ELITE";
		});
		progress.put("total", total);
		progress.put("nextThreshold", nextThreshold);

		BigDecimal range = nextThreshold.subtract(previousThreshold);
		BigDecimal earned = total.subtract(previousThreshold).max(BigDecimal.ZERO);
		int progressPct = range.compareTo(BigDecimal.ZERO) > 0
				? earned.multiply(new BigDecimal("100")).divide(range, 0, RoundingMode.DOWN).intValue()
				: 100;
		progress.put("progressPct", Math.min(progressPct, 100));
		return progress;
	}
}
