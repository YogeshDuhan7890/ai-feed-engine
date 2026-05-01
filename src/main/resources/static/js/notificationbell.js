/* ================================
   NotificationBell.js — Reusable Component
   Report Section 3: Core UI Components
   Report Section 8: Notification System
   ✅ Unread badge indicator
   ✅ Dropdown notification panel
   ✅ Click to navigate to content
   ✅ Real-time polling (30s)
================================ */

export class NotificationBell {
	/**
	 * @param {Object} options
	 * @param {HTMLElement} options.bellEl    - bell icon button
	 * @param {HTMLElement} options.badgeEl   - badge span
	 * @param {HTMLElement} [options.dropdownEl] - dropdown panel (optional)
	 * @param {number} [options.pollInterval] - ms between polls (default 30000)
	 */
	constructor(options) {
		this.bellEl = options.bellEl
		this.badgeEl = options.badgeEl
		this.dropdownEl = options.dropdownEl || null
		this.pollInterval = options.pollInterval || 30000
		this._open = false
		this._timer = null
		this._csrf = this._getCsrf.bind(this)

		this.bellEl?.addEventListener('click', (e) => {
			e.stopPropagation()
			this._open ? this.closeDropdown() : this.openDropdown()
		})
		document.addEventListener('click', () => { if (this._open) this.closeDropdown() })

		this.fetchCount()
		this._timer = setInterval(() => this.fetchCount(), this.pollInterval)
	}

	/* Fetch unread count and update badge */
	async fetchCount() {
		try {
			const res = await fetch('/api/notifications/unread-count')
			if (!res.ok) return
			const data = await res.json()
			const count = data?.count ?? 0
			if (this.badgeEl) {
				this.badgeEl.textContent = count > 9 ? '9+' : count
				this.badgeEl.style.display = count > 0 ? 'inline-block' : 'none'
			}
		} catch { }
	}

	/* Open dropdown and load notifications */
	async openDropdown() {
		if (!this.dropdownEl) return
		this._open = true
		this.dropdownEl.style.display = 'block'
		this.dropdownEl.textContent = ''

		const loading = document.createElement('div')
		loading.style.cssText = 'padding:16px;text-align:center;color:#888;font-size:13px;'
		loading.textContent = 'Loading...'
		this.dropdownEl.appendChild(loading)

		try {
			const res = await fetch('/api/notifications')
			const data = await res.json()
			this.dropdownEl.textContent = ''
			if (!data?.length) {
				const p = document.createElement('div')
				p.style.cssText = 'padding:16px;text-align:center;color:#666;font-size:13px;'
				p.textContent = 'Koi notification nahi 🔕'
				this.dropdownEl.appendChild(p)
				return
			}
			data.slice(0, 10).forEach(n => this.dropdownEl.appendChild(this._buildItem(n)))
		} catch {
			this.dropdownEl.textContent = 'Load fail ho gayi.'
		}
	}

	closeDropdown() {
		this._open = false
		if (this.dropdownEl) this.dropdownEl.style.display = 'none'
	}

	_buildItem(n) {
		const icons = { LIKE: '❤️', COMMENT: '💬', FOLLOW: '👤', SHARE: '↗️' }
		const div = document.createElement('div')
		div.style.cssText = `display:flex;align-items:center;gap:10px;padding:10px 14px;
			cursor:pointer;border-bottom:1px solid #2a2a2a;
			background:${n.read ? 'transparent' : '#1f0f14'};`
		div.addEventListener('click', () => {
			if (!n.read) this._markRead(n.id, div)
			if (n.link) window.location.href = n.link  // Section 8: click to navigate
		})
		const icon = document.createElement('span')
		icon.style.fontSize = '20px'
		icon.textContent = icons[n.type] || '🔔'
		const body = document.createElement('div')
		body.style.flex = '1'
		const msg = document.createElement('div')
		msg.style.cssText = 'font-size:13px;color:#eee;'
		msg.textContent = n.message || ''
		const time = document.createElement('div')
		time.style.cssText = 'font-size:11px;color:#666;margin-top:2px;'
		time.textContent = this._formatTime(n.createdAt)
		body.append(msg, time)
		div.append(icon, body)
		return div
	}

	async _markRead(id, el) {
		try {
			await fetch(`/api/notifications/${id}/read`, {
				method: 'POST', headers: this._csrf()
			})
			el.style.background = 'transparent'
			await this.fetchCount()
		} catch { }
	}

	_getCsrf(extra = {}) {
		const m = document.querySelector('meta[name="_csrf"]')
		const h = document.querySelector('meta[name="_csrf_header"]')
		const token = m?.getAttribute('content') || ''
		const header = h?.getAttribute('content') || 'X-CSRF-TOKEN'
		return token ? { [header]: token, ...extra } : extra
	}

	_formatTime(dateStr) {
		if (!dateStr) return ''
		const diff = Date.now() - new Date(dateStr).getTime()
		const m = Math.floor(diff / 60000)
		if (m < 1) return 'abhi'
		if (m < 60) return `${m}m`
		const h = Math.floor(m / 60)
		if (h < 24) return `${h}h`
		return `${Math.floor(h / 24)}d`
	}

	destroy() {
		clearInterval(this._timer)
	}
}