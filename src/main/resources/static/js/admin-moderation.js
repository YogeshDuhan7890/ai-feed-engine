/* admin-moderation.js */
let scanResults = [], activeFilter = 'all'
function csrfH() { return { 'Content-Type': 'application/json', [CSRF_HEADER]: CSRF_TOKEN } }
document.addEventListener('DOMContentLoaded', loadAiModerationStatus)

async function loadAiModerationStatus() {
	try {
		const res = await fetch('/api/admin/moderation/ai-key-check')
		const data = await res.json()
		const enabled = !!data.enabled
		const toggle = document.getElementById('aiModerationToggle')
		const badge = document.getElementById('aiModerationStatus')
		if (toggle) toggle.checked = enabled
		if (badge) {
			badge.textContent = enabled ? 'AI ON' : 'AI OFF'
			badge.className = 'badge ' + (enabled ? 'bg-success' : 'bg-secondary')
		}
	} catch (e) {
		toast('AI status check failed: ' + e.message, 'warning')
	}
}

async function toggleAiModeration(input) {
	const enabled = !!input.checked
	try {
		const res = await fetch('/api/admin/moderation/ai-toggle', {
			method: 'POST',
			headers: csrfH(),
			body: JSON.stringify({ enabled })
		})
		const data = await res.json()
		if (!data.success) {
			input.checked = !enabled
			toast('Toggle fail: ' + (data.message || 'Unknown error'), 'danger')
			return
		}
		toast('AI moderation ' + (enabled ? 'enabled' : 'disabled') + ' ✓', 'success')
		loadAiModerationStatus()
	} catch (e) {
		input.checked = !enabled
		toast('Toggle error: ' + e.message, 'danger')
	}
}

async function runScan() {
	const btn = document.getElementById('scanBtn')
	const limit = parseInt(document.getElementById('scanLimit')?.value || 20)
	btn.disabled = true; btn.textContent = '⏳ Scanning...'
	try {
		const res = await fetch('/api/admin/moderation/scan', { method: 'POST', headers: csrfH(), body: JSON.stringify({ limit }) })
		const data = await res.json()
		if (!data.success) { toast('Scan fail: ' + data.message, 'danger'); return }
		scanResults = data.flagged || []
		const scanned = data.scanned || 0
		const high = scanResults.filter(r => r.severity === 'HIGH').length
		setText('statScanned', scanned)
		setText('statFlagged', scanResults.length)
		setText('statHigh', high)
		setText('statClean', scanned - scanResults.length)
		renderResults(scanResults)
		toast(`✅ Scan complete: ${scanResults.length} flagged out of ${scanned}`, 'success')
	} catch (e) { toast('Error: ' + e.message, 'danger') }
	finally { btn.disabled = false; btn.textContent = '🔍 Run Scan' }
}

function filterResults(filter) {
	activeFilter = filter
		;['All', 'High', 'Med', 'Low'].forEach(f => {
			const btn = document.getElementById('filter' + (f === 'All' ? 'All' : f === 'High' ? 'High' : f === 'Med' ? 'Med' : 'Low'))
			if (btn) btn.classList.toggle('active', (f === 'All' ? 'all' : f === 'High' ? 'HIGH' : f === 'Med' ? 'MEDIUM' : 'LOW') === filter)
		})
	const filtered = filter === 'all' ? scanResults : scanResults.filter(r => r.severity === filter)
	renderResults(filtered)
}

function renderResults(results) {
	const el = document.getElementById('resultsContainer')
	if (!results.length) {
		el.innerHTML = '<div class="alert alert-success text-center">✅ Koi flagged post nahi mili selected filter mein</div>'
		return
	}
	el.innerHTML = results.map(r => {
		const sevColor = r.severity === 'HIGH' ? 'danger' : r.severity === 'MEDIUM' ? 'warning' : 'info'
		const sevIcon = r.severity === 'HIGH' ? '🔴' : r.severity === 'MEDIUM' ? '🟡' : '🔵'
		return `<div class="card shadow-sm p-3 mb-3 border-${sevColor}">
			<div class="d-flex justify-content-between align-items-start">
				<div class="flex-grow-1">
					<div class="d-flex align-items-center gap-2 mb-2">
						<span>${sevIcon}</span>
						<span class="badge bg-${sevColor}">${r.severity}</span>
						<span class="text-muted small">Post #${r.postId} · @${esc(r.userName || 'User#' + r.userId)}</span>
						<span class="ms-auto fw-bold text-${sevColor}">Score: ${r.score}</span>
					</div>
					<div class="small p-2 rounded mb-2" style="background:#f8f9fa;font-style:italic">${esc(r.content || '(no content)')}</div>
					<div class="d-flex flex-wrap gap-1">
						${(r.reasons || []).map(reason => `<span class="badge bg-light text-dark border" style="font-size:0.7rem">${esc(reason)}</span>`).join('')}
					</div>
				</div>
				<div class="d-flex flex-column gap-1 ms-3">
					<button class="btn btn-sm btn-warning" onclick="hidePost(${r.postId}, this)">🙈 Hide</button>
					<button class="btn btn-sm btn-danger" onclick="deletePost(${r.postId}, this)">🗑 Delete</button>
					<button class="btn btn-sm btn-outline-secondary" onclick="ignorePost(${r.postId}, this)">✓ Ignore</button>
				</div>
			</div>
		</div>`
	}).join('')
}

async function hidePost(postId, btn) {
	btn.disabled = true
	await fetch('/admin/posts/hide/' + postId, { method: 'POST', headers: csrfH() })
	removeFromResults(postId)
	toast('Post hidden ✓', 'success')
}

async function deletePost(postId, btn) {
	if (!confirm('Delete this post?')) return
	btn.disabled = true
	await fetch('/admin/posts/delete/' + postId, { method: 'POST', headers: csrfH() })
	removeFromResults(postId)
	toast('Post deleted ✓', 'success')
}

function ignorePost(postId, btn) {
	removeFromResults(postId)
	toast('Ignored ✓', 'secondary')
}

function removeFromResults(postId) {
	scanResults = scanResults.filter(r => r.postId !== postId)
	filterResults(activeFilter)
	setText('statFlagged', scanResults.length)
}

async function bulkAction() {
	if (!scanResults.length) { toast('Koi flagged post nahi hai', 'warning'); return }
	if (!confirm(`${scanResults.length} flagged posts hide karein?`)) return
	for (const r of scanResults) {
		await fetch('/admin/posts/hide/' + r.postId, { method: 'POST', headers: csrfH() })
	}
	scanResults = []
	filterResults('all')
	toast(`✅ ${scanResults.length} posts hidden`, 'success')
}

function setText(id, v) { const el = document.getElementById(id); if (el) el.textContent = v }
function esc(s) { return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }
function toast(msg, type) { const d = document.createElement('div'); d.className = `alert alert-${type} position-fixed bottom-0 end-0 m-3`; d.style.cssText = 'z-index:9999;min-width:240px'; d.textContent = msg; document.body.appendChild(d); setTimeout(() => d.remove(), 3500) }