(function() {

	var activeChatUserId = null;
	var activeChatName = null;
	var myUserId = null;
	var pollTimer = null;
	var wsMessageSubscription = null;
	var wsTypingSubscription = null;
	var typingTimer = null;
	var pendingImage = null;
	var lastConversationSignature = "";

	var POLL_INTERVAL = 3000;
	var TYPING_IDLE_MS = 1200;

	var EMOJIS = ["😀", "😂", "❤️", "🔥", "👍", "😍", "🙌", "💯", "😭", "🎉", "💪", "✨", "😊", "🙏"];

	function csrf() {
		return document.querySelector('meta[name="_csrf"]')?.getAttribute("content") || "";
	}

	function csrfHeader() {
		return document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content") || "X-CSRF-TOKEN";
	}

	document.addEventListener("DOMContentLoaded", function() {
		fetch("/api/profile/me")
			.then(function(res) {
				return res.json();
			})
			.then(function(user) {
				myUserId = user.id || user.userId || null;
			})
			.catch(function() {
			});

		bindRealtimeEvents();
		loadInbox();
		setupInput();
		setupEmojiPicker();
		setupImageInput();
		setupInboxSearch();
		openChatFromUrl();
	});

	function bindRealtimeEvents() {
		window.addEventListener("realtime:connected", function() {
			if (!activeChatUserId) {
				return;
			}
			stopPolling();
			setupRealtimeSubscriptions();
			updateOnlineStatus(activeChatUserId);
		});

		window.addEventListener("realtime:presence", function(event) {
			var data = event.detail || {};
			if (activeChatUserId && String(activeChatUserId) === String(data.userId)) {
				updateOnlineStatus(activeChatUserId);
			}
		});
	}

	function setupInboxSearch() {
		var input = document.getElementById("dmSearchInput");
		if (!input) {
			return;
		}

		var debounceTimer = null;
		input.addEventListener("input", function(event) {
			clearTimeout(debounceTimer);
			var query = event.target.value.trim();
			if (!query) {
				loadInbox();
				return;
			}
			debounceTimer = setTimeout(function() {
				searchInbox(query);
			}, 300);
		});

		input.addEventListener("keydown", function(event) {
			if (event.key === "Escape") {
				input.value = "";
				loadInbox();
			}
		});
	}

	function openChatFromUrl() {
		var userId = new URLSearchParams(window.location.search).get("with");
		if (!userId) {
			return;
		}

		fetch("/api/profile/user/" + encodeURIComponent(userId))
			.then(function(res) {
				return res.json();
			})
			.then(function(user) {
				openChat(userId, user.name || "User", user.avatar || null);
			})
			.catch(function() {
			});
	}

	async function loadInbox() {
		var inbox = document.getElementById("inboxList");
		if (!inbox) {
			return;
		}

		try {
			var response = await fetch("/api/dm/inbox");
			var data = await response.json();

			inbox.innerHTML = "";
			if (!Array.isArray(data) || !data.length) {
				inbox.innerHTML = [
					'<div style="text-align:center;padding:50px 20px;color:#374151">',
					'<div style="font-size:2.5rem;margin-bottom:10px;opacity:.3">💬</div>',
					'<div style="font-size:13px">Koi message nahi abhi</div>',
					'<div style="font-size:11px;color:#1f2937;margin-top:4px">Search karke kisi ko message karo</div>',
					"</div>"
				].join("");
				return;
			}

			data.forEach(function(item) {
				inbox.appendChild(buildInboxItem(item));
			});

			refreshInboxSelection();
			updateInboxPresence(data.map(function(item) {
				return item.userId;
			}));
		} catch (error) {
			console.error("Inbox load failed:", error);
		}
	}

	function buildInboxItem(item) {
		var wrapper = document.createElement("div");
		wrapper.className = "inbox-item" + (String(item.userId) === String(activeChatUserId) ? " active" : "");
		wrapper.dataset.userId = item.userId;

		var avatarWrap = document.createElement("div");
		avatarWrap.className = "inbox-avatar";
		avatarWrap.appendChild(buildAvatar(item.avatar, 44));

		var onlineDot = document.createElement("span");
		onlineDot.className = "online-dot";
		onlineDot.dataset.onlineUserId = item.userId;
		onlineDot.style.background = "#6b7280";
		onlineDot.title = "Offline";
		avatarWrap.appendChild(onlineDot);

		var info = document.createElement("div");
		info.className = "inbox-info";

		var row = document.createElement("div");
		row.className = "inbox-row1";

		var name = document.createElement("div");
		name.className = "inbox-name";
		name.style.fontWeight = item.unreadCount > 0 ? "800" : "700";
		name.style.color = item.unreadCount > 0 ? "#f1f1f8" : "#d1d5db";
		name.textContent = item.name || "User";

		var time = document.createElement("div");
		time.className = "inbox-time";
		time.textContent = formatInboxTime(item.lastTime);

		row.appendChild(name);
		row.appendChild(time);

		var preview = document.createElement("div");
		preview.className = "inbox-preview" + (item.unreadCount > 0 ? " unread" : "");
		preview.textContent = item.lastMessage || "Image";

		info.appendChild(row);
		info.appendChild(preview);

		wrapper.appendChild(avatarWrap);
		wrapper.appendChild(info);

		if (item.unreadCount > 0) {
			var badge = document.createElement("span");
			badge.className = "unread-badge";
			badge.textContent = item.unreadCount > 9 ? "9+" : String(item.unreadCount);
			wrapper.appendChild(badge);
		}

		wrapper.addEventListener("click", function() {
			openChat(item.userId, item.name, item.avatar);
		});

		return wrapper;
	}

	async function updateInboxPresence(userIds) {
		if (!window.RealtimeService || !Array.isArray(userIds) || !userIds.length) {
			return;
		}

		try {
			var online = await window.RealtimeService.batchOnlineStatus(userIds);
			Object.keys(online || {}).forEach(function(userId) {
				var dot = document.querySelector('[data-online-user-id="' + userId + '"]');
				if (!dot) {
					return;
				}
				var isOnline = online[userId] === true;
				dot.style.background = isOnline ? "#22c55e" : "#6b7280";
				dot.title = isOnline ? "Online" : "Offline";
			});
		} catch (error) {
			console.error("Presence load failed:", error);
		}
	}

	async function openChat(userId, name, avatar) {
		try {
			var validationResponse = await fetch("/api/dm/can-chat/" + encodeURIComponent(userId));
			var validation = await validationResponse.json();
			if (!validation.allowed) {
				showToast(validation.message || "Chat allowed nahi hai", "error");
				return;
			}
		} catch (error) {
		}

		activeChatUserId = String(userId);
		activeChatName = name || "User";
		lastConversationSignature = "";
		hideTypingIndicator();
		stopPolling();
		clearRealtimeSubscriptions();

		var msgArea = document.getElementById("msgArea");
		if (msgArea) {
			msgArea.innerHTML = "";
		}

		refreshInboxSelection();
		showChatShell(userId, activeChatName, avatar);

		await fetchMessages(true);
		updateOnlineStatus(activeChatUserId);

		if (window.RealtimeService && window.RealtimeService.isConnected()) {
			setupRealtimeSubscriptions();
			return;
		}

		startPolling();
	}

	function showChatShell(userId, name, avatar) {
		var noChat = document.getElementById("noChatMsg");
		var header = document.getElementById("chatHead");
		var area = document.getElementById("msgArea");
		var inputWrap = document.getElementById("chatInputWrap");
		var panel = document.getElementById("chatPanel");
		var avatarEl = document.getElementById("chatHeadAvatar");
		var nameEl = document.getElementById("chatHeadName");
		var statusEl = document.getElementById("chatHeadStatus");
		var profileBtn = document.getElementById("profileBtn");

		if (noChat) {
			noChat.style.display = "none";
		}
		header?.classList.add("visible");
		area?.classList.add("visible");
		inputWrap?.classList.add("visible");
		panel?.classList.add("mobile-open");

		if (avatarEl) {
			avatarEl.innerHTML = "";
			avatarEl.appendChild(buildAvatar(avatar, 40));
		}
		if (nameEl) {
			nameEl.textContent = name;
		}
		if (statusEl) {
			statusEl.textContent = "Status load ho raha hai...";
			statusEl.style.color = "#6b7280";
		}
		if (profileBtn) {
			profileBtn.href = "/profile/user/" + userId;
		}

		document.getElementById("msgInput")?.focus();
	}

	function refreshInboxSelection() {
		document.querySelectorAll(".inbox-item").forEach(function(item) {
			item.classList.toggle("active", String(item.dataset.userId) === String(activeChatUserId));
		});
	}

	function startPolling() {
		stopPolling();
		pollTimer = setInterval(function() {
			if (!activeChatUserId) {
				return;
			}
			fetchMessages(false);
			updateOnlineStatus(activeChatUserId);
		}, POLL_INTERVAL);
	}

	function stopPolling() {
		if (pollTimer) {
			clearInterval(pollTimer);
			pollTimer = null;
		}
	}

	function clearRealtimeSubscriptions() {
		if (wsMessageSubscription && typeof wsMessageSubscription.unsubscribe === "function") {
			wsMessageSubscription.unsubscribe();
		}
		if (wsTypingSubscription && typeof wsTypingSubscription.unsubscribe === "function") {
			wsTypingSubscription.unsubscribe();
		}
		wsMessageSubscription = null;
		wsTypingSubscription = null;
	}

	function setupRealtimeSubscriptions() {
		if (!window.RealtimeService || !window.RealtimeService.isConnected()) {
			return;
		}

		clearRealtimeSubscriptions();

		wsMessageSubscription = window.RealtimeService.subscribeMessages(function(message) {
			if (!message) {
				return;
			}

			var belongsToActiveChat = activeChatUserId && (
				String(message.senderId) === String(activeChatUserId) ||
				String(message.receiverId) === String(activeChatUserId)
			);

			loadInbox();

			if (belongsToActiveChat) {
				hideTypingIndicator();
				fetchMessages(true);
			}
		});

		wsTypingSubscription = window.RealtimeService.subscribeTyping(function(data) {
			if (!data || !activeChatUserId) {
				return;
			}

			if (String(data.senderId) === String(activeChatUserId)) {
				showTypingIndicator(Boolean(data.typing));
			}
		});
	}

	async function fetchMessages(forceRender) {
		if (!activeChatUserId) {
			return;
		}

		try {
			var response = await fetch("/api/dm/conversation/" + encodeURIComponent(activeChatUserId));
			var messages = await response.json();
			var signature = buildConversationSignature(messages || []);

			if (!forceRender && signature === lastConversationSignature) {
				return;
			}

			lastConversationSignature = signature;
			renderMessages(messages || []);
		} catch (error) {
			console.error("Conversation load failed:", error);
		}
	}

	function buildConversationSignature(messages) {
		if (!messages.length) {
			return "empty";
		}

		var last = messages[messages.length - 1];
		return [
			messages.length,
			last.id || 0,
			last.read === true,
			last.createdAt || ""
		].join(":");
	}

	function renderMessages(messages) {
		var area = document.getElementById("msgArea");
		if (!area) {
			return;
		}

		var nearBottom = area.scrollHeight - area.scrollTop - area.clientHeight < 120;
		area.innerHTML = "";

		if (!messages.length) {
			area.innerHTML = '<div style="text-align:center;padding:60px 20px;color:#374151"><div style="font-size:2rem;opacity:.3;margin-bottom:8px">👋</div><div style="font-size:13px">Pehla message bhejo</div></div>';
			return;
		}

		var lastDate = null;
		messages.forEach(function(message) {
			var createdAt = message.createdAt ? new Date(message.createdAt) : null;
			var dateKey = createdAt ? createdAt.toDateString() : null;

			if (dateKey && dateKey !== lastDate) {
				var separator = document.createElement("div");
				separator.className = "date-sep";
				separator.textContent = humanDate(createdAt);
				area.appendChild(separator);
				lastDate = dateKey;
			}

			area.appendChild(buildMessageElement(message));
		});

		if (nearBottom || !lastDate) {
			area.scrollTop = area.scrollHeight;
		}
	}

	function buildMessageElement(message) {
		var isMine = message.isMine === true || String(message.senderId) === String(myUserId);

		var wrapper = document.createElement("div");
		wrapper.className = "msg-wrap " + (isMine ? "me" : "them");
		wrapper.dataset.msgId = message.id;

		var bubble = document.createElement("div");
		bubble.className = "msg-bubble";

		if (message.imageUrl) {
			var image = document.createElement("img");
			image.className = "msg-img";
			image.src = message.imageUrl;
			image.alt = "Message image";
			image.onerror = function() {
				image.style.display = "none";
			};
			bubble.appendChild(image);
		}

		if (message.text) {
			var text = document.createElement("div");
			text.className = "msg-text";
			text.textContent = message.text;
			bubble.appendChild(text);
		}

		var meta = document.createElement("div");
		meta.className = "msg-meta";

		var time = document.createElement("span");
		time.className = "msg-time";
		time.textContent = message.createdAt
			? new Date(message.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
			: "";
		meta.appendChild(time);

		if (isMine) {
			var tick = document.createElement("span");
			tick.className = "msg-tick " + (message.read ? "read" : "sent");
			tick.textContent = message.read ? "✓✓" : "✓";
			meta.appendChild(tick);
		}

		bubble.appendChild(meta);
		wrapper.appendChild(bubble);
		return wrapper;
	}

	async function sendMessage() {
		var input = document.getElementById("msgInput");
		var text = input?.value.trim() || "";
		if (!activeChatUserId || (!text && !pendingImage)) {
			return;
		}

		var area = document.getElementById("msgArea");
		var previewUrl = pendingImage ? URL.createObjectURL(pendingImage) : null;
		var tempMessage = buildMessageElement({
			id: "temp-" + Date.now(),
			text: text,
			imageUrl: previewUrl,
			senderId: myUserId,
			isMine: true,
			read: false,
			createdAt: new Date().toISOString()
		});
		tempMessage.style.opacity = "0.6";
		area?.appendChild(tempMessage);
		if (area) {
			area.scrollTop = area.scrollHeight;
		}

		var imageFile = pendingImage;
		input.value = "";
		input.style.height = "auto";
		updateCharCount(0);
		clearPendingImage();
		stopTypingSignal();

		try {
			if (imageFile) {
				var form = new FormData();
				form.append("receiverId", activeChatUserId);
				if (text) {
					form.append("text", text);
				}
				form.append("image", imageFile);

				var imageResponse = await fetch("/api/dm/send-image", {
					method: "POST",
					headers: buildCsrfHeaders(),
					body: form
				});

				if (!imageResponse.ok) {
					throw new Error(await imageResponse.text());
				}
			} else {
				var response = await fetch("/api/dm/send", {
					method: "POST",
					headers: Object.assign({
						"Content-Type": "application/json"
					}, buildCsrfHeaders()),
					body: JSON.stringify({
						receiverId: activeChatUserId,
						text: text
					})
				});

				if (!response.ok) {
					throw new Error(await response.text());
				}
			}

			tempMessage.remove();
			lastConversationSignature = "";
			await fetchMessages(true);
			loadInbox();
		} catch (error) {
			tempMessage.remove();
			if (previewUrl) {
				URL.revokeObjectURL(previewUrl);
			}
			if (input) {
				input.value = text;
			}
			showToast("Message send nahi hua", "error");
			console.error("Send failed:", error);
		}
	}

	async function deleteConversation() {
		if (!activeChatUserId || !confirm((activeChatName || "User") + " ke saath conversation delete karni hai?")) {
			return;
		}

		try {
			await fetch("/api/dm/delete/" + encodeURIComponent(activeChatUserId), {
				method: "DELETE",
				headers: buildCsrfHeaders()
			});

			activeChatUserId = null;
			activeChatName = null;
			lastConversationSignature = "";
			stopPolling();
			clearRealtimeSubscriptions();
			hideTypingIndicator();

			document.getElementById("noChatMsg").style.display = "";
			document.getElementById("chatHead")?.classList.remove("visible");
			document.getElementById("msgArea")?.classList.remove("visible");
			document.getElementById("chatInputWrap")?.classList.remove("visible");
			document.getElementById("chatPanel")?.classList.remove("mobile-open");

			var area = document.getElementById("msgArea");
			if (area) {
				area.innerHTML = "";
			}

			await loadInbox();
			showToast("Conversation delete ho gayi");
		} catch (error) {
			showToast("Conversation delete nahi hui", "error");
			console.error("Delete failed:", error);
		}
	}

	function setupInput() {
		var input = document.getElementById("msgInput");
		if (!input) {
			return;
		}

		input.addEventListener("keydown", function(event) {
			if (event.key === "Enter" && !event.shiftKey) {
				event.preventDefault();
				sendMessage();
			}
		});

		input.addEventListener("input", function() {
			input.style.height = "auto";
			input.style.height = Math.min(input.scrollHeight, 120) + "px";
			updateCharCount(input.value.length);
			signalTyping();
		});

		document.getElementById("sendBtn")?.addEventListener("click", sendMessage);
		document.getElementById("deleteConvoBtn")?.addEventListener("click", deleteConversation);
	}

	function updateCharCount(length) {
		var counter = document.getElementById("charCount");
		if (!counter) {
			return;
		}
		counter.textContent = length > 900 ? length + "/1000" : "";
		counter.style.color = length > 950 ? "#f43f5e" : "#6b7280";
	}

	function signalTyping() {
		if (!activeChatUserId || !window.RealtimeService || !window.RealtimeService.isConnected()) {
			return;
		}

		window.RealtimeService.sendTyping(activeChatUserId, true);
		clearTimeout(typingTimer);
		typingTimer = setTimeout(function() {
			stopTypingSignal();
		}, TYPING_IDLE_MS);
	}

	function stopTypingSignal() {
		clearTimeout(typingTimer);
		typingTimer = null;
		if (!activeChatUserId || !window.RealtimeService || !window.RealtimeService.isConnected()) {
			return;
		}
		window.RealtimeService.sendTyping(activeChatUserId, false);
	}

	function showTypingIndicator(isTyping) {
		var indicator = document.getElementById("typingIndicator");
		var nameEl = document.getElementById("typingName");
		if (!indicator || !nameEl) {
			return;
		}

		if (isTyping) {
			nameEl.textContent = (activeChatName || "User") + " typing...";
			indicator.classList.add("visible");
			return;
		}

		hideTypingIndicator();
	}

	function hideTypingIndicator() {
		var indicator = document.getElementById("typingIndicator");
		var nameEl = document.getElementById("typingName");
		indicator?.classList.remove("visible");
		if (nameEl) {
			nameEl.textContent = "";
		}
	}

	async function updateOnlineStatus(userId) {
		if (!userId) {
			return;
		}

		try {
			var response = await fetch("/api/presence/online/" + encodeURIComponent(userId));
			var data = await response.json();
			var status = document.getElementById("chatHeadStatus");
			if (!status) {
				return;
			}

			if (data.online) {
				status.textContent = "● Online";
				status.style.color = "#22c55e";
				return;
			}

			status.textContent = data.lastSeen ? "● " + formatLastSeen(data.lastSeen) : "● Offline";
			status.style.color = "#6b7280";
		} catch (error) {
			console.error("Status check failed:", error);
		}
	}

	function formatLastSeen(timestamp) {
		var diffMinutes = Math.floor((Date.now() - Number(timestamp)) / 60000);
		if (diffMinutes < 1) {
			return "Abhi abhi active tha";
		}
		if (diffMinutes < 60) {
			return diffMinutes + " min pehle active tha";
		}

		var diffHours = Math.floor(diffMinutes / 60);
		if (diffHours < 24) {
			return diffHours + " ghante pehle active tha";
		}

		return new Date(Number(timestamp)).toLocaleDateString("hi-IN", {
			day: "numeric",
			month: "short"
		});
	}

	function setupEmojiPicker() {
		var picker = document.getElementById("emojiPicker");
		var button = document.getElementById("emojiBtn");
		var input = document.getElementById("msgInput");

		if (!picker || !button || !input) {
			return;
		}

		EMOJIS.forEach(function(emoji) {
			var option = document.createElement("span");
			option.className = "emoji-opt";
			option.textContent = emoji;
			option.addEventListener("click", function() {
				var start = input.selectionStart || input.value.length;
				var end = input.selectionEnd || input.value.length;
				input.value = input.value.slice(0, start) + emoji + input.value.slice(end);
				input.focus();
				input.selectionStart = input.selectionEnd = start + emoji.length;
				updateCharCount(input.value.length);
			});
			picker.appendChild(option);
		});

		button.addEventListener("click", function(event) {
			event.preventDefault();
			event.stopPropagation();
			picker.classList.toggle("open");
		});

		document.addEventListener("click", function() {
			picker.classList.remove("open");
		});
	}

	function setupImageInput() {
		var imageButton = document.getElementById("imgBtn");
		var fileInput = document.getElementById("imgFileInput");
		var preview = document.getElementById("imgPreview");
		var previewThumb = document.getElementById("imgPreviewThumb");
		var previewName = document.getElementById("imgPreviewName");
		var removeButton = document.getElementById("imgPreviewRemove");

		imageButton?.addEventListener("click", function() {
			fileInput?.click();
		});

		fileInput?.addEventListener("change", function(event) {
			var file = event.target.files?.[0];
			if (!file) {
				return;
			}
			if (!["image/jpeg", "image/png"].includes(file.type)) {
				showToast("Sirf JPG/PNG images allowed hain", "error");
				fileInput.value = "";
				return;
			}
			if (file.size > 10 * 1024 * 1024) {
				showToast("Image 10MB se badi nahi ho sakti", "error");
				fileInput.value = "";
				return;
			}

			pendingImage = file;
			var url = URL.createObjectURL(file);
			if (previewThumb) {
				previewThumb.src = url;
			}
			if (previewName) {
				previewName.textContent = file.name;
			}
			preview?.classList.add("visible");
			fileInput.value = "";
		});

		removeButton?.addEventListener("click", clearPendingImage);
	}

	function clearPendingImage() {
		pendingImage = null;
		var preview = document.getElementById("imgPreview");
		var previewThumb = document.getElementById("imgPreviewThumb");
		var previewName = document.getElementById("imgPreviewName");

		preview?.classList.remove("visible");
		if (previewThumb) {
			if (previewThumb.src.startsWith("blob:")) {
				URL.revokeObjectURL(previewThumb.src);
			}
			previewThumb.src = "";
		}
		if (previewName) {
			previewName.textContent = "";
		}
	}

	async function searchInbox(query) {
		var inbox = document.getElementById("inboxList");
		if (!inbox) {
			return;
		}

		try {
			var response = await fetch("/api/search/users?q=" + encodeURIComponent(query));
			var users = await response.json();
			inbox.innerHTML = "";

			if (!Array.isArray(users) || !users.length) {
				inbox.innerHTML = '<div style="padding:16px;color:#4b5563;font-size:13px">Koi user nahi mila</div>';
				return;
			}

			users.forEach(function(user) {
				var item = document.createElement("div");
				item.className = "inbox-item";

				var avatarWrap = document.createElement("div");
				avatarWrap.className = "inbox-avatar";
				avatarWrap.appendChild(buildAvatar(user.avatar, 40));

				var info = document.createElement("div");
				info.className = "inbox-info";

				var name = document.createElement("div");
				name.className = "inbox-name";
				name.textContent = user.name || "User";

				info.appendChild(name);
				item.appendChild(avatarWrap);
				item.appendChild(info);

				item.addEventListener("click", function() {
					document.getElementById("dmSearchInput").value = "";
					openChat(user.id, user.name, user.avatar);
					loadInbox();
				});

				inbox.appendChild(item);
			});
		} catch (error) {
			console.error("Search failed:", error);
		}
	}

	function buildAvatar(src, size) {
		var wrapper = document.createElement("div");
		wrapper.className = "av-fallback";
		wrapper.style.cssText = [
			"width:" + size + "px",
			"height:" + size + "px",
			"border-radius:50%",
			"background:#1a1a2e",
			"display:flex",
			"align-items:center",
			"justify-content:center",
			"overflow:hidden",
			"flex-shrink:0",
			"font-size:1.2rem"
		].join(";");

		if (src) {
			var image = document.createElement("img");
			image.src = src;
			image.alt = "Avatar";
			image.style.cssText = "width:100%;height:100%;object-fit:cover;border-radius:50%;";
			image.onerror = function() {
				image.style.display = "none";
				wrapper.textContent = "👤";
			};
			wrapper.appendChild(image);
		} else {
			wrapper.textContent = "👤";
		}

		return wrapper;
	}

	function humanDate(date) {
		var today = new Date().toDateString();
		var yesterday = new Date(Date.now() - 86400000).toDateString();
		var value = date.toDateString();
		if (value === today) {
			return "Aaj";
		}
		if (value === yesterday) {
			return "Kal";
		}
		return date.toLocaleDateString("hi-IN", {
			day: "numeric",
			month: "short"
		});
	}

	function formatInboxTime(dateStr) {
		if (!dateStr) {
			return "";
		}

		var date = new Date(dateStr);
		var diffMinutes = Math.floor((Date.now() - date) / 60000);
		if (diffMinutes < 1) {
			return "abhi";
		}
		if (diffMinutes < 60) {
			return diffMinutes + "m";
		}
		if (diffMinutes < 1440) {
			return Math.floor(diffMinutes / 60) + "h";
		}
		return date.toLocaleDateString("hi-IN", {
			day: "numeric",
			month: "short"
		});
	}

	function buildCsrfHeaders() {
		var token = csrf();
		if (!token) {
			return {};
		}

		var headers = {};
		headers[csrfHeader()] = token;
		return headers;
	}

	function showToast(message, type) {
		document.getElementById("__dm_toast")?.remove();

		var toast = document.createElement("div");
		toast.id = "__dm_toast";
		toast.textContent = message;
		toast.style.cssText = [
			"position:fixed",
			"bottom:80px",
			"left:50%",
			"transform:translateX(-50%)",
			"background:" + (type === "error" ? "#f43f5e" : "#10b981"),
			"color:#fff",
			"padding:9px 22px",
			"border-radius:20px",
			"font-size:13px",
			"font-weight:600",
			"z-index:9999",
			"pointer-events:none"
		].join(";");

		document.body.appendChild(toast);
		setTimeout(function() {
			toast.remove();
		}, 3000);
	}

	window.openChat = openChat;
	window.sendMessage = sendMessage;
	window.deleteConversation = deleteConversation;

})();
