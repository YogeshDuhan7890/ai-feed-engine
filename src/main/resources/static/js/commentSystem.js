/* ================================
   commentSystem.js — FULLY UPGRADED
   ✅ Reusable ES6 class
   ✅ Load + submit comments
   ✅ XSS safe
   ✅ Like comment (with Redis toggle)
   ✅ Reply to comment (nested, 1 level)
   ✅ Delete own comment
   ✅ Char count on input
   ✅ Optimistic UI (show immediately)
   ✅ Error rollback on fail
   ✅ NEW: Emoji picker (20 emojis)
   ✅ NEW: @mention highlight
   ✅ NEW: Reply indicator bar
   ✅ NEW: Skeleton loader
   ✅ NEW: Animated item entry
   ✅ NEW: Comment count live update
================================ */

const EMOJIS = ['😂', '❤️', '🔥', '👏', '😍', '🙌', '💯', '😮', '🥹', '✨', '😭', '🎉', '💪', '👀', '🤣', '😊', '🙏', '💀', '👍', '🥰']

class CommentSystem {
	constructor(options) {
		this.listEl = options.listEl
		this.inputEl = options.inputEl
		this.sendBtn = options.sendBtn
		this.onCount = options.onCount || null
		this.postId = null
		this.myUserId = options.myUserId || null
		this._replyingTo = null   // { commentId, userName }

		this._initInput()
		this._injectStyles()
	}

	/* ── Init input listeners ── */
	_initInput() {
		// Send on click
		this.sendBtn?.addEventListener('click', () => this.submit())

		// Send on Enter (not Shift+Enter)
		this.inputEl?.addEventListener('keydown', e => {
			if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.submit() }
			if (e.key === 'Escape') this._cancelReply()
		})

		// Char count
		this.inputEl?.addEventListener('input', () => {
			const max = 300
			const len = this.inputEl.value.length
			const el = document.getElementById('commentCharCount')
			if (el) {
				el.textContent = `${len}/${max}`
				el.style.color = len > 270 ? '#f59e0b' : len >= max ? '#f43f5e' : '#4b5563'
			}
			if (this.sendBtn) this.sendBtn.disabled = len === 0 || len > max
		})

