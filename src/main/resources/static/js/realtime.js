(function() {

	const RT = window.RealtimeService = {};

	let stompClient = null;
	let connected = false;
	let reconnectTimer = null;
	let heartbeatTimer = null;
	let currentUsername = null;
	let subscriptions = {};

	const RECONNECT_DELAY = 3000;
	const HEARTBEAT_INTERVAL = 120000;

	RT.init = function(username) {
		if (!username) return;
		currentUsername = username;
		loadSockJS(function() {
			connect();
		});
	};

	function loadSockJS(cb) {
		if (window.SockJS) {
			loadStomp(cb);
			return;
		}
		var script = document.createElement('script');
		script.src = 'https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js';
		script.onload = function() {
			loadStomp(cb);
		};
		document.head.appendChild(script);
	}

	function loadStomp(cb) {
		if (window.Stomp) {
			cb();
			return;
		}
		var script = document.createElement('script');
		script.src = 'https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js';
		script.onload = cb;
		document.head.appendChild(script);
	}

	function connect() {
		try {
			var socket = new SockJS('/ws');
			stompClient = Stomp.over(socket);
			stompClient.debug = null;

			stompClient.connect({}, function() {
				connected = true;
				clearTimeout(reconnectTimer);

				subscribePresence();
				subscribeNotifications();
				startHeartbeat();

				window.dispatchEvent(new CustomEvent('realtime:connected'));
			}, function() {
				connected = false;
				scheduleReconnect();
			});
		} catch (e) {
			scheduleReconnect();
		}
	}

	function scheduleReconnect() {
		clearTimeout(reconnectTimer);
		reconnectTimer = setTimeout(function() {
			if (!connected) connect();
		}, RECONNECT_DELAY);
	}

	function subscribePresence() {
		if (!stompClient || subscriptions.presence) return;
		subscriptions.presence = stompClient.subscribe('/topic/presence', function(msg) {
			try {
				RT.onPresenceUpdate(JSON.parse(msg.body));
			} catch (e) {
			}
		});
	}

	function subscribeNotifications() {
		if (!stompClient || !connected || subscriptions.notifications) return;
		subscriptions.notifications = stompClient.subscribe('/user/queue/notifications', function(msg) {
			try {
				RT.onNotification(JSON.parse(msg.body));
			} catch (e) {
			}
		});
	}

	RT.subscribeMessages = function(userIdOrCallback, maybeCallback) {
		if (!stompClient || !connected) return null;
		var callback = typeof userIdOrCallback === 'function' ? userIdOrCallback : maybeCallback;
		if (typeof callback !== 'function') return null;
		if (subscriptions.messages) subscriptions.messages.unsubscribe();
		subscriptions.messages = stompClient.subscribe('/user/queue/messages', function(msg) {
			try {
				callback(JSON.parse(msg.body));
			} catch (e) {
			}
		});
		return subscriptions.messages;
	};

	RT.subscribeTyping = function(userIdOrCallback, maybeCallback) {
		if (!stompClient || !connected) return null;
		var callback = typeof userIdOrCallback === 'function' ? userIdOrCallback : maybeCallback;
		if (typeof callback !== 'function') return null;
		if (subscriptions.typing) subscriptions.typing.unsubscribe();
		subscriptions.typing = stompClient.subscribe('/user/queue/typing', function(msg) {
			try {
				callback(JSON.parse(msg.body));
			} catch (e) {
			}
		});
		return subscriptions.typing;
	};

	RT.sendMessage = function(senderId, receiverId, text) {
		if (!stompClient || !connected) return false;
		if (text === undefined) {
			text = receiverId;
			receiverId = senderId;
		}
		stompClient.send('/app/chat.send', {}, JSON.stringify({
			receiverId: receiverId,
			text: text
		}));
		return true;
	};

	RT.sendTyping = function(senderId, receiverId, isTyping) {
		if (!stompClient || !connected) return false;
		if (isTyping === undefined) {
			isTyping = receiverId;
			receiverId = senderId;
		}
		stompClient.send('/app/chat.typing', {}, JSON.stringify({
			receiverId: receiverId,
			typing: isTyping
		}));
		return true;
	};

	RT.isOnline = async function(userId) {
		try {
			var res = await fetch('/api/presence/online/' + encodeURIComponent(userId));
			var data = await res.json();
			return data.online === true;
		} catch (e) {
			return false;
		}
	};

	RT.batchOnlineStatus = async function(userIds) {
		try {
			var res = await fetch('/api/presence/online/batch', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ userIds: userIds })
			});
			var data = await res.json();
			return data.online || {};
		} catch (e) {
			return {};
		}
	};

	function startHeartbeat() {
		clearInterval(heartbeatTimer);
		heartbeatTimer = setInterval(function() {
			if (stompClient && connected) {
				stompClient.send('/app/chat.heartbeat', {}, '{}');
			}
		}, HEARTBEAT_INTERVAL);
	}

	RT.renderOnlineDot = function(isOnline) {
		return isOnline
			? '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#22c55e;margin-left:5px;flex-shrink:0" title="Online"></span>'
			: '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#6b7280;margin-left:5px;flex-shrink:0" title="Offline"></span>';
	};

	RT.formatLastSeen = function(ts) {
		if (!ts || ts === 0) return '';
		var diff = Date.now() - ts;
		var min = Math.floor(diff / 60000);
		if (min < 1) return 'Abhi abhi online tha';
		if (min < 60) return min + ' min pehle';
		var hr = Math.floor(min / 60);
		if (hr < 24) return hr + ' ghante pehle';
		return Math.floor(hr / 24) + ' din pehle';
	};

	RT.onPresenceUpdate = function(data) {
		var dots = document.querySelectorAll('[data-online-user-id="' + data.userId + '"]');
		dots.forEach(function(el) {
			el.style.background = data.status === 'online' ? '#22c55e' : '#6b7280';
			el.title = data.status === 'online' ? 'Online' : RT.formatLastSeen(data.lastSeen);
		});
		window.dispatchEvent(new CustomEvent('realtime:presence', { detail: data }));
	};

	RT.onNotification = function(data) {
		var badge = document.getElementById('notifBadge');
		if (badge) {
			var count = parseInt(badge.textContent || '0', 10) + 1;
			badge.textContent = count;
			badge.style.display = 'block';
		}
		showNotifToast(data);
		window.dispatchEvent(new CustomEvent('realtime:notification', { detail: data }));
	};

	function showNotifToast(data) {
		var existing = document.getElementById('rt-notif-toast');
		if (existing) existing.remove();

		var toast = document.createElement('div');
		toast.id = 'rt-notif-toast';
		toast.style.cssText = [
			'position:fixed;top:70px;right:16px;',
			'background:#0d0d1a;border:1px solid rgba(99,102,241,0.3);',
			'border-radius:14px;padding:12px 16px;z-index:9999;',
			'box-shadow:0 8px 30px rgba(0,0,0,0.5);',
			'display:flex;align-items:center;gap:10px;',
			'max-width:300px;cursor:pointer;',
			'animation:slideIn .3s ease;'
		].join('');

		var icon = data.type === 'LIKE' ? '❤' : data.type === 'COMMENT' ? '💬' :
			data.type === 'FOLLOW' ? '👤' : data.type === 'MESSAGE' ? '✉️' : '🔔';

		toast.innerHTML = '<div style="font-size:1.2rem">' + icon + '</div>' +
			'<div><div style="font-size:.82rem;font-weight:700;color:#f1f1f8">' + (data.title || 'Notification') + '</div>' +
			'<div style="font-size:.75rem;color:#6b7280">' + (data.body || '') + '</div></div>';

		toast.onclick = function() {
			if (data.url) window.location.href = data.url;
			toast.remove();
		};

		if (!document.getElementById('rt-style')) {
			var style = document.createElement('style');
			style.id = 'rt-style';
			style.textContent = '@keyframes slideIn{from{opacity:0;transform:translateX(30px)}to{opacity:1;transform:translateX(0)}}';
			document.head.appendChild(style);
		}

		document.body.appendChild(toast);
		setTimeout(function() {
			if (toast.parentNode) toast.remove();
		}, 5000);
	}

	RT.isConnected = function() {
		return connected;
	};

	RT.disconnect = function() {
		clearInterval(heartbeatTimer);
		Object.values(subscriptions).forEach(function(sub) {
			if (sub && typeof sub.unsubscribe === 'function') sub.unsubscribe();
		});
		subscriptions = {};
		if (stompClient) stompClient.disconnect();
		connected = false;
	};

	document.addEventListener('DOMContentLoaded', function() {
		var userMeta = document.querySelector('meta[name="current-username"]');
		if (userMeta) {
			RT.init(userMeta.getAttribute('content'));
		}
	});

})();
