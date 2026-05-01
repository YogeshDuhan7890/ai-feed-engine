/* =============================================
   PUSH-NOTIFICATIONS.JS — UPGRADED
   ✅ Service worker register + subscribe
   ✅ In-app permission prompt (custom UI)
   ✅ Auto-subscribe if already granted
   ✅ Unsubscribe support
   ✅ Header button sync
   ✅ Permission denied graceful handling
   ✅ Notification preferences save
============================================= */

(function() {

	function csrf() { return document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '' }
	function csrfH() { return document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN' }

	var SW_PATH = '/sw.js';
	var _swReg = null;

	/* ── Init on page load ── */
	document.addEventListener('DOMContentLoaded', function() {
		if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
			hidePushBtn();
			return;
		}

		// Register service worker
		navigator.serviceWorker.register(SW_PATH).then(function(reg) {
			_swReg = reg;
			return reg.pushManager.getSubscription();
		}).then(function(existing) {
			if (existing) {
				// Already subscribed
				setPushBtnState('on');
			} else if (Notification.permission === 'granted') {
				// Permission granted but no subscription — re-subscribe
				subscribePush();
			} else if (Notification.permission === 'default') {
				// Show button to prompt
				setPushBtnState('off');
			} else {
				// Denied
				hidePushBtn();
			}
		}).catch(function(e) {
			console.log('SW register failed:', e.message);
			hidePushBtn();
		});

		// Header button click
		document.getElementById('enablePushBtn')?.addEventListener('click', function() {
			if (Notification.permission === 'granted') {
				subscribePush();
			} else {
				showPermissionPrompt();
			}
		});
	});

	/* ── Show custom in-app permission prompt ── */
	function showPermissionPrompt() {
		// Remove existing
		document.getElementById('pushPrompt')?.remove();

		var prompt = document.createElement('div');
		prompt.id = 'pushPrompt';
		prompt.style.cssText = [
			'position:fixed;bottom:80px;left:50%;transform:translateX(-50%) translateY(20px);',
			'width:min(360px,90vw);',
			'background:#0d0d1a;border:1px solid rgba(99,102,241,0.3);',
			'border-radius:18px;padding:20px;z-index:1000;',
			'box-shadow:0 8px 40px rgba(0,0,0,0.6),0 0 0 1px rgba(99,102,241,0.1);',
			'animation:promptIn .35s cubic-bezier(0.34,1.56,0.64,1);',
			'opacity:0;'
		].join('');

		prompt.innerHTML = [
			'<style>',
			'@keyframes promptIn{from{opacity:0;transform:translateX(-50%) translateY(30px)}to{opacity:1;transform:translateX(-50%) translateY(0)}}',
			'</style>',
			'<div style="display:flex;gap:14px;align-items:flex-start">',
			'  <div style="width:44px;height:44px;border-radius:14px;background:rgba(99,102,241,0.15);',
			'    border:1px solid rgba(99,102,241,0.25);display:flex;align-items:center;justify-content:center;',
			'    font-size:1.3rem;flex-shrink:0">🔔</div>',
			'  <div style="flex:1">',
			'    <div style="font-size:.9rem;font-weight:700;color:#f1f1f8;margin-bottom:4px">',
			'      Notifications enable karo',
			'    </div>',
			'    <div style="font-size:.78rem;color:#6b7280;line-height:1.5">',
			'      Likes, comments aur messages ke liye real-time alerts paao',
			'    </div>',
			'  </div>',
			'</div>',
			'<div style="display:flex;gap:8px;margin-top:14px">',
			'  <button id="pushPromptAllow" style="flex:1;padding:10px;',
			'    background:linear-gradient(135deg,#6366f1,#8b5cf6);border:none;color:#fff;',
			'    border-radius:12px;font-size:.82rem;font-weight:700;cursor:pointer;font-family:inherit;">',
			'    🔔 Allow',
			'  </button>',
			'  <button id="pushPromptDeny" style="padding:10px 16px;',
			'    background:rgba(255,255,255,0.06);border:1px solid rgba(255,255,255,0.1);',
			'    color:#9ca3af;border-radius:12px;font-size:.82rem;cursor:pointer;font-family:inherit;">',
			'    Baad mein',
			'  </button>',
			'</div>'
		].join('');

		document.body.appendChild(prompt);

		// Animate in
		requestAnimationFrame(function() {
			prompt.style.opacity = '1';
			prompt.style.transform = 'translateX(-50%) translateY(0)';
		});

		document.getElementById('pushPromptAllow').addEventListener('click', function() {
			prompt.remove();
			requestPushPermission();
		});

		document.getElementById('pushPromptDeny').addEventListener('click', function() {
			dismissPrompt(prompt);
		});

		// Auto dismiss after 8s
		setTimeout(function() {
			if (document.getElementById('pushPrompt')) dismissPrompt(prompt);
		}, 8000);
	}

	function dismissPrompt(el) {
		if (!el) return;
		el.style.transition = 'opacity .25s, transform .25s';
		el.style.opacity = '0';
		el.style.transform = 'translateX(-50%) translateY(20px)';
		setTimeout(function() { el.remove(); }, 250);
	}

	/* ── Request browser permission ── */
	async function requestPushPermission() {
		var perm = await Notification.requestPermission();
		if (perm !== 'granted') {
			showPushToast('Notifications blocked. Browser settings se enable karo.', 'warn');
			hidePushBtn();
			return;
		}
		await subscribePush();
	}

	/* ── Subscribe ── */
	async function subscribePush() {
		try {
			var reg = _swReg || await navigator.serviceWorker.ready;

			// Get VAPID key from server
			var keyRes = await fetch('/api/push/vapid-key');
			if (!keyRes.ok) { console.warn('VAPID key fetch failed'); return; }
			var keyData = await keyRes.json();
			if (!keyData.publicKey) { console.warn('No VAPID key configured'); return; }

			// Check existing sub
			var existing = await reg.pushManager.getSubscription();
			if (existing) { setPushBtnState('on'); return; }

			// Subscribe
			var sub = await reg.pushManager.subscribe({
				userVisibleOnly: true,
				applicationServerKey: urlBase64ToUint8Array(keyData.publicKey)
			});

			// Save to server
			var subJson = sub.toJSON();
			await fetch('/api/push/subscribe', {
				method: 'POST',
				headers: { [csrfH()]: csrf(), 'Content-Type': 'application/json' },
				body: JSON.stringify({ endpoint: subJson.endpoint, keys: subJson.keys })
			});

			setPushBtnState('on');
			showPushToast('Notifications ON! 🔔 Ab real-time alerts milenge.');

		} catch (e) {
			console.error('Push subscribe error:', e);
			if (e.name === 'NotAllowedError') {
				showPushToast('Permission denied. Browser settings check karo.', 'warn');
			}
		}
	}

	/* ── Unsubscribe ── */
	async function unsubscribePush() {
		try {
			var reg = _swReg || await navigator.serviceWorker.ready;
			var sub = await reg.pushManager.getSubscription();
			if (!sub) return;

			await fetch('/api/push/unsubscribe', {
				method: 'POST',
				headers: { [csrfH()]: csrf(), 'Content-Type': 'application/json' },
				body: JSON.stringify({ endpoint: sub.endpoint })
			});

			await sub.unsubscribe();
			setPushBtnState('off');
			showPushToast('Notifications OFF ho gayi.', 'warn');

		} catch (e) {
			console.error('Unsubscribe error:', e);
		}
	}

	/* ── Header button state ── */
	function setPushBtnState(state) {
		var btn = document.getElementById('enablePushBtn');
		if (!btn) return;
		btn.style.display = 'flex';
		if (state === 'on') {
			btn.title = 'Notifications ON — click to turn off';
			btn.style.color = '#818cf8';
			btn.onclick = function() { unsubscribePush(); };
		} else {
			btn.title = 'Notifications enable karo';
			btn.style.color = '';
			btn.onclick = function() { showPermissionPrompt(); };
		}
	}

	function hidePushBtn() {
		var btn = document.getElementById('enablePushBtn');
		if (btn) btn.style.display = 'none';
	}

	/* ── Base64 helper ── */
	function urlBase64ToUint8Array(base64String) {
		var padding = '='.repeat((4 - base64String.length % 4) % 4);
		var base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
		var raw = window.atob(base64);
		var output = new Uint8Array(raw.length);
		for (var i = 0; i < raw.length; ++i) output[i] = raw.charCodeAt(i);
		return output;
	}

	/* ── Toast ── */
	function showPushToast(msg, type) {
		document.getElementById('push-toast')?.remove();
		var t = document.createElement('div');
		t.id = 'push-toast';
		var bg = type === 'warn' ? '#f59e0b' : '#10b981';
		t.style.cssText = [
			'position:fixed;top:70px;right:16px;',
			'background:' + bg + ';color:#fff;',
			'padding:10px 18px;border-radius:12px;font-size:.82rem;font-weight:600;',
			'z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,0.4);',
			'animation:promptIn .3s ease;max-width:280px;line-height:1.4;'
		].join('');
		t.textContent = msg;
		document.body.appendChild(t);
		setTimeout(function() { t.remove(); }, 4000);
	}

	// Expose for manual use
	window.requestPushPermission = requestPushPermission;
	window.unsubscribePush = unsubscribePush;

})();