		// Emoji button
		const emojiBtn = document.getElementById('commentEmojiBtn')
		emojiBtn?.addEventListener('click', e => { e.stopPropagation(); this._toggleEmojiPicker() })
	}

	/* ── Load comments ── */
	async load(postId) {
		this.postId = postId
		this._cancelReply()
		if (!this.listEl) return
		this.listEl.innerHTML = this._skeletonHTML()

		try {
			const res = await fetch(`/api/comments/${postId}`)
			if (!res.ok) throw new Error('Fetch failed')
			const data = await res.json()
			this.listEl.innerHTML = ''

			if (!data?.length) {
				this._showEmpty()
				this.onCount?.(0)
				return
			}

			const totalCount = data.reduce((a, c) => a + 1 + (c.replies?.length || 0), 0)
			data.forEach(c => {
				this.listEl.appendChild(this._buildItem(c, false))
				// Render replies indented
				if (c.replies?.length) {
					c.replies.forEach(r => this.listEl.appendChild(this._buildItem(r, false, true)))
				}
			})
			this.onCount?.(totalCount)

		} catch {
			this.listEl.innerHTML = ''
			this._showMsg('Comments load nahi hue. Refresh karo.', '#f87171')
		}
	}

	/* ── Submit comment / reply ── */
	async submit() {
		const text = this.inputEl?.value.trim()
		if (!text || !this.postId) return
		if (text.length > 300) return

		const isReply = !!this._replyingTo
		const parentId = this._replyingTo?.commentId

		// Optimistic UI
		const tempId = 'temp-' + Date.now()
		const tempEl = this._buildItem({
			id: tempId,
			text,
			userName: 'You',
			createdAt: new Date().toISOString()
		}, true, isReply)
		tempEl.style.opacity = '0.5'
		this.listEl?.appendChild(tempEl)

		if (this.inputEl) this.inputEl.value = ''
		if (this.sendBtn) { this.sendBtn.disabled = true }
		this._cancelReply()

		try {
			const body = { text }
			if (parentId) body.parentId = String(parentId)

			const res = await fetch(`/api/comments/${this.postId}`, {
				method: 'POST',
				headers: this._csrfH({ 'Content-Type': 'application/json' }),
				body: JSON.stringify(body)
			})
			if (!res.ok) throw new Error('Failed')

			tempEl.remove()
			await this.load(this.postId)

		} catch {
			tempEl.remove()
			if (this.inputEl) this.inputEl.value = text
			this._toast('Comment send nahi hua. Retry karo.', 'error')
		} finally {
			if (this.sendBtn) this.sendBtn.disabled = false
		}
	}

	/* ── Build comment item ── */
	_buildItem(c, isTemp = false, isReply = false) {
		const div = document.createElement('div')
		div.className = 'cs-comment-item' + (isReply ? ' cs-reply' : '')
		div.dataset.id = c.id
		div.style.cssText = `
      display:flex;gap:10px;
      padding:${isReply ? '8px 14px 8px 36px' : '12px 16px'};
      border-bottom:1px solid rgba(255,255,255,0.05);
      animation:csFadeIn .2s ease;
      ${isReply ? 'border-left:2px solid rgba(99,102,241,0.2);margin-left:16px;' : ''}
    `

		// Avatar
		const av = document.createElement('div')
		av.style.cssText = 'width:32px;height:32px;border-radius:50%;background:#1a1a2e;display:flex;align-items:center;justify-content:center;font-size:1rem;flex-shrink:0;overflow:hidden;'
		if (c.userAvatar) {
			const img = document.createElement('img')
			img.src = c.userAvatar
			img.style.cssText = 'width:100%;height:100%;object-fit:cover;border-radius:50%'
			img.onerror = () => { img.style.display = 'none'; av.textContent = '👤' }
			av.appendChild(img)
		} else {
			av.textContent = '👤'
		}

		// Body
		const body = document.createElement('div')
		body.style.flex = '1'

		// Name + time row
		const top = document.createElement('div')
		top.style.cssText = 'display:flex;align-items:center;gap:7px;margin-bottom:3px;'
		const nm = document.createElement('span')
		nm.style.cssText = 'font-weight:700;font-size:.82rem;color:#e5e7eb;cursor:pointer;'
		nm.textContent = c.userName || 'User'
		if (c.userId) nm.onclick = () => { location.href = '/profile/user/' + c.userId }
		const tm = document.createElement('span')
		tm.style.cssText = 'font-size:.68rem;color:#4b5563;'
		tm.textContent = this._fmtTime(c.createdAt)
		top.append(nm, tm)

		// Text — @mention highlight
		const tx = document.createElement('div')
		tx.style.cssText = 'font-size:.82rem;color:#d1d5db;line-height:1.5;margin-bottom:5px;word-break:break-word;'
		tx.innerHTML = this._hlMentions(this._esc(c.text || ''))

		// Actions row
		const actions = document.createElement('div')
		actions.style.cssText = 'display:flex;align-items:center;gap:12px;'

		// Like btn
		const likeBtn = document.createElement('button')
		likeBtn.style.cssText = 'background:none;border:none;color:#6b7280;font-size:.72rem;cursor:pointer;padding:0;display:flex;align-items:center;gap:3px;transition:color .15s;font-family:inherit;'
		const likeCount = c.likeCount || 0
		likeBtn.innerHTML = `❤️${likeCount > 0 ? ' ' + likeCount : ''}`
		if (!isTemp) {
			likeBtn.addEventListener('click', async () => {
				likeBtn.disabled = true
				try {
					const res = await fetch(`/api/comments/${c.id}/like`, {
						method: 'POST', headers: this._csrfH()
					})
					const data = await res.json()
					likeBtn.innerHTML = `❤️${data.likes > 0 ? ' ' + data.likes : ''}`
					likeBtn.style.color = '#f43f5e'
				} catch { } finally { likeBtn.disabled = false }
			})
		}
		actions.appendChild(likeBtn)

		// Reply btn (only top-level)
		if (!isReply && !isTemp) {
			const replyBtn = document.createElement('button')
			replyBtn.style.cssText = 'background:none;border:none;color:#6b7280;font-size:.72rem;cursor:pointer;padding:0;transition:color .15s;font-family:inherit;'
			replyBtn.textContent = 'Reply'
			replyBtn.addEventListener('click', () => this._startReply(c.id, c.userName))
			actions.appendChild(replyBtn)
		}

		// Delete btn
		if (isTemp || (this.myUserId && String(c.userId) === String(this.myUserId))) {
			const delBtn = document.createElement('button')
			delBtn.style.cssText = 'background:none;border:none;color:#4b5563;font-size:.72rem;cursor:pointer;padding:0;margin-left:auto;transition:color .15s;font-family:inherit;'
			delBtn.textContent = 'Delete'
			delBtn.addEventListener('click', async () => {
				if (isTemp) { div.remove(); return }
				if (!confirm('Comment delete karna chahte ho?')) return
				try {
					await fetch(`/api/comments/${c.id}`, { method: 'DELETE', headers: this._csrfH() })
					div.remove()
					// Remove replies below if any
					const nextEl = div.nextElementSibling
					if (nextEl?.classList.contains('cs-reply') && nextEl.dataset.parentId == c.id) nextEl.remove()
					const remaining = this.listEl?.querySelectorAll('.cs-comment-item:not(.cs-reply)').length || 0
					this.onCount?.(remaining)
				} catch { this._toast('Delete fail hua.', 'error') }
			})
			actions.appendChild(delBtn)
		}

		body.append(top, tx, actions)
		div.append(av, body)
		return div
	}

	/* ── Reply mode ── */
	_startReply(commentId, userName) {
		this._replyingTo = { commentId, userName }
		// Show indicator
		let bar = document.getElementById('cs-reply-bar')
		if (!bar) {
			bar = document.createElement('div')
			bar.id = 'cs-reply-bar'
			bar.style.cssText = 'padding:6px 14px;background:rgba(99,102,241,.1);border-top:1px solid rgba(99,102,241,.2);font-size:.72rem;color:#818cf8;display:flex;align-items:center;justify-content:space-between;'
			const wrap = this.inputEl?.parentElement
			if (wrap) wrap.insertBefore(bar, wrap.firstChild)
		}
		bar.innerHTML = `<span>↩ @${this._esc(userName)} ko reply</span>
      <button style="background:none;border:none;color:#6b7280;cursor:pointer;font-size:.82rem;" id="cs-cancel-reply">✕</button>`
		document.getElementById('cs-cancel-reply')?.addEventListener('click', () => this._cancelReply())
		if (this.inputEl) {
			this.inputEl.placeholder = `@${userName} ko reply...`
			this.inputEl.focus()
		}
	}

	_cancelReply() {
		this._replyingTo = null
		document.getElementById('cs-reply-bar')?.remove()
		if (this.inputEl) this.inputEl.placeholder = 'Comment likhein...'
	}

	/* ── Emoji Picker ── */
	_toggleEmojiPicker() {
		const existing = document.getElementById('cs-emoji-picker')
		if (existing) { existing.remove(); return }

		const picker = document.createElement('div')
		picker.id = 'cs-emoji-picker'
		picker.style.cssText = `
      position:absolute;bottom:56px;left:10px;right:10px;
      background:#0d0d1a;border:1px solid rgba(255,255,255,.1);
      border-radius:14px;padding:10px;
      display:flex;flex-wrap:wrap;gap:4px;
      z-index:60;box-shadow:0 8px 24px rgba(0,0,0,.5);
      animation:csFadeIn .15s ease;
    `
		EMOJIS.forEach(emoji => {
			const btn = document.createElement('button')
			btn.style.cssText = 'background:none;border:none;font-size:1.25rem;cursor:pointer;padding:5px 7px;border-radius:8px;transition:background .1s;'
			btn.textContent = emoji
			btn.onmouseenter = () => btn.style.background = 'rgba(255,255,255,.08)'
			btn.onmouseleave = () => btn.style.background = 'none'
			btn.onclick = () => {
				if (this.inputEl) {
					const pos = this.inputEl.selectionStart || this.inputEl.value.length
					this.inputEl.value = this.inputEl.value.slice(0, pos) + emoji + this.inputEl.value.slice(pos)
					this.inputEl.dispatchEvent(new Event('input'))
					this.inputEl.focus()
				}
				picker.remove()
			}
			picker.appendChild(btn)
		})

		const wrap = this.inputEl?.closest('.comments-input-row')
		if (wrap) { wrap.style.position = 'relative'; wrap.appendChild(picker) }

		// Auto-close
		setTimeout(() => {
			document.addEventListener('click', function h(e) {
				if (!picker.contains(e.target) && e.target.id !== 'commentEmojiBtn') {
					picker.remove()
					document.removeEventListener('click', h)
				}
			})
		}, 10)
	}

	/* ── Inject CSS ── */
	_injectStyles() {
		if (document.getElementById('cs-styles')) return
		const style = document.createElement('style')
		style.id = 'cs-styles'
		style.textContent = `
      @keyframes csFadeIn {
        from { opacity:0; transform:translateY(6px); }
        to   { opacity:1; transform:translateY(0); }
      }
      .cs-comment-item:last-child { border-bottom:none; }
      .cs-comment-item:hover { background:rgba(255,255,255,.02); }
      .cs-reply { background:rgba(99,102,241,.03); }
    `
		document.head.appendChild(style)
	}

	/* ── Skeleton ── */
	_skeletonHTML() {
		return Array(3).fill(0).map(() => `
      <div style="display:flex;gap:10px;padding:12px 16px;border-bottom:1px solid rgba(255,255,255,.04)">
        <div style="width:32px;height:32px;border-radius:50%;background:#111122;flex-shrink:0;animation:skPulse 1.4s infinite"></div>
        <div style="flex:1;display:flex;flex-direction:column;gap:6px;padding-top:2px">
          <div style="height:9px;width:28%;border-radius:4px;background:#111122;animation:skPulse 1.4s infinite"></div>
          <div style="height:9px;width:65%;border-radius:4px;background:#111122;animation:skPulse 1.4s infinite"></div>
        </div>
      </div>`).join('')
	}

	/* ── Empty state ── */
	_showEmpty() {
		if (!this.listEl) return
		this.listEl.innerHTML = `
      <div style="text-align:center;padding:40px 20px;color:#4b5563">
        <div style="font-size:2.5rem;margin-bottom:10px;opacity:.5">💬</div>
        <div style="font-size:.82rem">Pehla comment karo!</div>
      </div>`
	}

	_showMsg(msg, color = '#6b7280') {
		if (!this.listEl) return
		const p = document.createElement('p')
		p.style.cssText = `color:${color};padding:20px;text-align:center;font-size:.82rem;`
		p.textContent = msg
		this.listEl.appendChild(p)
	}

	/* ── Utils ── */
	_toast(msg, type = 'info') {
		document.getElementById('__cs-toast')?.remove()
		const colors = { error: '#f43f5e', success: '#10b981', info: '#6366f1' }
		const t = document.createElement('div'); t.id = '__cs-toast'
		t.textContent = msg
		t.style.cssText = `position:fixed;bottom:80px;left:50%;transform:translateX(-50%);
      background:${colors[type] || colors.info};color:#fff;padding:9px 20px;
      border-radius:20px;font-size:.8rem;font-weight:600;z-index:9999;pointer-events:none;`
		document.body.appendChild(t)
		setTimeout(() => t.remove(), 3000)
	}

	_fmtTime(dateStr) {
		if (!dateStr) return ''
		const diff = Math.floor((Date.now() - new Date(dateStr)) / 60000)
		if (diff < 1) return 'abhi'
		if (diff < 60) return diff + 'm'
		const h = Math.floor(diff / 60)
		if (h < 24) return h + 'h'
		return Math.floor(h / 24) + 'd'
	}

	_hlMentions(text) {
		return text.replace(/@(\w+)/g,
			'<span style="color:#818cf8;font-weight:600">@$1</span>')
	}

	_esc(s) {
		return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
	}

	_csrfH(extra = {}) {
		const t = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || ''
		const h = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN'
		return t ? { [h]: t, ...extra } : extra
	}
}



