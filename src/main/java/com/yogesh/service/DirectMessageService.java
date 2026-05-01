package com.yogesh.service;

import com.yogesh.model.DirectMessage;
import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.DirectMessageRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

	private final DirectMessageRepository dmRepository;
	private final UserRepository userRepository;
	private final BlockRepository blockRepository;
	private final FollowRepository followRepository;
	private final MessageSource messageSource;

	@Transactional
	public Map<String, Object> send(Long senderId, Long receiverId, String text) {
		validateConversationAllowed(senderId, receiverId);

		if (text == null || text.isBlank())
			throw new IllegalArgumentException(msg("dm.message.empty", "Message empty nahi ho sakta"));
		if (text.length() > 1000)
			throw new IllegalArgumentException(msg("dm.message.tooLong", "Message 1000 characters se zyada nahi ho sakta"));

		DirectMessage msg = new DirectMessage();
		msg.setSenderId(senderId);
		msg.setReceiverId(receiverId);
		msg.setText(text.trim());
		DirectMessage saved = dmRepository.save(msg);

		return toMap(saved, null);
	}

	@Transactional
	public Map<String, Object> sendWithImage(Long senderId, Long receiverId, String text, String imageUrl) {
		validateConversationAllowed(senderId, receiverId);

		DirectMessage msg = new DirectMessage();
		msg.setSenderId(senderId);
		msg.setReceiverId(receiverId);
		msg.setText((text == null || text.isBlank()) ? "" : text.trim());
		msg.setImageUrl(imageUrl);
		DirectMessage saved = dmRepository.save(msg);

		Map<String, Object> result = new HashMap<>();
		result.put("id", saved.getId());
		result.put("text", saved.getText());
		result.put("imageUrl", saved.getImageUrl());
		result.put("senderId", senderId);
		result.put("receiverId", receiverId);
		result.put("createdAt", saved.getCreatedAt());
		result.put("read", false);
		return result;
	}

	public boolean canChat(Long myId, Long otherId) {
		if (myId == null || otherId == null || myId.equals(otherId))
			return false;
		boolean iFollow = followRepository.existsByFollowerIdAndFollowingId(myId, otherId);
		boolean theyFollow = followRepository.existsByFollowerIdAndFollowingId(otherId, myId);
		return iFollow || theyFollow;
	}

	public List<Map<String, Object>> getConversation(Long myId, Long otherId) {
		dmRepository.markConversationRead(otherId, myId);

		List<DirectMessage> messages = dmRepository.findConversation(myId, otherId);
		Map<Long, User> userMap = userRepository.findAllById(List.of(myId, otherId)).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		return messages.stream().map(m -> toMap(m, userMap)).collect(Collectors.toList());
	}

	public List<Map<String, Object>> getInbox(Long userId) {
		List<Long> partnerIds = dmRepository.findConversationPartnerIds(userId);
		if (partnerIds.isEmpty())
			return List.of();

		Map<Long, User> userMap = userRepository.findAllById(partnerIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		List<DirectMessage> latestMsgs = dmRepository.findLatestMessagePerPartner(userId);
		Map<Long, DirectMessage> latestByPartner = new HashMap<>();
		for (DirectMessage m : latestMsgs) {
			Long partnerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
			latestByPartner.put(partnerId, m);
		}

		List<Map<String, Object>> inbox = new ArrayList<>();
		for (Long partnerId : partnerIds) {
			User partner = userMap.get(partnerId);
			if (partner == null)
				continue;

			DirectMessage latest = latestByPartner.get(partnerId);
			String lastText = latest != null ? latest.getText() : "";
			if (lastText.length() > 50)
				lastText = lastText.substring(0, 50) + "...";
			String lastTime = latest != null ? latest.getCreatedAt().toString() : "";

			long unread = dmRepository.countBySenderIdAndReceiverIdAndReadFalse(partnerId, userId);

			Map<String, Object> item = new LinkedHashMap<>();
			item.put("userId", partnerId);
			item.put("name", partner.getName());
			item.put("avatar", partner.getAvatar());
			item.put("lastMessage", lastText);
			item.put("lastTime", lastTime);
			item.put("unreadCount", unread);
			item.put("unread", unread);
			inbox.add(item);
		}

		inbox.sort((a, b) -> {
			long unreadA = (long) a.get("unreadCount");
			long unreadB = (long) b.get("unreadCount");
			if (unreadA != unreadB)
				return Long.compare(unreadB, unreadA);
			return String.valueOf(b.get("lastTime")).compareTo(String.valueOf(a.get("lastTime")));
		});
		return inbox;
	}

	public long getTotalUnread(Long userId) {
		return dmRepository.countTotalUnreadForUser(userId);
	}

	@Transactional
	public void markAsRead(Long readerId, Long senderId) {
		dmRepository.markConversationRead(senderId, readerId);
	}

	@Transactional
	public void deleteConversation(Long myId, Long otherId) {
		dmRepository.deleteConversation(myId, otherId);
	}

	private void validateConversationAllowed(Long senderId, Long receiverId) {
		if (senderId == null || receiverId == null)
			throw new IllegalArgumentException(msg("dm.participants.invalid", "Invalid conversation participants"));
		if (senderId.equals(receiverId))
			throw new IllegalArgumentException(msg("dm.self", "Apne aap ko message nahi kar sakte"));
		if (!userRepository.existsById(receiverId))
			throw new IllegalArgumentException(msg("dm.receiver.notFound", "Receiver nahi mila"));
		if (!canChat(senderId, receiverId))
			throw new IllegalArgumentException(msg("dm.follow.required",
					"Sirf un logon ko message kar sakte ho jinhe tum follow karte ho ya jo tumhe follow karte hain"));
		if (blockRepository.existsByBlockerIdAndBlockedId(receiverId, senderId))
			throw new IllegalArgumentException(msg("dm.blocked.byOther", "Is user ne aapko block kiya hua hai"));
		if (blockRepository.existsByBlockerIdAndBlockedId(senderId, receiverId))
			throw new IllegalArgumentException(msg("dm.blocked.byYou", "Aapne is user ko block kiya hua hai"));
	}

	private Map<String, Object> toMap(DirectMessage m, Map<Long, User> userMap) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", m.getId());
		map.put("senderId", m.getSenderId());
		map.put("receiverId", m.getReceiverId());
		map.put("text", m.getText());
		map.put("imageUrl", m.getImageUrl());
		map.put("read", m.isRead());
		map.put("createdAt", m.getCreatedAt());
		map.put("isMine", false);

		if (userMap != null) {
			User sender = userMap.get(m.getSenderId());
			map.put("senderName", sender != null ? sender.getName() : "User");
			map.put("senderAvatar", sender != null ? sender.getAvatar() : null);
		}
		return map;
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
