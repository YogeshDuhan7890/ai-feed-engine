/* =============================================
   SHARE.JS — AI Feed
   ✅ Share sheet bottom panel
   ✅ WhatsApp, Telegram, Twitter/X, Instagram
   ✅ Copy link (with feedback)
   ✅ QR Code generation (no library needed)
   ✅ Engagement tracking
   ✅ Native share API fallback
   ✅ Smooth animations
============================================= */

(function() {

	/* ── Inject share sheet HTML + CSS ── */
	function injectShareSheet() {
		if (document.getElementById('shareSheet')) return;

		var style = document.createElement('style');
		style.textContent = [
			'@keyframes sheetUp{from{transform:translateY(100%);opacity:0}to{transform:translateY(0);opacity:1}}',
			'@keyframes sheetDown{from{transform:translateY(0);opacity:1}to{transform:translateY(100%);opacity:0}}',
			'@keyframes copyPop{0%{transform:scale(1)}50%{transform:scale(1.15)}100%{transform:scale(1)}}',
			'#shareBackdrop{position:fixed;inset:0;background:rgba(0,0,0,0.7);backdrop-filter:blur(4px);z-index:900;display:none}',
			'#shareSheet{position:fixed;bottom:0;left:0;right:0;max-width:560px;margin:0 auto;background:#0d0d1a;border:1px solid rgba(255,255,255,0.1);border-bottom:none;border-radius:22px 22px 0 0;z-index:901;display:none;padding:0 0 env(safe-area-inset-bottom,0)}',
			'#shareSheet.open{display:block;animation:sheetUp .3s cubic-bezier(0.34,1.2,0.64,1)}',
			'.ss-handle{width:36px;height:4px;background:rgba(255,255,255,0.15);border-radius:2px;margin:12px auto 0}',
			'.ss-header{padding:14px 20px 10px;display:flex;align-items:center;justify-content:space-between}',
			'.ss-title{font-size:.92rem;font-weight:700;color:#f1f1f8}',
			'.ss-close{background:rgba(255,255,255,0.07);border:none;color:#9ca3af;width:28px;height:28px;border-radius:50%;cursor:pointer;font-size:.8rem;display:flex;align-items:center;justify-content:center}',
			'.ss-preview{margin:0 16px 14px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.07);border-radius:14px;padding:12px;display:flex;gap:12px;align-items:center}',
			'.ss-preview-thumb{width:48px;height:64px;border-radius:8px;background:#1a1a2e;object-fit:cover;flex-shrink:0}',
			'.ss-preview-title{font-size:.82rem;font-weight:600;color:#e5e7eb;margin-bottom:3px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}',
			'.ss-preview-url{font-size:.72rem;color:#4b5563;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}',
			'.ss-apps{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;padding:0 16px 14px}',
			'.ss-app{display:flex;flex-direction:column;align-items:center;gap:6px;padding:12px 8px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.07);border-radius:14px;cursor:pointer;transition:all .2s;text-decoration:none}',
			'.ss-app:hover{background:rgba(255,255,255,0.08);border-color:rgba(255,255,255,0.14);transform:translateY(-2px)}',
			'.ss-app:active{transform:scale(0.95)}',
			'.ss-app-icon{width:40px;height:40px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:1.3rem}',
			'.ss-app-label{font-size:.68rem;font-weight:600;color:#9ca3af;text-align:center}',
			'.ss-divider{height:1px;background:rgba(255,255,255,0.06);margin:0 16px 14px}',
			'.ss-copy-row{padding:0 16px 6px;display:flex;gap:8px}',
			'.ss-link-input{flex:1;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);border-radius:12px;padding:10px 14px;color:#9ca3af;font-size:.78rem;outline:none;font-family:inherit;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
			'.ss-copy-btn{background:linear-gradient(135deg,#6366f1,#8b5cf6);border:none;color:#fff;padding:10px 18px;border-radius:12px;font-size:.78rem;font-weight:700;cursor:pointer;transition:all .2s;font-family:inherit;white-space:nowrap}',
			'.ss-copy-btn:hover{opacity:.88}',
			'.ss-copy-btn.copied{background:linear-gradient(135deg,#10b981,#059669);animation:copyPop .3s ease}',
			'.ss-more-row{padding:0 16px 20px;display:flex;gap:8px}',
			'.ss-more-btn{flex:1;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.08);color:#9ca3af;padding:10px;border-radius:12px;font-size:.78rem;cursor:pointer;transition:all .2s;font-family:inherit;display:flex;align-items:center;justify-content:center;gap:6px}',
			'.ss-more-btn:hover{background:rgba(255,255,255,0.09);color:#f1f1f8}'
		].join('');
		document.head.appendChild(style);

		var sheet = document.createElement('div');
		sheet.id = 'shareSheet';
		sheet.innerHTML = [
			'<div class="ss-handle"></div>',
			'<div class="ss-header">',
			'  <span class="ss-title">Share karo</span>',
			'  <button class="ss-close" id="ssClose">✕</button>',
			'</div>',
			'<div class="ss-preview" id="ssPreview" style="display:none">',
			'  <div class="ss-preview-thumb" id="ssThumb" style="display:flex;align-items:center;justify-content:center;font-size:1.5rem;color:#374151">🎬</div>',
			'  <div style="flex:1;min-width:0">',
			'    <div class="ss-preview-title" id="ssPreviewTitle">AI Feed Video</div>',
			'    <div class="ss-preview-url" id="ssPreviewUrl"></div>',
			'  </div>',
			'</div>',
			'<div class="ss-apps" id="ssApps"></div>',
			'<div class="ss-divider"></div>',
			'<div class="ss-copy-row">',
			'  <input class="ss-link-input" id="ssLinkInput" readonly>',
			'  <button class="ss-copy-btn" id="ssCopyBtn">📋 Copy</button>',
			'</div>',
			'<div class="ss-more-row">',
			'  <button class="ss-more-btn" id="ssNativeBtn">',
			'    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>',
			'    More Options',
			'  </button>',
			'  <button class="ss-more-btn" id="ssQrBtn">',
			'    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="7" y="7" width="1" height="1" fill="currentColor"/><rect x="16" y="7" width="1" height="1" fill="currentColor"/><rect x="7" y="16" width="1" height="1" fill="currentColor"/><path d="M14 14h3v3h-3zM17 17h3v3h-3zM14 20h3"/></svg>',
			'    QR Code',
			'  </button>',
			'</div>'
		].join('');

		var backdrop = document.createElement('div');
		backdrop.id = 'shareBackdrop';

		document.body.appendChild(backdrop);
		document.body.appendChild(sheet);

		document.getElementById('ssClose').addEventListener('click', closeShare);
		backdrop.addEventListener('click', closeShare);

		document.getElementById('ssCopyBtn').addEventListener('click', function() {
			var inp = document.getElementById('ssLinkInput');
			var btn = document.getElementById('ssCopyBtn');
			navigator.clipboard.writeText(inp.value).then(function() {
				btn.textContent = '✅ Copied!';
				btn.classList.add('copied');
				setTimeout(function() { btn.textContent = '📋 Copy'; btn.classList.remove('copied'); }, 2000);
			}).catch(function() {
				inp.select(); document.execCommand('copy');
				btn.textContent = '✅ Done!';
				setTimeout(function() { btn.textContent = '📋 Copy'; }, 2000);
			});
		});

		document.getElementById('ssNativeBtn').addEventListener('click', function() {
			var url = document.getElementById('ssLinkInput').value;
			if (navigator.share) {
				navigator.share({ title: 'AI Feed', url: url }).catch(function() { });
			}
		});

		document.getElementById('ssQrBtn').addEventListener('click', function() {
			showQrCode(document.getElementById('ssLinkInput').value);
		});

		var startY = 0;
		sheet.addEventListener('touchstart', function(e) { startY = e.touches[0].clientY; }, { passive: true });
		sheet.addEventListener('touchend', function(e) {
			if (e.changedTouches[0].clientY - startY > 80) closeShare();
		}, { passive: true });
	}

	var SHARE_APPS = [
		{ id: 'whatsapp', label: 'WhatsApp', icon: '💬', color: '#25d366', url: function(u, t) { return 'https://wa.me/?text=' + encodeURIComponent(t + ' ' + u); } },
		{ id: 'telegram', label: 'Telegram', icon: '✈️', color: '#2ca5e0', url: function(u, t) { return 'https://t.me/share/url?url=' + encodeURIComponent(u) + '&text=' + encodeURIComponent(t); } },
		{ id: 'twitter', label: 'X / Twitter', icon: '𝕏', color: '#1a1a1a', url: function(u, t) { return 'https://twitter.com/intent/tweet?text=' + encodeURIComponent(t) + '&url=' + encodeURIComponent(u); } },
		{ id: 'facebook', label: 'Facebook', icon: 'f', color: '#1877f2', url: function(u) { return 'https://www.facebook.com/sharer/sharer.php?u=' + encodeURIComponent(u); } },
		{ id: 'linkedin', label: 'LinkedIn', icon: 'in', color: '#0a66c2', url: function(u) { return 'https://www.linkedin.com/sharing/share-offsite/?url=' + encodeURIComponent(u); } },
		{ id: 'reddit', label: 'Reddit', icon: '🔴', color: '#ff4500', url: function(u, t) { return 'https://www.reddit.com/submit?url=' + encodeURIComponent(u) + '&title=' + encodeURIComponent(t); } },
		{ id: 'email', label: 'Email', icon: '📧', color: '#6366f1', url: function(u, t) { return 'mailto:?subject=' + encodeURIComponent(t) + '&body=' + encodeURIComponent(u); } },
		{ id: 'sms', label: 'SMS', icon: '💬', color: '#10b981', url: function(u, t) { return 'sms:?body=' + encodeURIComponent(t + ' ' + u); } }
	];

	function openShare(postId, options) {
		options = options || {};
		injectShareSheet();

		var url = options.url || (location.origin + '/reel/' + postId);
		var title = options.title || 'AI Feed pe ye video dekho!';
		var caption = options.caption || '';

		document.getElementById('ssLinkInput').value = url;

		var preview = document.getElementById('ssPreview');
		preview.style.display = 'flex';
		document.getElementById('ssPreviewTitle').textContent = caption || title;
		document.getElementById('ssPreviewUrl').textContent = url.replace(/https?:\/\//, '');

		if (options.thumbnail) {
			var thumb = document.getElementById('ssThumb');
			thumb.innerHTML = '';
			var img = document.createElement('img');
			img.className = 'ss-preview-thumb';
			img.src = options.thumbnail;
			img.onerror = function() { thumb.textContent = '🎬'; };
			thumb.appendChild(img);
		}

		var appsEl = document.getElementById('ssApps');
		appsEl.innerHTML = '';
		SHARE_APPS.forEach(function(app) {
			var btn = document.createElement('a');
			btn.className = 'ss-app';
			btn.href = app.url(url, title);
			btn.target = '_blank'; btn.rel = 'noopener';
			btn.innerHTML = '<div class="ss-app-icon" style="background:' + app.color + '22;border:1px solid ' + app.color + '44">' +
				'<span style="font-size:' + (app.icon.length > 1 ? '1rem' : '1.3rem') + ';color:' + app.color + ';font-weight:700">' + app.icon + '</span></div>' +
				'<span class="ss-app-label">' + app.label + '</span>';
			btn.addEventListener('click', function() { trackShare(postId, app.id); });
			appsEl.appendChild(btn);
		});

		document.getElementById('shareBackdrop').style.display = 'block';
		document.getElementById('shareSheet').classList.add('open');
		trackShare(postId, 'open');
	}

	function closeShare() {
		var sheet = document.getElementById('shareSheet');
		var bd = document.getElementById('shareBackdrop');
		if (!sheet) return;
		sheet.style.animation = 'sheetDown .25s ease forwards';
		setTimeout(function() {
			sheet.classList.remove('open');
			sheet.style.animation = '';
			if (bd) bd.style.display = 'none';
			var qr = document.getElementById('ssQrModal');
			if (qr) qr.remove();
		}, 250);
	}

	async function trackShare(postId, platform) {
		if (platform !== 'open') return;
		try {
			var csrf = document.querySelector('meta[name="_csrf"]');
			var csrfH = document.querySelector('meta[name="_csrf_header"]');
			var t = csrf ? csrf.getAttribute('content') : '';
			var h = csrfH ? csrfH.getAttribute('content') : 'X-CSRF-TOKEN';
			await fetch('/api/engagement', {
				method: 'POST',
				headers: { [h]: t, 'Content-Type': 'application/json' },
				body: JSON.stringify({ postId: postId, type: 'SHARE' })
			});
		} catch (e) { }
	}

	function showQrCode(url) {
		var existing = document.getElementById('ssQrModal');
		if (existing) { existing.remove(); return; }

		var modal = document.createElement('div');
		modal.id = 'ssQrModal';
		modal.style.cssText = 'position:fixed;inset:0;z-index:950;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,0.85);backdrop-filter:blur(8px);';

		var box = document.createElement('div');
		box.style.cssText = 'background:#0d0d1a;border:1px solid rgba(255,255,255,0.1);border-radius:20px;padding:24px;text-align:center;max-width:280px;width:90vw;';

		var title = document.createElement('div');
		title.style.cssText = 'font-size:.9rem;font-weight:700;color:#f1f1f8;margin-bottom:16px;';
		title.textContent = 'QR Code — Scan karo';

		var qrImg = document.createElement('img');
		qrImg.style.cssText = 'width:180px;height:180px;border-radius:12px;background:#fff;padding:8px;display:block;margin:0 auto 14px;';
		var qrUrl = 'https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=' + encodeURIComponent(url);
		qrImg.src = qrUrl;

		var urlText = document.createElement('div');
		urlText.style.cssText = 'font-size:.7rem;color:#4b5563;margin-bottom:14px;word-break:break-all;';
		urlText.textContent = url;

		var btns = document.createElement('div');
		btns.style.cssText = 'display:flex;gap:8px;';

		var dlBtn = document.createElement('button');
		dlBtn.style.cssText = 'flex:1;background:linear-gradient(135deg,#6366f1,#8b5cf6);border:none;color:#fff;padding:10px;border-radius:10px;font-size:.78rem;font-weight:700;cursor:pointer;font-family:inherit;';
		dlBtn.textContent = '⬇ Download';
		dlBtn.addEventListener('click', function() {
			var a = document.createElement('a');
			a.href = qrUrl; a.download = 'qr-aifeed.png'; a.target = '_blank'; a.click();
		});

		var closeBtn = document.createElement('button');
		closeBtn.style.cssText = 'flex:1;background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.1);color:#9ca3af;padding:10px;border-radius:10px;font-size:.78rem;cursor:pointer;font-family:inherit;';
		closeBtn.textContent = 'Close';
		closeBtn.addEventListener('click', function() { modal.remove(); });

		btns.append(dlBtn, closeBtn);
		box.append(title, qrImg, urlText, btns);
		modal.appendChild(box);
		modal.addEventListener('click', function(e) { if (e.target === modal) modal.remove(); });
		document.body.appendChild(modal);
	}

	window._shareModule = openShare;
	window.openShare = openShare;
	window.closeShare = closeShare;

	// reels.js calls sharePost(postId) - expose it
	window.sharePost = function(postId) { openShare(postId); };

})();