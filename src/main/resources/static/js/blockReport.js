/* =============================================
   BLOCK-REPORT.JS — AI Feed
   ✅ Block / Unblock user
   ✅ Report user with reason
   ✅ Report post with reason
   ✅ 3-dot menu on profile + reels
   ✅ Block list page support
   ✅ Confirmation dialogs
   ✅ Toast feedback
============================================= */

(function() {

	function csrf() { return document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || ''; }
	function csrfH() { return document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN'; }

	var REPORT_REASONS = [
		{ value: 'SPAM', label: '🚫 Spam' },
		{ value: 'HATE', label: '🤬 Hate Speech' },
		{ value: 'VIOLENCE', label: '⚠️ Violence' },
		{ value: 'NUDITY', label: '🔞 Nudity' },
		{ value: 'HARASSMENT', label: '😡 Harassment' },
		{ value: 'MISINFORMATION', label: '❌ Misinformation' },
		{ value: 'FAKE_ACCOUNT', label: '🎭 Fake Account' },
		{ value: 'OTHER', label: '📝 Other' }
	];

	/* ── Block User ─────────────────────────────────────────────────────────── */
	async function blockUser(userId, userName, onSuccess) {
		if (!confirm('"' + (userName || 'User') + '" ko block karna chahte ho?\n\nBlock karne se:\n• Woh tumhari profile nahi dekh sakta\n• Tumhara content nahi dekh sakta\n• Follow remove ho jayega')) return;

		try {
			var res = await fetch('/api/block/' + userId, {
				method: 'POST',
				headers: { [csrfH()]: csrf() }
			});
			var data = await res.json();
			showToast((userName || 'User') + ' block ho gaya 🚫');
			if (onSuccess) onSuccess(true);
			// Refresh page if on profile
			if (location.pathname.includes('/profile/user/' + userId)) {
				setTimeout(function() { location.reload(); }, 1000);
			}
		} catch (e) {
			showToast('Block fail hua. Retry karo.', 'error');
		}
	}

	/* ── Unblock User ───────────────────────────────────────────────────────── */
	async function unblockUser(userId, userName, onSuccess) {
		try {
			var res = await fetch('/api/block/unblock/' + userId, {
				method: 'POST',
				headers: { [csrfH()]: csrf() }
			});
			var data = await res.json();
			showToast((userName || 'User') + ' unblock ho gaya ✅');
			if (onSuccess) onSuccess(false);
		} catch (e) {
			showToast('Unblock fail hua.', 'error');
		}
	}

	/* ── Report Modal ───────────────────────────────────────────────────────── */
	function showReportModal(targetType, targetId, targetName) {
		document.getElementById('brReportModal')?.remove();

		var modal = document.createElement('div');
		modal.id = 'brReportModal';
		modal.style.cssText = 'position:fixed;inset:0;z-index:1000;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.85);backdrop-filter:blur(4px);';

		var box = document.createElement('div');
		box.style.cssText = 'background:#0d0d1a;border:1px solid rgba(255,255,255,.1);border-radius:20px;padding:24px;width:min(380px,92vw);max-height:90vh;overflow-y:auto;';

		var title = document.createElement('div');
		title.style.cssText = 'font-size:.95rem;font-weight:700;color:#f1f1f8;margin-bottom:4px;';
		title.textContent = 'Report ' + (targetType === 'USER' ? 'User' : 'Post');

		var subtitle = document.createElement('div');
		subtitle.style.cssText = 'font-size:.78rem;color:#6b7280;margin-bottom:18px;';
		subtitle.textContent = targetName ? '"' + targetName + '"' : '';

		// Reason grid
		var grid = document.createElement('div');
		grid.style.cssText = 'display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:16px;';

		var selectedReason = null;

		REPORT_REASONS.forEach(function(r) {
			var btn = document.createElement('button');
			btn.style.cssText = 'background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.1);color:#d1d5db;padding:10px 8px;border-radius:12px;cursor:pointer;font-size:.78rem;font-family:inherit;text-align:left;transition:all .15s;';
			btn.textContent = r.label;
			btn.dataset.value = r.value;
			btn.addEventListener('click', function() {
				grid.querySelectorAll('button').forEach(function(b) {
					b.style.background = 'rgba(255,255,255,.05)';
					b.style.borderColor = 'rgba(255,255,255,.1)';
					b.style.color = '#d1d5db';
				});
				btn.style.background = 'rgba(99,102,241,.15)';
				btn.style.borderColor = 'rgba(99,102,241,.4)';
				btn.style.color = '#a5b4fc';
				selectedReason = r.value;
			});
			grid.appendChild(btn);
		});

		// Description
		var desc = document.createElement('textarea');
		desc.placeholder = 'Aur detail dena chahte ho? (optional)';
		desc.maxLength = 500;
		desc.style.cssText = 'width:100%;background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.1);color:#f1f1f8;border-radius:12px;padding:10px;font-size:.82rem;resize:none;height:80px;font-family:inherit;box-sizing:border-box;outline:none;margin-bottom:14px;';

		// Buttons
		var btnRow = document.createElement('div');
		btnRow.style.cssText = 'display:flex;gap:8px;';

		var cancelBtn = document.createElement('button');
		cancelBtn.textContent = 'Cancel';
		cancelBtn.style.cssText = 'flex:1;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.1);color:#9ca3af;padding:11px;border-radius:12px;cursor:pointer;font-family:inherit;font-size:.85rem;';
		cancelBtn.addEventListener('click', function() { modal.remove(); });

		var submitBtn = document.createElement('button');
		submitBtn.textContent = 'Report Karo';
		submitBtn.style.cssText = 'flex:1;background:linear-gradient(135deg,#f43f5e,#ef4444);border:none;color:#fff;padding:11px;border-radius:12px;cursor:pointer;font-family:inherit;font-size:.85rem;font-weight:700;';
		submitBtn.addEventListener('click', async function() {
			if (!selectedReason) { showToast('Reason select karo', 'error'); return; }
			submitBtn.disabled = true;
			submitBtn.textContent = 'Submitting...';
			try {
				var url = '/api/report/' + (targetType === 'USER' ? 'user' : 'post') + '/' + targetId;
				var res = await fetch(url, {
					method: 'POST',
					headers: { [csrfH()]: csrf(), 'Content-Type': 'application/json' },
					body: JSON.stringify({ reason: selectedReason, description: desc.value.trim() })
				});
				var data = await res.json();
				modal.remove();
				showToast(data.message || 'Report submit ho gayi ✅');
			} catch (e) {
				showToast('Report fail hua. Retry karo.', 'error');
				submitBtn.disabled = false;
				submitBtn.textContent = 'Report Karo';
			}
		});

		btnRow.append(cancelBtn, submitBtn);
		box.append(title, subtitle, grid, desc, btnRow);
		modal.appendChild(box);
		modal.addEventListener('click', function(e) { if (e.target === modal) modal.remove(); });
		document.body.appendChild(modal);
	}

	/* ── 3-dot Menu for Profile page ────────────────────────────────────────── */
	function showProfileMenu(userId, userName, isBlocked, anchorEl) {
		document.getElementById('brMenu')?.remove();

		var menu = document.createElement('div');
		menu.id = 'brMenu';
		var rect = anchorEl ? anchorEl.getBoundingClientRect() : { bottom: 100, right: 100 };
		menu.style.cssText = [
			'position:fixed;',
			'top:' + (rect.bottom + 8) + 'px;',
			'right:' + (window.innerWidth - rect.right) + 'px;',
			'background:#0d0d1a;border:1px solid rgba(255,255,255,.12);',
			'border-radius:14px;overflow:hidden;',
			'box-shadow:0 8px 32px rgba(0,0,0,.5);',
			'z-index:500;min-width:180px;',
			'animation:menuFadeIn .15s ease;'
		].join('');

		var items = [
			{
				icon: '🔗', label: 'Profile link copy karo',
				action: function() {
					navigator.clipboard.writeText(location.origin + '/profile/user/' + userId);
					showToast('Link copy ho gaya!');
				}
			},
			{
				icon: isBlocked ? '✅' : '🚫',
				label: isBlocked ? 'Unblock karo' : 'Block karo',
				color: isBlocked ? '#10b981' : '#f43f5e',
				action: function() {
					if (isBlocked) unblockUser(userId, userName, function() { location.reload(); });
					else blockUser(userId, userName, function() { });
				}
			},
			{
				icon: '⚠️', label: 'Report karo',
				color: '#f59e0b',
				action: function() { showReportModal('USER', userId, userName); }
			}
		];

		items.forEach(function(item) {
			var btn = document.createElement('button');
			btn.style.cssText = [
				'width:100%;background:none;border:none;',
				'color:' + (item.color || '#d1d5db') + ';',
				'padding:12px 16px;text-align:left;cursor:pointer;',
				'font-size:.85rem;font-family:inherit;',
				'display:flex;align-items:center;gap:10px;',
				'border-bottom:1px solid rgba(255,255,255,.06);',
				'transition:background .15s;'
			].join('');
			btn.innerHTML = '<span>' + item.icon + '</span><span>' + item.label + '</span>';
			btn.addEventListener('mouseenter', function() { btn.style.background = 'rgba(255,255,255,.05)'; });
			btn.addEventListener('mouseleave', function() { btn.style.background = 'none'; });
			btn.addEventListener('click', function() { menu.remove(); item.action(); });
			menu.appendChild(btn);
		});

		// Remove last border
		var lastBtn = menu.lastElementChild;
		if (lastBtn) lastBtn.style.borderBottom = 'none';

		document.body.appendChild(menu);

		// Close on outside click
		setTimeout(function() {
			document.addEventListener('click', function handler(e) {
				if (!menu.contains(e.target) && e.target !== anchorEl) {
					menu.remove();
					document.removeEventListener('click', handler);
				}
			});
		}, 10);
	}

	/* ── 3-dot Menu for Reel/Post ───────────────────────────────────────────── */
	function showPostMenu(postId, postOwnerId, isMyPost, anchorEl) {
		document.getElementById('brMenu')?.remove();

		var menu = document.createElement('div');
		menu.id = 'brMenu';
		var rect = anchorEl ? anchorEl.getBoundingClientRect() : { bottom: 100, right: 100 };
		menu.style.cssText = [
			'position:fixed;',
			'top:' + (rect.bottom + 8) + 'px;',
			'right:' + (window.innerWidth - rect.right) + 'px;',
			'background:#0d0d1a;border:1px solid rgba(255,255,255,.12);',
			'border-radius:14px;overflow:hidden;',
			'box-shadow:0 8px 32px rgba(0,0,0,.5);',
			'z-index:500;min-width:180px;',
			'animation:menuFadeIn .15s ease;'
		].join('');

		var items = isMyPost ? [
			{
				icon: '🗑', label: 'Post delete karo', color: '#f43f5e',
				action: function() { deletePost(postId); }
			}
		] : [
			{
				icon: '⚠️', label: 'Post report karo', color: '#f59e0b',
				action: function() { showReportModal('POST', postId, 'Post #' + postId); }
			},
			{
				icon: '🚫', label: 'User block karo', color: '#f43f5e',
				action: function() { blockUser(postOwnerId, 'User', function() { }); }
			}
		];

		items.forEach(function(item) {
			var btn = document.createElement('button');
			btn.style.cssText = [
				'width:100%;background:none;border:none;',
				'color:' + (item.color || '#d1d5db') + ';',
				'padding:12px 16px;text-align:left;cursor:pointer;',
				'font-size:.85rem;font-family:inherit;',
				'display:flex;align-items:center;gap:10px;',
				'border-bottom:1px solid rgba(255,255,255,.06);',
				'transition:background .15s;'
			].join('');
			btn.innerHTML = '<span>' + item.icon + '</span><span>' + item.label + '</span>';
			btn.addEventListener('mouseenter', function() { btn.style.background = 'rgba(255,255,255,.05)'; });
			btn.addEventListener('mouseleave', function() { btn.style.background = 'none'; });
			btn.addEventListener('click', function() { menu.remove(); item.action(); });
			menu.appendChild(btn);
		});

		var lastBtn = menu.lastElementChild;
		if (lastBtn) lastBtn.style.borderBottom = 'none';

		document.body.appendChild(menu);
		setTimeout(function() {
			document.addEventListener('click', function handler(e) {
				if (!menu.contains(e.target) && e.target !== anchorEl) {
					menu.remove();
					document.removeEventListener('click', handler);
				}
			});
		}, 10);
	}

	/* ── Delete post ────────────────────────────────────────────────────────── */
	async function deletePost(postId) {
		if (!confirm('Post delete karna chahte ho? Yeh action undo nahi hoga.')) return;
		try {
			await fetch('/api/post/' + postId, {
				method: 'DELETE',
				headers: { [csrfH()]: csrf() }
			});
			showToast('Post delete ho gayi 🗑');
			// Remove from DOM
			var reel = document.querySelector('.reel[data-post-id="' + postId + '"]');
			if (reel) reel.remove();
		} catch (e) {
			showToast('Delete fail hua.', 'error');
		}
	}

	/* ── Block List page ────────────────────────────────────────────────────── */
	async function loadBlockList() {
		var container = document.getElementById('blockListContainer');
		if (!container) return;
		try {
			var res = await fetch('/api/block/list');
			var data = await res.json();
			container.innerHTML = '';
			if (!data || !data.length) {
				container.innerHTML = [
					'<div style="text-align:center;padding:60px 20px;color:#4b5563">',
					'<div style="font-size:3rem;margin-bottom:12px;opacity:.4">🚫</div>',
					'<div style="font-size:.88rem">Koi blocked user nahi hai</div>',
					'</div>'
				].join('');
				return;
			}
			data.forEach(function(u) {
				var card = document.createElement('div');
				card.style.cssText = 'display:flex;align-items:center;gap:14px;padding:14px 16px;background:#0d0d1a;border:1px solid rgba(255,255,255,.07);border-radius:14px;margin-bottom:8px;';

				var av = document.createElement('div');
				av.style.cssText = 'width:44px;height:44px;border-radius:50%;background:#1a1a2e;display:flex;align-items:center;justify-content:center;font-size:1.2rem;overflow:hidden;flex-shrink:0;';
				if (u.avatar) {
					var img = document.createElement('img');
					img.src = u.avatar; img.style.cssText = 'width:100%;height:100%;object-fit:cover;border-radius:50%;';
					img.onerror = function() { img.style.display = 'none'; av.textContent = '👤'; };
					av.appendChild(img);
				} else { av.textContent = '👤'; }

				var info = document.createElement('div'); info.style.flex = '1';
				var nm = document.createElement('div'); nm.textContent = u.name || 'User';
				nm.style.cssText = 'font-weight:600;font-size:.9rem;color:#e5e7eb;margin-bottom:2px;';
				var since = document.createElement('div');
				since.textContent = 'Blocked: ' + (u.blockedAt ? new Date(u.blockedAt).toLocaleDateString('hi-IN') : '');
				since.style.cssText = 'font-size:.72rem;color:#4b5563;';
				info.append(nm, since);

				var unblockBtn = document.createElement('button');
				unblockBtn.textContent = 'Unblock';
				unblockBtn.style.cssText = 'background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);color:#9ca3af;padding:7px 14px;border-radius:20px;cursor:pointer;font-size:.78rem;font-weight:600;font-family:inherit;';
				unblockBtn.addEventListener('click', function() {
					unblockUser(u.userId, u.name, function() { card.remove(); });
				});

				card.append(av, info, unblockBtn);
				container.appendChild(card);
			});
		} catch (e) {
			console.error('Block list load error:', e);
		}
	}

	/* ── CSS injection ────────────────────────────────────────────────────────── */
	(function injectCSS() {
		if (document.getElementById('br-styles')) return;
		var s = document.createElement('style');
		s.id = 'br-styles';
		s.textContent = '@keyframes menuFadeIn{from{opacity:0;transform:scale(.95)}to{opacity:1;transform:scale(1)}}';
		document.head.appendChild(s);
	})();

	/* ── Toast ──────────────────────────────────────────────────────────────── */
	function showToast(msg, type) {
		document.getElementById('__br-t')?.remove();
		var t = document.createElement('div');
		t.id = '__br-t';
		t.textContent = msg;
		var bg = type === 'error' ? '#f43f5e' : '#10b981';
		t.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:' + bg + ';color:#fff;padding:9px 20px;border-radius:20px;font-size:.8rem;font-weight:600;z-index:9999;pointer-events:none;';
		document.body.appendChild(t);
		setTimeout(function() { t.remove(); }, 3000);
	}

	/* ── Auto-init block list if on that page ────────────────────────────────── */
	document.addEventListener('DOMContentLoaded', function() {
		if (document.getElementById('blockListContainer')) loadBlockList();
	});

	/* ── Expose globally ─────────────────────────────────────────────────────── */
	window.blockUser = blockUser;
	window.unblockUser = unblockUser;
	window.showReportModal = showReportModal;
	window.showProfileMenu = showProfileMenu;
	window.showPostMenu = showPostMenu;
	window.loadBlockList = loadBlockList;

})();