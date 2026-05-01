package com.yogesh.service;

import com.yogesh.model.Story;
import com.yogesh.model.StoryView;
import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.StoryRepository;
import com.yogesh.repository.StoryViewRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

	private static final String PRIVACY_PUBLIC = "PUBLIC";
	private static final String PRIVACY_FOLLOWERS = "FOLLOWERS";
	private static final String PRIVACY_PRIVATE = "PRIVATE";
	private static final String PRIVACY_CLOSE_FRIENDS = "CLOSE_FRIENDS";

	private final StoryRepository storyRepository;
	private final StoryViewRepository storyViewRepository;
	private final FollowRepository followRepository;
	private final UserRepository userRepository;
	private final BlockRepository blockRepository;
	private final com.yogesh.repository.CloseFriendRepository closeFriendRepository;

	@Transactional
	public Story createWithPrivacy(Long userId, String mediaUrl, String mediaType, String caption, String privacy) {
		Story story = new Story();
		story.setUserId(userId);
		story.setMediaUrl(mediaUrl);
		story.setMediaType(mediaType != null ? mediaType : "IMAGE");
		story.setCaption(caption);
		story.setPrivacy(normalizePrivacy(privacy));
		story.setExpiresAt(LocalDateTime.now().plusHours(24));
		return storyRepository.save(story);
	}

	@Transactional
	public Story create(Long userId, String mediaUrl, String mediaType, String caption, String privacy) {
		return createWithPrivacy(userId, mediaUrl, mediaType, caption, privacy);
	}

	@Transactional
	public Story create(Long userId, String mediaUrl, String mediaType, String caption) {
		return create(userId, mediaUrl, mediaType, caption, PRIVACY_PUBLIC);
	}

	public List<Map<String, Object>> getFeedStories(Long myId) {
		LocalDateTime now = LocalDateTime.now();
		List<Long> followingIds = new ArrayList<>(followRepository.findFollowingIdsByFollowerId(myId));
		followingIds.removeIf(Objects::isNull);
		followingIds.removeIf(id -> id.equals(myId));
		followingIds.add(0, myId);

		Set<Long> prioritizedUserIds = new LinkedHashSet<>(followingIds);
		List<Story> prioritizedStories = storyRepository.findActiveStoriesByUserIds(followingIds, now).stream()
				.filter(story -> canViewStory(story, myId)).toList();

		List<Story> publicStories = storyRepository.findActiveStories(now).stream()
				.filter(story -> !prioritizedUserIds.contains(story.getUserId()))
				.filter(story -> PRIVACY_PUBLIC.equals(normalizePrivacy(story.getPrivacy())))
				.filter(story -> canViewStory(story, myId))
				.toList();

		List<Story> stories = new ArrayList<>(prioritizedStories);
		stories.addAll(publicStories);
		if (stories.isEmpty()) {
			return List.of();
		}

		Set<Long> userIds = stories.stream().map(Story::getUserId).collect(Collectors.toSet());
		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, user -> user));
		Map<Long, List<Story>> groupedStories = stories.stream().collect(Collectors.groupingBy(Story::getUserId));
		Set<Long> viewedStoryIds = storyViewRepository.findViewedStoryIdsByViewer(myId);

		List<Long> orderedUserIds = new ArrayList<>(prioritizedUserIds);
		Map<Long, LocalDateTime> publicLatest = publicStories.stream().collect(Collectors.toMap(Story::getUserId,
				Story::getCreatedAt, (left, right) -> left != null && (right == null || left.isAfter(right)) ? left : right));
		publicLatest.entrySet().stream()
				.sorted((a, b) -> {
					LocalDateTime left = a.getValue();
					LocalDateTime right = b.getValue();
					if (left == null && right == null) return 0;
					if (left == null) return 1;
					if (right == null) return -1;
					return right.compareTo(left);
				})
				.map(Map.Entry::getKey)
				.forEach(orderedUserIds::add);

		List<Map<String, Object>> result = new ArrayList<>();
		for (Long userId : orderedUserIds) {
			List<Story> userStories = groupedStories.get(userId);
			if (userStories == null || userStories.isEmpty()) {
				continue;
			}

			User user = userMap.get(userId);
			boolean hasUnread = userStories.stream().anyMatch(story -> !viewedStoryIds.contains(story.getId()));

			Map<String, Object> item = new HashMap<>();
			item.put("userId", userId);
			item.put("userName", user != null ? user.getName() : "User");
			item.put("userAvatar", user != null ? user.getAvatar() : null);
			item.put("isMe", userId.equals(myId));
			item.put("hasUnread", hasUnread);
			item.put("storyCount", userStories.size());
			item.put("stories", userStories.stream().map(story -> {
				Map<String, Object> map = new HashMap<>();
				map.put("id", story.getId());
				map.put("mediaUrl", story.getMediaUrl());
				map.put("mediaType", story.getMediaType());
				map.put("caption", story.getCaption());
				map.put("privacy", normalizePrivacy(story.getPrivacy()));
				map.put("storyType", story.getStoryType());
				map.put("bgColor", story.getBgColor());
				map.put("textColor", story.getTextColor());
				map.put("textContent", story.getCaption());
				map.put("expiresAt", story.getExpiresAt());
				map.put("createdAt", story.getCreatedAt());
				map.put("viewed", viewedStoryIds.contains(story.getId()));
				map.put("viewCount", story.getViewCount());
				map.put("replyCount", story.getReplyCount());
				return map;
			}).collect(Collectors.toList()));
			result.add(item);
		}

		return result;
	}

	@Transactional
	public void markViewed(Long storyId, Long viewerId) {
		Story story = requireVisibleStory(storyId, viewerId);
		if (story.getUserId().equals(viewerId) || storyViewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) {
			return;
		}

		StoryView view = new StoryView();
		view.setStoryId(storyId);
		view.setViewerId(viewerId);
		storyViewRepository.save(view);

		story.setViewCount(story.getViewCount() + 1);
		storyRepository.save(story);
	}

	@Transactional
	public void delete(Long storyId, Long userId) {
		storyRepository.findById(storyId).ifPresent(story -> {
			if (story.getUserId().equals(userId)) {
				storyRepository.delete(story);
			}
		});
	}

	public List<Map<String, Object>> getViewers(Long storyId, Long ownerId) {
		Story story = storyRepository.findById(storyId).orElseThrow(() -> new RuntimeException("Story not found"));
		if (!story.getUserId().equals(ownerId)) {
			throw new RuntimeException("Not your story");
		}

		List<StoryView> views = storyViewRepository.findByStoryId(storyId);
		List<Long> viewerIds = views.stream().map(StoryView::getViewerId).toList();
		Map<Long, User> userMap = userRepository.findAllById(viewerIds).stream()
				.collect(Collectors.toMap(User::getId, user -> user));

		return views.stream().map(view -> {
			User user = userMap.get(view.getViewerId());
			Map<String, Object> item = new HashMap<>();
			item.put("userId", view.getViewerId());
			item.put("name", user != null ? user.getName() : "User");
			item.put("avatar", user != null ? user.getAvatar() : null);
			item.put("viewedAt", view.getViewedAt());
			return item;
		}).toList();
	}

	@Transactional
	public Story createTextStory(Long userId, String text, String bgColor, String textColor, String privacy) {
		Story story = new Story();
		story.setUserId(userId);
		story.setMediaUrl("");
		story.setMediaType("TEXT");
		story.setCaption(text);
		story.setBgColor(bgColor != null ? bgColor : "linear-gradient(135deg,#6366f1,#8b5cf6)");
		story.setTextColor(textColor != null ? textColor : "#ffffff");
		story.setStoryType("TEXT");
		story.setPrivacy(normalizePrivacy(privacy));
		story.setExpiresAt(LocalDateTime.now().plusHours(24));
		return storyRepository.save(story);
	}

	@Transactional
	public Story createTextStory(Long userId, String text, String bgColor, String textColor) {
		return createTextStory(userId, text, bgColor, textColor, PRIVACY_PUBLIC);
	}

	@Transactional
	public Map<String, Object> reactToStory(Long storyId, Long userId, String emoji) {
		if (emoji == null || emoji.isBlank()) {
			throw new RuntimeException("Emoji required");
		}

		Story story = requireVisibleStory(storyId, userId);
		Map<String, Integer> reactions = parseReactions(story.getReactions());
		reactions.merge(emoji.trim(), 1, Integer::sum);
		story.setReactions(reactionsToJson(reactions));
		storyRepository.save(story);

		return Map.of("reactions", reactions, "emoji", emoji.trim());
	}

	@Transactional
	public void replyToStory(Long storyId, Long senderId, String message) {
		if (message == null || message.isBlank()) {
			return;
		}

		Story story = requireVisibleStory(storyId, senderId);
		if (!story.getUserId().equals(senderId)) {
			story.setReplyCount(story.getReplyCount() + 1);
			storyRepository.save(story);
		}
	}

	private Story requireVisibleStory(Long storyId, Long viewerId) {
		Story story = storyRepository.findById(storyId).orElseThrow(() -> new RuntimeException("Story not found"));
		if (story.getExpiresAt() == null || !story.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new RuntimeException("Story expired");
		}
		if (!canViewStory(story, viewerId)) {
			throw new RuntimeException("Story access denied");
		}
		return story;
	}

	private boolean canViewStory(Story story, Long viewerId) {
		if (story == null || viewerId == null) {
			return false;
		}
		if (story.getUserId().equals(viewerId)) {
			return true;
		}
		if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, story.getUserId())
				|| blockRepository.existsByBlockerIdAndBlockedId(story.getUserId(), viewerId)) {
			return false;
		}

		String privacy = normalizePrivacy(story.getPrivacy());
		return switch (privacy) {
		case PRIVACY_PUBLIC -> true;
		case PRIVACY_FOLLOWERS -> followRepository.existsByFollowerIdAndFollowingId(viewerId, story.getUserId());
		case PRIVACY_CLOSE_FRIENDS -> closeFriendRepository.existsByUserIdAndFriendUserId(story.getUserId(), viewerId);
		case PRIVACY_PRIVATE -> false;
		default -> false;
		};
	}

	private String normalizePrivacy(String privacy) {
		String value = privacy == null ? PRIVACY_PUBLIC : privacy.trim().toUpperCase(Locale.ROOT);
		return switch (value) {
		case PRIVACY_CLOSE_FRIENDS -> PRIVACY_CLOSE_FRIENDS;
		case PRIVACY_PUBLIC, PRIVACY_FOLLOWERS, PRIVACY_PRIVATE -> value;
		default -> PRIVACY_PUBLIC;
		};
	}

	private Map<String, Integer> parseReactions(String json) {
		Map<String, Integer> map = new LinkedHashMap<>();
		if (json == null || json.isBlank()) {
			return map;
		}

		try {
			String clean = json.replace("{", "").replace("}", "").replace("\"", "");
			for (String part : clean.split(",")) {
				String[] kv = part.split(":");
				if (kv.length == 2) {
					map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
				}
			}
		} catch (Exception ignored) {
		}
		return map;
	}

	private String reactionsToJson(Map<String, Integer> map) {
		if (map.isEmpty()) {
			return null;
		}

		StringBuilder builder = new StringBuilder("{");
		map.forEach((key, value) -> builder.append("\"").append(key).append("\":").append(value).append(","));
		if (builder.charAt(builder.length() - 1) == ',') {
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.append("}").toString();
	}

	@Scheduled(fixedRate = 3600000)
	@Transactional
	public void cleanupExpired() {
		storyRepository.deleteExpiredStories(LocalDateTime.now());
	}
}
