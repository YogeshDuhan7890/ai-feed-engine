const TEMPLATES = {
	new_feature: {
		title: 'New Feature Launch',
		message: 'AI Feed par naya feature live ho gaya hai. Abhi check karo.',
		url: '/feed'
	},
	maintenance: {
		title: 'Scheduled Maintenance',
		message: 'Short maintenance window planned hai. Thodi der baad dobara check karo.',
		url: '/feed'
	},
	trending: {
		title: 'Trending Right Now',
		message: 'Top videos aur trends dekhne ke liye tap karo.',
		url: '/feed'
	},
	welcome: {
		title: 'Welcome to AI Feed',
		message: 'Apna pehla video upload karo aur community join karo.',
		url: '/feed'
	}
}

let vapidConfigured = false

function csrfHeaders() {
	return {
		'Content-Type': 'application/json',
		[CSRF_HEADER]: CSRF_TOKEN
	}
}

async function loadStats() {
	try {
		const res = await fetch('/api/admin/push/stats')
		const data = await res.json()
		vapidConfigured = !!data.vapidConfigured

		setText('statTotalSubs', data.totalSubscriptions ?? '-')
		setText('statActiveUsers', data.activeSubscribers ?? '-')
		setText('statBroadcastCount', (data.broadcastHistory || []).length)

		const vapidEl = document.getElementById('vapidStatus')
		if (vapidEl) {
			vapidEl.innerHTML = data.vapidConfigured
				? '<span class="vapid-ok">VAPID configured</span>'
				: '<span class="vapid-warn">VAPID missing - push send nahi hoga</span>'
		}

		const vapidMeta = document.getElementById('vapidMeta')
		if (vapidMeta) {
			const key = data.vapidPublicKey || ''
			const subject = data.vapidSubject || 'mailto:admin@aifeed.com'
			vapidMeta.textContent = data.vapidConfigured
				? `Subject: ${subject} | Public key: ${key.slice(0, 20)}...`
				: 'Abhi runtime VAPID keys configured nahi hain.'
		}

		const generateBtn = document.getElementById('generateVapidBtn')
		if (generateBtn) {
			generateBtn.textContent = data.vapidConfigured ? 'Regenerate VAPID Keys' : 'Generate Free VAPID Keys'
		}

		const history = data.broadcastHistory || []
		if (history.length > 0) {
			try {
				const last = JSON.parse(history[0])
				setText('statLastSent', last.time || '-')
			} catch {
				setText('statLastSent', '-')
			}
		} else {
			setText('statLastSent', 'Never')
		}

		renderHistory(history)
	} catch (error) {
		console.error('Push stats load fail:', error)
	}
}

function renderHistory(history) {
	const el = document.getElementById('historyList')
	if (!el) return

	if (!history || history.length === 0) {
		el.innerHTML = '<div class="text-muted small text-center py-3">Abhi tak koi broadcast nahi bheja gaya.</div>'
		return
	}

	el.innerHTML = history.map(raw => {
		let item = {}
		try {
			item = JSON.parse(raw)
		} catch {
			return ''
		}

		const targetBadge = item.target === 'user'
			? `<span class="badge bg-warning text-dark target-badge">User #${escHtml(item.userId || '?')}</span>`
			: '<span class="badge bg-primary target-badge">All</span>'

		return `
			<div class="history-item">
				<div class="d-flex justify-content-between align-items-start gap-2">
					<div>
						<div><strong>${escHtml(item.title || '')}</strong> ${targetBadge}</div>
						<div class="text-muted mt-1">${escHtml(item.message || '')}</div>
						<div class="small text-muted mt-1">${escHtml(item.url || '/feed')} | ${item.sent ?? 0} sent</div>
					</div>
					<div class="time text-nowrap">${escHtml(item.time || '')}</div>
				</div>
			</div>
		`
	}).join('')
}

