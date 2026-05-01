package com.yogesh.service;

import lombok.extern.slf4j.Slf4j;

import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestedUsersService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final BlockRepository blockRepository;

	/**
	 * Suggested users — jinhe main follow nahi karta, aur block nahi kiya. Simple
	 * algorithm: followers of my followings (friends-of-friends). Fallback: latest
	 * joined users.
	 */
	public List<Map<String, Object>> getSuggestions(Long myId, int limit) {
		// Jinhe main follow karta hoon
		Set<Long> alreadyFollowing = followRepository.findFollowingIdsByFollowerId(myId);

		// Jinhone mujhe block kiya ya maine jinko block kiya
		Set<Long> blocked = new HashSet<>(blockRepository.findBlockedAndBlockerIds(myId));

		// Friends-of-friends: mere followings ke followers
		Set<Long> foaf = new HashSet<>();
		for (Long fid : alreadyFollowing) {
			followRepository.findFollowerIdsByFollowingId(fid).stream()
					.filter(uid -> !uid.equals(myId) && !alreadyFollowing.contains(uid) && !blocked.contains(uid))
					.forEach(foaf::add);
		}

		List<Long> candidateIds = new ArrayList<>(foaf);

		// FoaF se enough nahi mila toh latest users se fill karo
		if (candidateIds.size() < limit) {
			userRepository.findAllIds().stream()
					.filter(uid -> !uid.equals(myId) && !alreadyFollowing.contains(uid) && !blocked.contains(uid))
					.filter(uid -> !candidateIds.contains(uid)).limit(limit - candidateIds.size())
					.forEach(candidateIds::add);
		}

		List<Long> finalIds = candidateIds.stream().limit(limit).collect(Collectors.toList());
		Map<Long, User> userMap = userRepository.findAllById(finalIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		return finalIds.stream().map(uid -> {
			User u = userMap.get(uid);
			if (u == null)
				return null;
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("userId", uid);
			item.put("name", u.getName());
			item.put("avatar", u.getAvatar());
			item.put("followers", followRepository.countByFollowingId(uid));
			return item;
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}
}