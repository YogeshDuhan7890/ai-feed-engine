/* ================================
   admin-audit.js
   Audit Log Page
================================ */

let currentPage = 0
let totalEntries = 0
let searchTimer = null
let allEntriesCache = []

function csrfHeaders() {
	return {
		'Content-Type': 'application/json',
		[CSRF_HEADER]: CSRF_TOKEN
	}
}

// ========================
// INIT
// ========================
document.addEventListener('DOMContentLoaded', () => {
	loadStats()
	loadLog()
})

// ========================
// LOAD STATS
// ========================
async function loadStats() {
	try {
		const res = await fetch('/api/admin/audit/stats')
		const data = await res.json()
		const counts = data.actionCounts || {}

		setText('statTotal', data.total ?? '—')

		const deletes = (counts['USER_DELETE'] || 0) + (counts['POST_DELETE'] || 0) + (counts['COMMENT_DELETE'] || 0)
		const blocks = (counts['USER_BLOCK'] || 0) + (counts['USER_TEMPBAN'] || 0)
		const roles = counts['ROLE_ASSIGN'] || 0
		const bcast = counts['PUSH_BROADCAST'] || 0
		const other = (data.total || 0) - deletes - blocks - roles - bcast

		setText('statDeletes', deletes)
		setText('statBlocks', blocks)
		setText('statRoles', roles)
		setText('statBroadcasts', bcast)
		setText('statOther', Math.max(0, other))
	} catch (e) {
		console.error('Stats fail:', e)
	}
}

// ========================
// LOAD LOG
// ========================
async function loadLog(page) {
	if (page !== undefined) currentPage = page
	const size = parseInt(document.getElementById('pageSize')?.value || 50)
	const search = buildSearchQuery()

	try {
		const url = `/api/admin/audit?page=${currentPage}&size=${size}&search=${encodeURIComponent(search)}`
		const res = await fetch(url)
		const data = await res.json()

		totalEntries = data.total || 0
		renderTable(data.entries || [])
		renderPagination(currentPage, size, totalEntries)
		setText('pageInfo', `Showing ${currentPage * size + 1}–${Math.min((currentPage + 1) * size, totalEntries)} of ${totalEntries} entries`)
	} catch (e) {
		document.getElementById('auditTableBody').innerHTML =
			`<tr><td colspan="4" class="text-danger text-center py-3">Load fail: ${e.message}</td></tr>`
	}
}

function buildSearchQuery() {
	const text = document.getElementById('searchInput')?.value.trim() || ''
	const action = document.getElementById('filterAction')?.value || ''
	return [text, action].filter(Boolean).join(' ')
}

// ========================
// RENDER TABLE
// ========================
function renderTable(entries) {
	const tbody = document.getElementById('auditTableBody')
	if (!entries || entries.length === 0) {
		tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">Koi audit entry nahi mili</td></tr>'
		return
	}

	tbody.innerHTML = entries.map(raw => {
		let entry = {}
		try { entry = JSON.parse(raw) } catch { return '' }

		const action = entry.action || '—'
		const badgeCls = 'action-badge action-' + action
		const actionBadge = `<span class="${badgeCls}">${action}</span>`

		return `<tr>
			<td class="time-cell">${escHtml(entry.time || '—')}</td>
			<td class="small">${escHtml(entry.admin || '—')}</td>
			<td>${actionBadge}</td>
			<td class="detail-cell" title="${escHtml(entry.detail || '')}">${escHtml(entry.detail || '—')}</td>
		</tr>`
	}).join('')
}

// ========================
// PAGINATION
// ========================
function renderPagination(page, size, total) {
	const totalPages = Math.ceil(total / size)
	const el = document.getElementById('pagination')
	if (!el || totalPages <= 1) { if (el) el.innerHTML = ''; return }

	let html = ''
	html += `<button class="btn btn-outline-secondary btn-sm page-btn" onclick="loadLog(${page - 1})" ${page === 0 ? 'disabled' : ''}>‹</button>`

	// Show up to 7 page buttons
	const start = Math.max(0, page - 3)
	const end = Math.min(totalPages - 1, page + 3)
	for (let i = start; i <= end; i++) {
		html += `<button class="btn btn-sm page-btn ${i === page ? 'btn-primary' : 'btn-outline-secondary'}" onclick="loadLog(${i})">${i + 1}</button>`
	}

	html += `<button class="btn btn-outline-secondary btn-sm page-btn" onclick="loadLog(${page + 1})" ${page >= totalPages - 1 ? 'disabled' : ''}>›</button>`
	el.innerHTML = html
}

// ========================
// SEARCH DEBOUNCE
// ========================
function debounceSearch() {
	clearTimeout(searchTimer)
	searchTimer = setTimeout(() => { currentPage = 0; loadLog() }, 400)
}

// ========================
// CLEAR LOG
// ========================
async function clearLog() {
	if (!confirm('Saara audit log delete karein? Ye undo nahi ho sakta.')) return
	try {
		await fetch('/api/admin/audit', { method: 'DELETE', headers: csrfHeaders() })
		currentPage = 0
		await Promise.all([loadStats(), loadLog()])
		showToast('Audit log cleared ✓', 'success')
	} catch (e) {
		showToast('Clear fail: ' + e.message, 'danger')
	}
}

// ========================
// EXPORT CSV
// ========================
async function exportAuditCSV() {
	try {
		const res = await fetch('/api/admin/audit?page=0&size=500&search=' + encodeURIComponent(buildSearchQuery()))
		const data = await res.json()
		const entries = data.entries || []

		let csv = 'Time,Admin,Action,Detail\n'
		entries.forEach(raw => {
			try {
				const e = JSON.parse(raw)
				csv += `"${e.time || ''}","${e.admin || ''}","${e.action || ''}","${(e.detail || '').replace(/"/g, '""')}"\n`
			} catch { }
		})

		const blob = new Blob([csv], { type: 'text/csv' })
		const url = URL.createObjectURL(blob)
		const a = document.createElement('a')
		a.href = url
		a.download = 'audit_log_' + new Date().toISOString().slice(0, 10) + '.csv'
		a.click()
		URL.revokeObjectURL(url)
	} catch (e) {
		showToast('Export fail: ' + e.message, 'danger')
	}
}

// ========================
// UTILS
// ========================
function showToast(msg, type) {
	const div = document.createElement('div')
	div.className = `alert alert-${type} position-fixed bottom-0 end-0 m-3 shadow`
	div.style.cssText = 'z-index:9999;min-width:240px;font-size:0.85rem'
	div.textContent = msg
	document.body.appendChild(div)
	setTimeout(() => div.remove(), 3000)
}

function setText(id, val) {
	const el = document.getElementById(id)
	if (el) el.textContent = val
}

function escHtml(str) {
	if (!str) return ''
	return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
