package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.DirectMessageService;
import com.yogesh.service.ModerationService;
import com.yogesh.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageController {

	private final DirectMessageService dmService;
	private final UserRepository userRepository;
	private final ModerationService moderationService;
	private final MessageSource messageSource;

	@PostMapping("/send")
	public Map<String, Object> send(@RequestBody Map<String, Object> body, Authentication auth) {
		User me = getUser(auth);
		Long receiverId = Long.parseLong(body.get("receiverId").toString());
		String text = body.get("text") != null ? body.get("text").toString() : "";
		if (!text.isBlank()) {
			moderationService.assertAllowed(text, "Message");
		}
		if (!dmService.canChat(me.getId(), receiverId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					msg("dm.follow.required",
							"Sirf un logon ko message kar sakte ho jinhe tum follow karte ho ya jo tumhe follow karte hain"));
		}
		Map<String, Object> result = dmService.send(me.getId(), receiverId, text);
		result.put("isMine", true);
		return result;
	}

	@GetMapping("/can-chat/{userId}")
	public Map<String, Object> canChatCheck(@PathVariable Long userId, Authentication auth) {
		User me = getUser(auth);
		boolean allowed = dmService.canChat(me.getId(), userId);
		return Map.of("allowed", allowed, "message",
				allowed ? msg("dm.chat.allowed", "Chat allowed")
						: msg("dm.chat.notAllowed", "Pehle follow karo ya follow karne ka wait karo"));
	}

	@PostMapping("/send-image")
	public Map<String, Object> sendImage(@RequestParam("receiverId") Long receiverId,
			@RequestParam(value = "text", defaultValue = "") String text, @RequestParam("image") MultipartFile image,
			Authentication auth) throws Exception {
		User me = getUser(auth);
		if (!dmService.canChat(me.getId(), receiverId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg("dm.follow.first", "Follow karo pehle"));
		}

		String extension;
		try {
			extension = FileStorageUtil.validateAllowedUpload(image, FileStorageUtil.IMAGE_UPLOAD_EXTENSIONS);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		if (!text.isBlank()) {
			moderationService.assertAllowed(text, "Message");
		}

		String folder = FileStorageUtil.createDateFolder("uploads/dm/");
		String filename = FileStorageUtil.generateFileName(extension);
		Path path = Paths.get(folder + filename);
		image.transferTo(path);
		String imageUrl = "/" + path.toString().replace("\\", "/");

		Map<String, Object> result = dmService.sendWithImage(me.getId(), receiverId, text, imageUrl);
		result.put("isMine", true);
		return result;
	}

	@GetMapping("/conversation/{userId}")
	public List<Map<String, Object>> conversation(@PathVariable Long userId, Authentication auth) {
		User me = getUser(auth);
		List<Map<String, Object>> messages = dmService.getConversation(me.getId(), userId);
		messages.forEach(m -> m.put("isMine", me.getId().equals(m.get("senderId"))));
		return messages;
	}

	@GetMapping("/inbox")
	public List<Map<String, Object>> inbox(Authentication auth) {
		User me = getUser(auth);
		return dmService.getInbox(me.getId());
	}

	@GetMapping("/unread")
	public Map<String, Object> unread(Authentication auth) {
		User me = getUser(auth);
		return Map.of("count", dmService.getTotalUnread(me.getId()));
	}

	@PostMapping("/read/{userId}")
	public Map<String, Object> markAsRead(@PathVariable Long userId, Authentication auth) {
		User me = getUser(auth);
		dmService.markAsRead(me.getId(), userId);
		return Map.of("success", true);
	}

	@DeleteMapping("/delete/{userId}")
	public Map<String, String> deleteConversation(@PathVariable Long userId, Authentication auth) {
		User me = getUser(auth);
		dmService.deleteConversation(me.getId(), userId);
		return Map.of("status", "deleted");
	}

	private User getUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
