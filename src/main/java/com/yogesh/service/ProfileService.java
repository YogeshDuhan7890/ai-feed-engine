package com.yogesh.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.yogesh.dto.ProfileDTO;
import com.yogesh.dto.UpdateProfileDTO;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileService {

	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final FollowRepository followRepository;

	public User getUserByEmail(String email) {
		return userRepository.findByEmail(email).orElseThrow();
	}

	public long countByUserId(Long userId) {
		return postRepository.countByUserId(userId);
	}

	public List<Post> getVideosByUserId(Long userId) {
		return postRepository.findByUserId(userId);
	}

	public ProfileDTO getProfile(Long userId) {

		User user = userRepository.findById(userId).orElseThrow();

		ProfileDTO dto = new ProfileDTO();

		dto.setId(user.getId());
		dto.setName(user.getName());
		dto.setEmail(user.getEmail());
		dto.setBio(user.getBio());
		dto.setAvatar(user.getAvatar());
		dto.setInterests(user.getInterests());

		/*
		 * BUG FIX: followersCount / followingCount use karo ProfileDTO mein "following"
		 * rename karke "followingCount" kiya — Lombok boolean clash fix ke liye.
		 */
		dto.setFollowersCount((int) followRepository.countByFollowingId(userId));
		dto.setFollowingCount((int) followRepository.countByFollowerId(userId));

		dto.setVideos(postRepository.countByUserId(userId));

		return dto;
	}

	public void updateProfile(Long userId, UpdateProfileDTO dto) {

		User user = userRepository.findById(userId).orElseThrow();

		if (dto.getName() != null && !dto.getName().isBlank())
			user.setName(dto.getName());

		if (dto.getBio() != null)
			user.setBio(dto.getBio());

		if (dto.getInterests() != null)
			user.setInterests(dto.getInterests());

		userRepository.save(user);
	}
}
