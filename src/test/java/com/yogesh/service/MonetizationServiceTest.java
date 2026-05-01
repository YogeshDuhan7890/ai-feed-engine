package com.yogesh.service;

import com.yogesh.model.CreatorWallet;
import com.yogesh.repository.CreatorWalletRepository;
import com.yogesh.repository.EarningTransactionRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonetizationServiceTest {

	@Mock
	private CreatorWalletRepository walletRepo;

	@Mock
	private EarningTransactionRepository txRepo;

	@Mock
	private WithdrawalRequestRepository withdrawalRepo;

	@Mock
	private PostRepository postRepo;

	@InjectMocks
	private MonetizationService monetizationService;

	@Test
	void enableMonetizationRequiresMinimumVideos() {
		CreatorWallet wallet = new CreatorWallet();
		wallet.setUserId(5L);
		when(walletRepo.findByUserId(5L)).thenReturn(Optional.of(wallet));
		when(postRepo.countByUserId(5L)).thenReturn(3L);

		Map<String, Object> result = monetizationService.enableMonetization(5L, "demo@upi");

		assertEquals(false, result.get("success"));
	}

	@Test
	void withdrawalFailsWhenBalanceIsLow() {
		CreatorWallet wallet = new CreatorWallet();
		wallet.setUserId(9L);
		wallet.setMonetizationEnabled(true);
		wallet.setUpiId("creator@upi");
		wallet.setAvailableBalance(new BigDecimal("50.00"));
		when(walletRepo.findByUserId(9L)).thenReturn(Optional.of(wallet));

		Map<String, Object> result = monetizationService.requestWithdrawal(9L, new BigDecimal("100.00"));

		assertEquals(false, result.get("success"));
	}
}