async function sendBroadcast() {
	const title = document.getElementById('notifTitle')?.value.trim()
	const message = document.getElementById('notifMessage')?.value.trim()
	const url = document.getElementById('notifUrl')?.value.trim() || '/feed'
	const target = document.querySelector('input[name="target"]:checked')?.value || 'all'
	const userId = document.getElementById('userId')?.value.trim()

	if (!title) return showResult('Title zaroor daalo', 'warning')
	if (!message) return showResult('Message khali nahi ho sakta', 'warning')
	if (target === 'user' && !userId) return showResult('Specific user ke liye User ID daalo', 'warning')
	if (!vapidConfigured) return showResult('Pehle free VAPID keys generate karo', 'warning')

	const btn = document.getElementById('sendBtn')
	btn.disabled = true
	btn.textContent = 'Sending...'

	try {
		const body = { title, message, url, target }
		if (target === 'user') body.userId = userId

		const res = await fetch('/api/admin/push/broadcast', {
			method: 'POST',
			headers: csrfHeaders(),
			body: JSON.stringify(body)
		})
		const data = await res.json()

		if (data.success) {
			showResult(data.message || 'Notification bhej di gayi.', 'success')
			await loadStats()
		} else {
			showResult(data.message || 'Broadcast fail ho gaya', 'danger')
		}
	} catch (error) {
		showResult('Network error: ' + error.message, 'danger')
	} finally {
		btn.disabled = false
		btn.textContent = 'Send Notification'
	}
}

async function generateVapidKeys() {
	const btn = document.getElementById('generateVapidBtn')
	if (btn) {
		btn.disabled = true
		btn.textContent = 'Generating...'
	}

	try {
		const res = await fetch('/api/push/generate-keys', { headers: csrfHeaders() })
		const data = await res.json()
		if (data.error) {
			showResult(`VAPID generate fail: ${data.error}`, 'danger')
			return
		}
		showResult('Free VAPID keys generate aur activate ho gayi.', 'success')
		await loadStats()
	} catch (error) {
		showResult('VAPID generate fail: ' + error.message, 'danger')
	} finally {
		if (btn) {
			btn.disabled = false
			btn.textContent = vapidConfigured ? 'Regenerate VAPID Keys' : 'Generate Free VAPID Keys'
		}
	}
}

async function clearHistory() {
	if (!confirm('Saari broadcast history clear karni hai?')) return
	try {
		await fetch('/api/admin/push/history', {
			method: 'DELETE',
			headers: csrfHeaders()
		})
		await loadStats()
	} catch (error) {
		showResult('History clear fail: ' + error.message, 'danger')
	}
}

function toggleUserField() {
	const target = document.querySelector('input[name="target"]:checked')?.value
	const field = document.getElementById('userIdField')
	if (field) field.style.display = target === 'user' ? '' : 'none'
}

function applyTemplate(key) {
	const template = TEMPLATES[key]
	if (!template) return
	setVal('notifTitle', template.title)
	setVal('notifMessage', template.message)
	setVal('notifUrl', template.url)
	updatePreview()
	updateCount('notifTitle', 'titleCount', 50)
	updateCount('notifMessage', 'msgCount', 120)
}

function clearForm() {
	setVal('notifTitle', '')
	setVal('notifMessage', '')
	setVal('notifUrl', '/feed')
	setVal('userId', '')
	document.getElementById('targetAll').checked = true
	toggleUserField()
	updatePreview()
	updateCount('notifTitle', 'titleCount', 50)
	updateCount('notifMessage', 'msgCount', 120)
	const result = document.getElementById('sendResult')
	if (result) result.style.display = 'none'
}

function updatePreview() {
	setText('prevTitle', document.getElementById('notifTitle')?.value || 'Title yahan aayega')
	setText('prevMsg', document.getElementById('notifMessage')?.value || 'Message yahan dikhega...')
	setText('prevUrl', document.getElementById('notifUrl')?.value || '/feed')
}

function updateCount(inputId, countId, max) {
	const value = document.getElementById(inputId)?.value?.length || 0
	const el = document.getElementById(countId)
	if (!el) return
	el.textContent = `${value} / ${max}`
	el.className = 'char-count' + (value > max * 0.9 ? ' warn' : '') + (value >= max ? ' error' : '')
}

function showResult(msg, type) {
	const el = document.getElementById('sendResult')
	if (!el) return
	el.innerHTML = `<div class="alert alert-${type} py-2 mb-0">${msg}</div>`
	el.style.display = ''
	setTimeout(() => {
		el.style.display = 'none'
	}, 5000)
}

function setText(id, value) {
	const el = document.getElementById(id)
	if (el) el.textContent = value
}

function setVal(id, value) {
	const el = document.getElementById(id)
	if (el) el.value = value
}

function escHtml(str) {
	return String(str || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}

updatePreview()
updateCount('notifTitle', 'titleCount', 50)
updateCount('notifMessage', 'msgCount', 120)
loadStats()
