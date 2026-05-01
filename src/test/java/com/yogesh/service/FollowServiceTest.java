package com.yogesh.service;

import com.yogesh.model.Follow;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

	@Mock
	private FollowRepository followRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private NotificationService notificationService;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private SetOperations<String, String> setOperations;

	@Mock
	private MessageSource messageSource;

	@InjectMocks
	private FollowService followService;

	@Test
	void privateAccountCreatesPendingRequest() {
		User target = new User();
		target.setId(2L);
		target.setPrivateAccount(true);

		when(userRepository.findById(2L)).thenReturn(Optional.of(target));
		when(followRepository.findFirstByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.follow(1L, 2L);

		ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
		verify(followRepository).save(captor.capture());
		assertEquals(Follow.STATUS_PENDING, captor.getValue().getStatus());
		assertEquals("REQUESTED", result.status());
		verify(userRepository, never()).incrementFollowers(any());
		verify(userRepository, never()).incrementFollowing(any());
		verify(setOperations, never()).add(any(), any());
	}

	@Test
	void publicAccountCreatesAcceptedFollowAndSyncsRedis() {
		User target = new User();
		target.setId(2L);
		target.setPrivateAccount(false);

		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		when(userRepository.findById(2L)).thenReturn(Optional.of(target));
		when(followRepository.findFirstByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.follow(1L, 2L);

		assertEquals("FOLLOWING", result.status());
		verify(setOperations).add("user:2:followers", "1");
		verify(setOperations).add("user:1:following", "2");
		verify(userRepository).incrementFollowers(2L);
		verify(userRepository).incrementFollowing(1L);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void acceptRequestPromotesPendingFollow() {
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		when(followRepository.acceptPendingFollow(1L, 2L)).thenReturn(1);
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.acceptRequest(2L, 1L);

		assertEquals("FOLLOWING", result.status());
		verify(setOperations).add("user:2:followers", "1");
		verify(setOperations).add("user:1:following", "2");
		verify(userRepository).incrementFollowers(2L);
		verify(userRepository).incrementFollowing(1L);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void acceptRequestDoesNotDoubleIncrementWhenAlreadyAccepted() {
		when(followRepository.acceptPendingFollow(1L, 2L)).thenReturn(0);
		when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.acceptRequest(2L, 1L);

		assertEquals("FOLLOWING", result.status());
		verify(userRepository, never()).incrementFollowers(any());
		verify(userRepository, never()).incrementFollowing(any());
		verify(setOperations, never()).add(any(), any());
	}

	@Test
	void unfollowOnlyDecrementsWhenAcceptedRowWasDeleted() {
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		when(followRepository.deleteByFollowerIdAndFollowingIdAndStatus(1L, 2L, Follow.STATUS_ACCEPTED)).thenReturn(1);
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.unfollow(1L, 2L);

		assertEquals("NONE", result.status());
		verify(setOperations).remove("user:2:followers", "1");
		verify(setOperations).remove("user:1:following", "2");
		verify(userRepository).decrementFollowers(2L);
		verify(userRepository).decrementFollowing(1L);
	}

	@Test
	void unfollowPendingRequestDoesNotDecrementCounters() {
		when(followRepository.deleteByFollowerIdAndFollowingIdAndStatus(1L, 2L, Follow.STATUS_ACCEPTED)).thenReturn(0);
		when(followRepository.deleteByFollowerIdAndFollowingIdAndStatus(1L, 2L, Follow.STATUS_PENDING)).thenReturn(1);
		when(messageSource.getMessage(anyString(), isNull(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));

		FollowService.FollowActionResult result = followService.unfollow(1L, 2L);

		assertEquals("NONE", result.status());
		verify(userRepository, never()).decrementFollowers(any());
		verify(userRepository, never()).decrementFollowing(any());
		verify(setOperations, never()).remove(any(), any());
	}
}
