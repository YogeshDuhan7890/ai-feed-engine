package com.yogesh.service;

import com.yogesh.dto.FollowUserDTO;
import com.yogesh.model.Follow;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

	public record FollowActionResult(boolean success, String status, String message) {
	}

	private final FollowRepository followRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final StringRedisTemplate redisTemplate;
	private final MessageSource messageSource;

	@Transactional
	public FollowActionResult follow(Long followerId, Long targetId) {
		if (followerId == null || targetId == null || followerId.equals(targetId)) {
			return new FollowActionResult(false, "INVALID", msg("follow.self", "Apne aap ko follow nahi kar sakte"));
		}

		User target = userRepository.findById(targetId)
				.orElseThrow(() -> new RuntimeException(msg("follow.target.notFound", "Target user nahi mila")));

		Optional<Follow> existingOpt = followRepository.findFirstByFollowerIdAndFollowingId(followerId, targetId);
		if (existingOpt.isPresent()) {
			Follow existing = existingOpt.get();
			if (Follow.STATUS_ACCEPTED.equalsIgnoreCase(existing.getStatus())) {
				return new FollowActionResult(true, "FOLLOWING", msg("follow.alreadyFollowing", "Aap pehle se follow kar rahe ho"));
			}
			if (Follow.STATUS_PENDING.equalsIgnoreCase(existing.getStatus())) {
				return new FollowActionResult(true, "REQUESTED", msg("follow.request.pending", "Follow request already pending"));
			}
		}

		Follow follow = new Follow();
		follow.setFollowerId(followerId);
		follow.setFollowingId(targetId);

		if (target.isPrivateAccount()) {
			follow.setStatus(Follow.STATUS_PENDING);
			followRepository.save(follow);
			createNotification(targetId, followerId, "FOLLOW_REQUEST");
			return new FollowActionResult(true, "REQUESTED", msg("follow.request.sent", "Follow request bhej di gayi"));
		}

		follow.setStatus(Follow.STATUS_ACCEPTED);
		followRepository.save(follow);
		activateFollowCounters(followerId, targetId);
		createNotification(targetId, followerId, "FOLLOW");
		return new FollowActionResult(true, "FOLLOWING", msg("follow.nowFollowing", "Ab aap follow kar rahe ho"));
	}

	@Transactional
	public FollowActionResult unfollow(Long followerId, Long targetId) {
		int acceptedDeleted = followRepository.deleteByFollowerIdAndFollowingIdAndStatus(
				followerId, targetId, Follow.STATUS_ACCEPTED);
		if (acceptedDeleted > 0) {
			deactivateFollowCounters(followerId, targetId);
			return new FollowActionResult(true, "NONE", msg("follow.unfollowed", "Unfollow ho gaya"));
		}

		int pendingDeleted = followRepository.deleteByFollowerIdAndFollowingIdAndStatus(
				followerId, targetId, Follow.STATUS_PENDING);
		if (pendingDeleted > 0) {
			return new FollowActionResult(true, "NONE", msg("follow.request.cancelled", "Follow request cancel ho gayi"));
		}

		return new FollowActionResult(true, "NONE", msg("follow.alreadyNotFollowing", "Aap pehle se follow nahi kar rahe ho"));
	}

	@Transactional
	public FollowActionResult acceptRequest(Long currentUserId, Long requesterId) {
		int accepted = followRepository.acceptPendingFollow(requesterId, currentUserId);
		if (accepted == 0) {
			if (followRepository.existsByFollowerIdAndFollowingId(requesterId, currentUserId)) {
				return new FollowActionResult(true, "FOLLOWING", msg("follow.alreadyFollowing", "Aap pehle se follow kar rahe ho"));
			}
			throw new RuntimeException(msg("follow.request.notFound", "Pending follow request nahi mili"));
		}

		activateFollowCounters(requesterId, currentUserId);
		createNotification(requesterId, currentUserId, "FOLLOW_ACCEPTED");
		return new FollowActionResult(true, "FOLLOWING", msg("follow.request.accepted", "Follow request accept ho gayi"));
	}

	@Transactional
	public FollowActionResult rejectRequest(Long currentUserId, Long requesterId) {
		Follow relation = followRepository
				.findFirstByFollowerIdAndFollowingIdAndStatus(requesterId, currentUserId, Follow.STATUS_PENDING)
				.orElseThrow(() -> new RuntimeException(msg("follow.request.notFound", "Pending follow request nahi mili")));
		followRepository.delete(relation);
		return new FollowActionResult(true, "NONE", msg("follow.request.rejected", "Follow request reject ho gayi"));
	}

	public String getFollowStatus(Long followerId, Long targetId) {
		if (followerId == null || targetId == null || followerId.equals(targetId)) {
			return "NONE";
		}
		return followRepository.findFirstByFollowerIdAndFollowingId(followerId, targetId)
				.map(follow -> Follow.STATUS_PENDING.equalsIgnoreCase(follow.getStatus()) ? "REQUESTED" : "FOLLOWING")
				.orElse("NONE");
	}

	public List<FollowUserDTO> getPendingRequests(Long userId) {
		List<Long> requesterIds = followRepository.findPendingByFollowingId(userId).stream().map(Follow::getFollowerId)
				.toList();
		return mapUsers(requesterIds, userId);
	}

	public long getPendingRequestCount(Long userId) {
		return followRepository.countPendingByFollowingId(userId);
	}

	public List<FollowUserDTO> getFollowers(Long userId, Long currentUserId) {
		List<Long> followerIds = followRepository.findByFollowingId(userId).stream().map(Follow::getFollowerId).toList();
		return mapUsers(followerIds, currentUserId);
	}

	public List<FollowUserDTO> getFollowing(Long userId, Long currentUserId) {
		List<Long> followingIds = followRepository.findByFollowerId(userId).stream().map(Follow::getFollowingId)
				.toList();
		return mapUsers(followingIds, currentUserId);
	}

	private List<FollowUserDTO> mapUsers(List<Long> userIds, Long currentUserId) {
		if (userIds.isEmpty()) {
			return List.of();
		}

		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, user -> user));

		List<Long> myFollowingIds = currentUserId != null
				? followRepository.findByFollowerId(currentUserId).stream().map(Follow::getFollowingId).toList()
				: List.of();

		return userIds.stream().map(userMap::get).filter(user -> user != null)
				.map(user -> new FollowUserDTO(
						user.getId(),
						user.getName(),
						user.getUsername(),
						user.getAvatar(),
						user.getBio(),
						user.isVerified(),
						(int) followRepository.countByFollowingId(user.getId()),
						myFollowingIds.contains(user.getId())))
				.toList();
	}

	private void activateFollowCounters(Long followerId, Long targetId) {
		redisTemplate.opsForSet().add("user:" + targetId + ":followers", String.valueOf(followerId));
		redisTemplate.opsForSet().add("user:" + followerId + ":following", String.valueOf(targetId));
		applyFollowCounterDelta(followerId, targetId, true);
	}

	private void deactivateFollowCounters(Long followerId, Long targetId) {
		redisTemplate.opsForSet().remove("user:" + targetId + ":followers", String.valueOf(followerId));
		redisTemplate.opsForSet().remove("user:" + followerId + ":following", String.valueOf(targetId));
		applyFollowCounterDelta(followerId, targetId, false);
	}

	private void applyFollowCounterDelta(Long followerId, Long targetId, boolean increment) {
		if (followerId == null || targetId == null) {
			return;
		}
		if (increment) {
			userRepository.incrementFollowers(targetId);
			userRepository.incrementFollowing(followerId);
		} else {
			userRepository.decrementFollowers(targetId);
			userRepository.decrementFollowing(followerId);
		}
	}

	private void createNotification(Long recipientId, Long actorId, String type) {
		try {
			notificationService.create(recipientId, actorId, type, null, null);
		} catch (Exception e) {
			log.warn("Follow notification error: {}", e.getMessage());
		}
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
