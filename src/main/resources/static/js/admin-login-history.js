let allHistory = []

document.addEventListener('DOMContentLoaded', () => {
	fetchLoginHistory()
})

function fetchLoginHistory() {
	fetch('/api/admin/login-history', {
		method: 'GET',
		headers: {
			'Content-Type': 'application/json',
			...(CSRF_HEADER && CSRF_TOKEN ? { [CSRF_HEADER]: CSRF_TOKEN } : {})
		}
	})
		.then(response => response.json())
		.then(data => {
			if (data.error) {
				throw new Error(data.error)
			}

			allHistory = (data.entries || [])
				.map(entry => normalizeEntry(entry))
				.filter(Boolean)

			renderTable(allHistory)
			document.getElementById('totalCount').textContent = data.total || allHistory.length
			document.getElementById('activeSessions').textContent = `${data.activeSessions || 0} Active Sessions`
		})
		.catch(error => {
			console.error(error)
			document.getElementById('historyBody').innerHTML =
				'<tr><td colspan="6" class="text-danger text-center py-4">Failed to load login history.</td></tr>'
		})
}

function normalizeEntry(entry) {
	try {
		const parsed = typeof entry === 'string' ? JSON.parse(entry) : entry
		const device = parsed.device
			? [parsed.device.browser, parsed.device.os].filter(Boolean).join(' / ')
			: (parsed.userAgent || '-')
		const location = parsed.location
			? [parsed.location.city, parsed.location.country].filter(Boolean).join(', ')
			: '-'

		return {
			loginTime: parsed.time,
			email: parsed.email || '-',
			ip: parsed.ip || '-',
			device: device || '-',
			location: location || '-',
			rawUserAgent: parsed.userAgent || '-'
		}
	} catch {
		return null
	}
}

function renderTable(rows) {
	const tbody = document.getElementById('historyBody')
	if (!rows.length) {
		tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No login records found.</td></tr>'
		return
	}

	tbody.innerHTML = rows.map(item => `
		<tr>
			<td>${formatTime(item.loginTime)}</td>
			<td>${escapeHtml(item.email)}</td>
			<td>${escapeHtml(item.ip)}</td>
			<td>${escapeHtml(shorten(item.device, 32))}</td>
			<td>${escapeHtml(shorten(item.location, 30))}</td>
			<td title="${escapeHtml(item.rawUserAgent)}">${escapeHtml(shorten(item.rawUserAgent, 42))}</td>
		</tr>
	`).join('')
}

function filterHistory(query) {
	const value = String(query || '').toLowerCase().trim()
	const filtered = allHistory.filter(item =>
		(item.email || '').toLowerCase().includes(value) ||
		(item.ip || '').toLowerCase().includes(value) ||
		(item.device || '').toLowerCase().includes(value) ||
		(item.location || '').toLowerCase().includes(value) ||
		(item.rawUserAgent || '').toLowerCase().includes(value)
	)

	renderTable(filtered)
	document.getElementById('totalCount').textContent = filtered.length
}

function formatTime(value) {
	if (!value) return '-'
	const date = new Date(value)
	return Number.isNaN(date.getTime()) ? value : date.toLocaleString('en-IN')
}

function shorten(text, max) {
	if (!text) return '-'
	return text.length > max ? `${text.slice(0, max)}...` : text
}

function escapeHtml(value) {
	return String(value || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}
