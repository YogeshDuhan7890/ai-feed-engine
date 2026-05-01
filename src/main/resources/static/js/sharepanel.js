/* ================================
   SharePanel.js — Reusable Component
   Report Section 3: Core UI Components
   Share functionality
================================ */

export class SharePanel {
	/**
	 * @param {Object} options
	 * @param {HTMLElement} options.triggerEl - button that opens panel
	 * @param {Function} [options.onShare]   - called with (postId, method)
	 */
	constructor(options = {}) {
		this.triggerEl = options.triggerEl
		this.onShare = options.onShare || null
		this._panel = null
		this.triggerEl?.addEventListener('click', (e) => {
			e.stopPropagation()
			this.open(this.triggerEl.dataset.postid)
		})
	}

	open(postId) {
		this.close()
		const postUrl = `${window.location.origin}/reel/${postId}`
		const panel = document.createElement('div')
		panel.id = '__share-panel'
		panel.style.cssText = `position:fixed;bottom:0;left:0;right:0;
			background:#1a1a1a;border-radius:16px 16px 0 0;padding:20px;
			z-index:1000;border-top:1px solid #333;`

		const title = document.createElement('div')
		title.textContent = 'Share'
		title.style.cssText = 'font-size:16px;font-weight:700;margin-bottom:16px;'

		const options = [
			{ icon: '📋', label: 'Copy Link', action: () => this._copyLink(postUrl) },
			{ icon: '💬', label: 'Send via DM', action: () => this._openDM(postId) },
			{ icon: '🐦', label: 'Twitter / X', action: () => this._shareTwitter(postUrl) },
			{ icon: '📱', label: 'Share...', action: () => this._nativeShare(postUrl) },
		]

		const grid = document.createElement('div')
		grid.style.cssText = 'display:flex;gap:16px;overflow-x:auto;padding-bottom:8px;'
		options.forEach(opt => {
			if (opt.label === 'Share...' && !navigator.share) return
			const btn = document.createElement('button')
			btn.style.cssText = `background:#2a2a2a;border:none;color:#fff;
				padding:12px 16px;border-radius:10px;cursor:pointer;
				display:flex;flex-direction:column;align-items:center;gap:6px;
				font-size:12px;min-width:70px;`
			const icon = document.createElement('span')
			icon.style.fontSize = '22px'
			icon.textContent = opt.icon
			const lbl = document.createElement('span')
			lbl.textContent = opt.label
			btn.append(icon, lbl)
			btn.addEventListener('click', () => { opt.action(); this.close() })
			grid.appendChild(btn)
		})

		const closeBtn = document.createElement('button')
		closeBtn.textContent = 'Cancel'
		closeBtn.style.cssText = `width:100%;margin-top:12px;background:transparent;
			border:1px solid #444;color:#ccc;padding:10px;border-radius:8px;cursor:pointer;`
		closeBtn.addEventListener('click', () => this.close())

		panel.append(title, grid, closeBtn)
		document.body.appendChild(panel)
		this._panel = panel

		// Close on backdrop click
		setTimeout(() => {
			document.addEventListener('click', this._outsideClick = (e) => {
				if (!panel.contains(e.target)) this.close()
			})
		}, 100)

		if (this.onShare) this.onShare(postId, 'open')
	}

	close() {
		this._panel?.remove()
		this._panel = null
		if (this._outsideClick) {
			document.removeEventListener('click', this._outsideClick)
			this._outsideClick = null
		}
	}

	_copyLink(url) {
		navigator.clipboard?.writeText(url)
			.then(() => this._toast('Link copy ho gaya! 🔗'))
			.catch(() => this._toast('Link: ' + url))
	}

	_openDM(postId) {
		window.location.href = `/messages?share=${postId}`
	}

	_shareTwitter(url) {
		window.open(`https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}`, '_blank')
	}

	async _nativeShare(url) {
		try { await navigator.share({ url }) } catch { }
	}

	_toast(msg) {
		document.getElementById('__toast')?.remove()
		const t = document.createElement('div')
		t.id = '__toast'
		t.textContent = msg
		t.style.cssText = 'position:fixed;bottom:30px;left:50%;transform:translateX(-50%);background:#222;color:#fff;padding:10px 22px;border-radius:8px;font-size:14px;z-index:9999;'
		document.body.appendChild(t)
		setTimeout(() => t.remove(), 3000)
	}
}