/* ── Auto init when DOM ready ── */
document.addEventListener('DOMContentLoaded', function() {
	var listEl = document.getElementById('commentsList');
	var inputEl = document.getElementById('commentInput');
	var sendBtn = document.getElementById('sendCommentBtn');

	if (!listEl || !inputEl) return;

	window._commentSystem = new CommentSystem({
		listEl: listEl,
		inputEl: inputEl,
		sendBtn: sendBtn,
		myUserId: window.__myUserId || null,
		onCount: function(n) {
			var pid = window._activePostId;
			if (pid) {
				var sel = '.action-btn.comment-btn[data-postid="' + pid + '"]';
				var btn = document.querySelector(sel);
				var lbl = btn && btn.querySelector('.btn-label');
				if (lbl) lbl.textContent = n > 0 ? n : 'Comment';
			}
			var hdr = document.querySelector('#commentsPanel .comments-header span');
			if (hdr) hdr.textContent = n > 0 ? ('💬 Comments (' + n + ')') : '💬 Comments';
		}
	});

	window.openComments = function(postId) {
		window._activePostId = postId;
		var panel = document.getElementById('commentsPanel');
		var bd = document.getElementById('backdrop');
		if (panel) panel.classList.add('open');
		if (bd) bd.classList.add('open');
		window._commentSystem && window._commentSystem.load(postId);
	};

	window.closeComments = function() {
		var panel = document.getElementById('commentsPanel');
		var bd = document.getElementById('backdrop');
		if (panel) panel.classList.remove('open');
		if (bd) bd.classList.remove('open');
		window._commentSystem && window._commentSystem._cancelReply();
		window._activePostId = null;
	};

	window.submitComment = function() {
		window._commentSystem && window._commentSystem.submit();
	};

	var closeBtn = document.getElementById('closeCommentsBtn');
	if (closeBtn) closeBtn.addEventListener('click', window.closeComments);

	var backdrop = document.getElementById('backdrop');
	if (backdrop) backdrop.addEventListener('click', window.closeComments);
});