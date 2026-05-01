/* ================================
   NotificationService.js
   Report Section 2: services/ folder
   Handles real-time notification polling (Section 8)
================================ */

export class NotificationService {
	constructor({ pollInterval = 30000 } = {}) {
		this._interval = pollInterval
		this._timer    = null
		this._handlers = []
		this._lastCount = 0
	}

	/* Start polling */
	start() {
		if (this._timer) return
		this._poll()
		this._timer = setInterval(() => this._poll(), this._interval)
	}

	stop() {
		clearInterval(this._timer)
		this._timer = null
	}

	/* Register a listener for new notifications */
	onChange(fn) { this._handlers.push(fn) }

	async _poll() {
		try {
			const res = await fetch('/api/notifications/unread-count')
			if (!res.ok) return
			const data = await res.json()
			const count = data?.count ?? 0
			if (count !== this._lastCount) {
				this._lastCount = count
				this._handlers.forEach(fn => fn(count))
			}
		} catch {}
	}

	async markAllRead() {
		const m = document.querySelector('meta[name="_csrf"]')
		const h = document.querySelector('meta[name="_csrf_header"]')
		const token = m?.getAttribute('content') || ''
		const header = h?.getAttribute('content') || 'X-CSRF-TOKEN'
		await fetch('/api/notifications/read-all', {
			method: 'POST',
			headers: token ? { [header]: token } : {}
		})
		this._lastCount = 0
		this._handlers.forEach(fn => fn(0))
	}
}