package com.yogesh.service;

import com.yogesh.dto.NotificationDTO;
import com.yogesh.model.Notification;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.NotificationRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final PostRepository postRepository;

	@Transactional
	public void create(Long toUserId, Long fromUserId, String type, Long postId, String commentText) {
		if (toUserId.equals(fromUserId)) {
			return;
		}

		Notification n = new Notification();
		n.setToUserId(toUserId);
		n.setFromUserId(fromUserId);
		n.setType(type);
		if (postId != null) {
			n.setPostId(postId);
		}
		if (commentText != null) {
			n.setCommentText(commentText);
		}
		n.setRead(false);
		n.setCreatedAt(LocalDateTime.now());
		notificationRepository.save(n);
	}

	public List<NotificationDTO> getForUser(Long userId) {
		List<Notification> notifications = notificationRepository.findByToUserIdOrderByCreatedAtDesc(userId);
		if (notifications.isEmpty()) {
			return List.of();
		}

		Set<Long> fromUserIds = notifications.stream()
				.map(Notification::getFromUserId)
				.filter(id -> id != null)
				.collect(Collectors.toSet());

		Map<Long, User> userMap = userRepository.findAllById(fromUserIds).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		Set<Long> postIds = notifications.stream()
				.map(Notification::getPostId)
				.filter(id -> id != null)
				.collect(Collectors.toSet());

		Map<Long, Post> postMap = postRepository.findAllById(postIds).stream()
				.collect(Collectors.toMap(Post::getId, Function.identity()));

		return notifications.stream()
				.map(n -> toDTO(n, userMap, postMap))
				.toList();
	}

	public long getUnreadCount(Long userId) {
		return notificationRepository.countByToUserIdAndReadFalse(userId);
	}

	@Transactional
	public void markRead(Long notifId, Long userId) {
		notificationRepository.findById(notifId).ifPresent(n -> {
			if (n.getToUserId().equals(userId)) {
				n.setRead(true);
				notificationRepository.save(n);
			}
		});
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.markAllReadByUserId(userId);
	}

	private NotificationDTO toDTO(Notification n, Map<Long, User> userMap, Map<Long, Post> postMap) {
		NotificationDTO dto = new NotificationDTO();
		dto.setId(n.getId());
		dto.setType(n.getType());
		dto.setPostId(n.getPostId());
		dto.setCommentText(n.getCommentText());
		dto.setRead(n.isRead());
		dto.setCreatedAt(n.getCreatedAt());

		User actor = n.getFromUserId() != null ? userMap.get(n.getFromUserId()) : null;
		if (actor != null) {
			dto.setActorId(actor.getId());
			dto.setActorName(actor.getName());
			dto.setActorAvatar(actor.getAvatar());
			dto.setFromUserName(actor.getName());
		} else {
			dto.setMessage(defaultMessage(n.getType(), n.getCommentText()));
		}

		Post post = n.getPostId() != null ? postMap.get(n.getPostId()) : null;
		if (post != null) {
			String thumb = post.getThumbnailUrl();
			if (thumb == null || thumb.isBlank()) {
				thumb = post.getVideoUrl();
			}
			dto.setThumbnail(thumb);
			dto.setPostThumbnail(thumb);
		}

		if (dto.getMessage() == null || dto.getMessage().isBlank()) {
			dto.setMessage(buildMessage(n.getType(), n.getCommentText()));
		}

		return dto;
	}

	private String buildMessage(String type, String commentText) {
		String normalized = type != null ? type.toUpperCase() : "";
		return switch (normalized) {
			case "LIKE" -> "liked your post";
			case "COMMENT" -> {
				if (commentText != null && !commentText.isBlank()) {
					yield "commented: " + commentText;
				}
				yield "commented on your post";
			}
			case "FOLLOW" -> "started following you";
			case "SHARE" -> "shared your post";
			case "BOOKMARK" -> "saved your post";
			case "MENTION" -> "mentioned you";
			default -> defaultMessage(type, commentText);
		};
	}

	private String defaultMessage(String type, String commentText) {
		if (commentText != null && !commentText.isBlank()) {
			return commentText;
		}
		return type != null && !type.isBlank() ? type + " notification" : "New activity";
	}
}
