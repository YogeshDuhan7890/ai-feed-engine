/* ═══════════════════════════════════════════════════════════
   admin-videos.js — Professional Video Management
   ✅ Search (content + tags + user)
   ✅ Tag filter (click chip to filter)
   ✅ User filter
   ✅ Status filter (visible/hidden)
   ✅ Sort (newest/oldest/likes/comments)
   ✅ Grid + List view toggle
   ✅ Bulk select + bulk hide/unhide/delete
   ✅ Per-row hide/unhide/delete
   ✅ Video detail modal
   ✅ Tags edit inline
   ✅ Toast notifications
   ✅ Debounced search
   ✅ Stats update
═══════════════════════════════════════════════════════════ */

let allVideos = []        // master list from API
let filteredVideos = []   // after client-side filter
let selectedIds = new Set()
let currentView = 'list'
let debounceTimer = null

// ── Init ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
	loadVideos()
})

// ── Debounced input filter ────────────────────────────────────────────────────
function debounceFilter() {
	clearTimeout(debounceTimer)
	debounceTimer = setTimeout(applyFilters, 350)
}

// ── Load from API ─────────────────────────────────────────────────────────────
async function loadVideos() {
	showSpinner(true)
	try {
		const sort = document.getElementById('sortSelect')?.value || 'newest'
		const url = `/api/admin/videos?sort=${sort}`
		const res = await fetch(url, { headers: { [CSRF_HEADER]: CSRF_TOKEN } })
		if (!res.ok) throw new Error('API error: ' + res.status)
		const data = await res.json()
		allVideos = Array.isArray(data) ? data : (data.videos || [])
		applyFilters()
	} catch (e) {
		console.error('Videos load error:', e)
		toast('Videos load nahi hui: ' + e.message, 'error')
	} finally {
		showSpinner(false)
	}
}

// ── Client-side filter + render ───────────────────────────────────────────────
function applyFilters() {
	const search = (document.getElementById('searchInput')?.value || '').toLowerCase().trim()
	const tag    = (document.getElementById('tagInput')?.value   || '').toLowerCase().trim()
	const user   = (document.getElementById('userInput')?.value  || '').toLowerCase().trim()
	const status = document.getElementById('statusFilter')?.value || 'all'
	const sort   = document.getElementById('sortSelect')?.value   || 'newest'

	filteredVideos = allVideos.filter(v => {
		const content  = (v.content || '').toLowerCase()
		const tags     = (v.tags    || '').toLowerCase()
		const userName = (v.userName || '').toLowerCase()

		if (search && !content.includes(search) && !tags.includes(search) && !userName.includes(search)) return false
		if (tag    && !tags.includes(tag))       return false
		if (user   && !userName.includes(user))  return false
		if (status === 'visible' && v.hidden)     return false
		if (status === 'hidden'  && !v.hidden)    return false
		if (status === 'scheduled') {
			const st = String(v.status || '').toUpperCase()
			const dueTs = v.scheduledAt ? new Date(v.scheduledAt).getTime() : 0
			const isPending = st === 'SCHEDULED' || (Number.isFinite(dueTs) && dueTs > Date.now())
			if (!isPending) return false
		}
		return true
	})

	// Sort
	filteredVideos.sort((a, b) => {
		if (sort === 'likes')    return (b.likes || 0)    - (a.likes || 0)
		if (sort === 'comments') return (b.comments || 0) - (a.comments || 0)
		const da = a.createdAt ? new Date(a.createdAt) : new Date(0)
		const db = b.createdAt ? new Date(b.createdAt) : new Date(0)
		return sort === 'oldest' ? da - db : db - da
	})

	updateStats()
	updateResultCount()

	if (currentView === 'list') renderList()
	else renderGrid()
}

// ── Stats bar ─────────────────────────────────────────────────────────────────
function updateStats() {
	document.getElementById('totalCount').textContent  = allVideos.length
	document.getElementById('hiddenCount').textContent = allVideos.filter(v => v.hidden).length
}

function updateResultCount() {
	const el = document.getElementById('resultCount')
	if (el) el.textContent = filteredVideos.length + ' results'
}

// ── LIST VIEW ─────────────────────────────────────────────────────────────────
function renderList() {
	const tbody = document.getElementById('videoTableBody')
	if (!tbody) return

	if (!filteredVideos.length) {
		showEmpty(true)
		tbody.innerHTML = ''
		return
	}
	showEmpty(false)

	tbody.innerHTML = filteredVideos.map(v => {
		const thumb = v.videoUrl
			? `<video class="thumb-video" src="${escUrl(v.videoUrl)}" muted preload="metadata"
				onclick="openModal(${v.id})" style="cursor:pointer"
				onmouseenter="this.play()" onmouseleave="this.pause();this.currentTime=0"></video>`
			: `<div class="thumb-placeholder">No Video</div>`

		const tags = buildTagChips(v.tags)
		const hiddenBadge = v.hidden
			? `<span class="badge" style="background:rgba(244,63,94,.2);color:#fca5a5;font-size:10px">Hidden</span>`
			: `<span class="badge" style="background:rgba(16,185,129,.2);color:#6ee7b7;font-size:10px">Visible</span>`

		const st = String(v.status || '').toUpperCase()
		const dueTs = v.scheduledAt ? new Date(v.scheduledAt).getTime() : 0
		const isPending = st === 'SCHEDULED' || (Number.isFinite(dueTs) && dueTs > Date.now())
		const scheduledBadge = isPending
			? `<span class="badge" style="background:rgba(59,130,246,.2);color:#93c5fd;font-size:10px;margin-left:6px">Scheduled</span>`
			: ''

		const likeBar = `
			<div class="like-bar"><div class="like-fill" style="width:${Math.min((v.likes||0)*5,100)}%"></div></div>
			<span class="like-count">❤ ${v.likes||0}</span>`

		const hideBtn = v.hidden
			? `<button onclick="unhideVideo(${v.id},this)" title="Unhide"
				style="background:rgba(16,185,129,.15);border:1px solid rgba(16,185,129,.3);color:#6ee7b7;border-radius:7px;padding:4px 8px;font-size:11px;cursor:pointer;font-weight:600">👁</button>`
			: `<button onclick="hideVideo(${v.id},this)" title="Hide"
				style="background:rgba(245,158,11,.15);border:1px solid rgba(245,158,11,.3);color:#fde68a;border-radius:7px;padding:4px 8px;font-size:11px;cursor:pointer;font-weight:600">🙈</button>`

		return `<tr id="row-${v.id}" class="${selectedIds.has(v.id)?'table-active':''}">
			<td>
				<input type="checkbox" data-id="${v.id}" onchange="toggleSelect(${v.id},this.checked)"
					${selectedIds.has(v.id)?'checked':''} style="accent-color:var(--primary)">
			</td>
			<td style="font-size:12px;color:var(--text-muted)">#${v.id}</td>
			<td class="thumb-cell">${thumb}</td>
			<td>
				<div style="font-size:13px;color:var(--text);max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
					title="${esc(v.content||'')}">${esc(v.content||'—')}</div>
				<div style="font-size:10px;color:var(--text-dim);margin-top:2px">
					${formatDate(v.createdAt)}
					${isPending && v.scheduledAt ? `<br/><span style="color:#93c5fd">Due: ${formatDate(v.scheduledAt)}</span>` : ''}
				</div>
			</td>
			<td>
				<span style="font-size:12px;font-weight:600;color:var(--text)" onclick="filterByUser('${esc(v.userName||'')}');event.stopPropagation()"
					style="cursor:pointer">${esc(v.userName||'—')}</span>
			</td>
			<td>${tags}</td>
			<td>${likeBar}</td>
			<td><span style="font-size:12px;color:var(--text-muted)">💬 ${v.comments||0}</span></td>
			<td>${hiddenBadge}${scheduledBadge}</td>
			<td>
				<div class="d-flex gap-1">
					<button onclick="openModal(${v.id})" title="View"
						style="background:rgba(99,102,241,.15);border:1px solid rgba(99,102,241,.3);color:#a5b4fc;border-radius:7px;padding:4px 8px;font-size:11px;cursor:pointer">👁</button>
					${hideBtn}
					<button onclick="deleteVideo(${v.id},this)" title="Delete"
						style="background:rgba(244,63,94,.15);border:1px solid rgba(244,63,94,.3);color:#fca5a5;border-radius:7px;padding:4px 8px;font-size:11px;cursor:pointer;font-weight:600">🗑</button>
				</div>
			</td>
		</tr>`
	}).join('')
}

// ── GRID VIEW ─────────────────────────────────────────────────────────────────
function renderGrid() {
	const container = document.getElementById('videoGridContainer')
	if (!container) return

	if (!filteredVideos.length) { showEmpty(true); container.innerHTML = ''; return }
	showEmpty(false)

	container.innerHTML = filteredVideos.map(v => {
		const thumb = v.videoUrl
			? `<video class="grid-thumb" src="${escUrl(v.videoUrl)}" muted preload="metadata"
				onmouseenter="this.play()" onmouseleave="this.pause();this.currentTime=0"></video>`
			: `<div class="grid-thumb-placeholder">🎬</div>`

		const badge = v.hidden
			? `<span class="badge grid-badge" style="background:rgba(244,63,94,.8);color:#fff">Hidden</span>`
			: `<span class="badge grid-badge" style="background:rgba(16,185,129,.8);color:#fff">Visible</span>`

		const st = String(v.status || '').toUpperCase()
		const dueTs = v.scheduledAt ? new Date(v.scheduledAt).getTime() : 0
		const isPending = st === 'SCHEDULED' || (Number.isFinite(dueTs) && dueTs > Date.now())
		const scheduledBadge = isPending
			? `<span class="badge grid-badge" style="background:rgba(59,130,246,.85);color:#fff;margin-left:6px">Scheduled</span>`
			: ''

		const hideBtn = v.hidden
			? `<button onclick="unhideVideo(${v.id},this);event.stopPropagation()"
				style="background:rgba(16,185,129,.2);border:1px solid rgba(16,185,129,.3);color:#6ee7b7;border-radius:6px;padding:4px;font-size:11px;cursor:pointer">👁 Unhide</button>`
			: `<button onclick="hideVideo(${v.id},this);event.stopPropagation()"
				style="background:rgba(245,158,11,.2);border:1px solid rgba(245,158,11,.3);color:#fde68a;border-radius:6px;padding:4px;font-size:11px;cursor:pointer">🙈 Hide</button>`

		return `<div class="col-md-3 col-sm-4 col-6">
			<div class="video-grid-card ${selectedIds.has(v.id)?'selected':''}" id="card-${v.id}" onclick="openModal(${v.id})">
				<input type="checkbox" class="grid-select" data-id="${v.id}"
					onchange="toggleSelect(${v.id},this.checked);event.stopPropagation()"
					${selectedIds.has(v.id)?'checked':''}
					style="accent-color:var(--primary)">
				${badge}${scheduledBadge}
				${thumb}
				<div class="grid-card-body">
					<div class="user-name">${esc(v.userName||'—')}</div>
					<div class="meta">❤ ${v.likes||0}  •  💬 ${v.comments||0}</div>
					<div class="meta" style="margin-top:4px">${buildTagChipsSmall(v.tags)}</div>
				</div>
				<div class="grid-actions" onclick="event.stopPropagation()">
					${hideBtn}
					<button onclick="deleteVideo(${v.id},this)"
						style="background:rgba(244,63,94,.2);border:1px solid rgba(244,63,94,.3);color:#fca5a5;border-radius:6px;padding:4px;font-size:11px;cursor:pointer;flex:1">🗑 Delete</button>
				</div>
			</div>
		</div>`
	}).join('')
}

// ── View toggle ───────────────────────────────────────────────────────────────
function setView(view) {
	currentView = view
	document.getElementById('viewList').style.display  = view==='list'  ? '' : 'none'
	document.getElementById('viewGrid').style.display  = view==='grid'  ? '' : 'none'

	const btnList = document.getElementById('btnList')
	const btnGrid = document.getElementById('btnGrid')
	if (view === 'list') {
		btnList.style.background = 'var(--primary)'; btnList.style.color = '#fff'
		btnGrid.style.background = 'var(--bg3)'; btnGrid.style.color = 'var(--text-muted)'
	} else {
		btnGrid.style.background = 'var(--primary)'; btnGrid.style.color = '#fff'
		btnList.style.background = 'var(--bg3)'; btnList.style.color = 'var(--text-muted)'
	}
	if (view === 'list') renderList()
	else renderGrid()
}

// ── Select / Deselect ─────────────────────────────────────────────────────────
function toggleSelect(id, checked) {
	if (checked) selectedIds.add(id)
	else selectedIds.delete(id)
	updateBulkBar()
	// sync checkboxes
	document.querySelectorAll(`[data-id="${id}"]`).forEach(cb => cb.checked = checked)
	// card border in grid
	const card = document.getElementById('card-' + id)
	if (card) card.classList.toggle('selected', checked)
}

function toggleSelectAll(checked) {
	filteredVideos.forEach(v => {
		if (checked) selectedIds.add(v.id)
		else selectedIds.delete(v.id)
	})
	document.querySelectorAll('[data-id]').forEach(cb => cb.checked = checked)
	filteredVideos.forEach(v => {
		const card = document.getElementById('card-' + v.id)
		if (card) card.classList.toggle('selected', checked)
	})
	updateBulkBar()
}

function clearSelection() {
	selectedIds.clear()
	document.querySelectorAll('[data-id]').forEach(cb => cb.checked = false)
	document.querySelectorAll('.video-grid-card').forEach(c => c.classList.remove('selected'))
	document.getElementById('selectAll').checked = false
	updateBulkBar()
}

function updateBulkBar() {
	const bar = document.getElementById('bulkBar')
	const cnt = document.getElementById('selectedCount')
	const count = selectedIds.size
	cnt.textContent = count
	bar.classList.toggle('show', count > 0)
}

// ── Tag click → filter ────────────────────────────────────────────────────────
function filterByTag(tag) {
	document.getElementById('tagInput').value = tag
	applyFilters()
}
function filterByUser(user) {
	document.getElementById('userInput').value = user
	applyFilters()
}

// ── Single row actions ────────────────────────────────────────────────────────
async function hideVideo(id, btn) {
	await postAction(`/admin/posts/hide/${id}`, btn, null, () => {
		updateVideoInList(id, { hidden: true })
		toast('Video hide ho gayi', 'success')
	})
}

async function unhideVideo(id, btn) {
	await postAction(`/admin/posts/unhide/${id}`, btn, null, () => {
		updateVideoInList(id, { hidden: false })
		toast('Video visible ho gayi', 'success')
	})
}

async function deleteVideo(id, btn) {
	if (!confirm(`Video #${id} permanently delete karein?`)) return
	await postAction(`/admin/posts/delete/${id}`, btn, null, () => {
		// Remove from allVideos + re-render
		allVideos = allVideos.filter(v => v.id !== id)
		selectedIds.delete(id)
		applyFilters()
		toast('Video delete ho gayi', 'success')
		closeModal()
	})
}

function updateVideoInList(id, changes) {
	allVideos = allVideos.map(v => v.id === id ? {...v, ...changes} : v)
	applyFilters()
}

// ── Bulk action ───────────────────────────────────────────────────────────────
async function bulkAction(action) {
	if (!selectedIds.size) { toast('Pehle videos select karo', 'info'); return }
	const label = { hide:'hide', unhide:'unhide', delete:'permanently delete' }[action]
	if (!confirm(`${selectedIds.size} videos ko ${label} karna chahte ho?`)) return

	showSpinner(true)
	try {
		const res = await fetch('/api/admin/videos/bulk', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', [CSRF_HEADER]: CSRF_TOKEN },
			body: JSON.stringify({ action, ids: [...selectedIds] })
		})
		const data = await res.json()
		if (data.success) {
			toast(data.message, 'success')
			if (action === 'delete') {
				const toDelete = new Set(selectedIds)
				allVideos = allVideos.filter(v => !toDelete.has(v.id))
			} else {
				const hidden = action === 'hide'
				selectedIds.forEach(id => {
					allVideos = allVideos.map(v => v.id === id ? {...v, hidden} : v)
				})
			}
			clearSelection()
			applyFilters()
		} else {
			toast(data.message || 'Action fail hua', 'error')
		}
	} catch (e) {
		toast('Network error: ' + e.message, 'error')
	} finally {
		showSpinner(false)
	}
}

// ── Video detail modal ────────────────────────────────────────────────────────
function openModal(id) {
	const v = allVideos.find(x => x.id === id)
	if (!v) return

	document.getElementById('modalTitle').textContent = `Video #${v.id} — ${v.userName || '—'}`

	const vid = document.getElementById('modalVideo')
	vid.src = v.videoUrl || ''
	vid.style.display = v.videoUrl ? '' : 'none'

	const hiddenBadge = v.hidden
		? `<span style="background:rgba(244,63,94,.2);color:#fca5a5;border-radius:6px;padding:2px 9px;font-size:12px;font-weight:600">Hidden</span>`
		: `<span style="background:rgba(16,185,129,.2);color:#6ee7b7;border-radius:6px;padding:2px 9px;font-size:12px;font-weight:600">Visible</span>`

	const st = String(v.status || '').toUpperCase()
	const dueTs = v.scheduledAt ? new Date(v.scheduledAt).getTime() : 0
	const isPending = st === 'SCHEDULED' || (Number.isFinite(dueTs) && dueTs > Date.now())
	const scheduledBadge = isPending
		? `<span style="background:rgba(59,130,246,.2);color:#93c5fd;border-radius:6px;padding:2px 9px;font-size:12px;font-weight:600;margin-left:6px">Scheduled</span>`
		: ''

	document.getElementById('modalInfo').innerHTML = `
		<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;font-size:13px">
			<div><span style="color:var(--text-muted)">User:</span> <strong style="color:#fff">${esc(v.userName||'—')}</strong></div>
			<div><span style="color:var(--text-muted)">Status:</span> ${hiddenBadge}${scheduledBadge}</div>
			<div><span style="color:var(--text-muted)">Likes:</span> <strong style="color:#f87171">❤ ${v.likes||0}</strong></div>
			<div><span style="color:var(--text-muted)">Comments:</span> <strong style="color:#a5b4fc">💬 ${v.comments||0}</strong></div>
			<div><span style="color:var(--text-muted)">Created:</span> <span style="color:var(--text)">${formatDate(v.createdAt)}</span>
				${isPending && v.scheduledAt ? `<br/><span style="color:#93c5fd">Due: ${formatDate(v.scheduledAt)}</span>` : ''}
			</div>
			<div style="grid-column:span 2"><span style="color:var(--text-muted)">Content:</span> <span style="color:var(--text)">${esc(v.content||'—')}</span></div>
			<div style="grid-column:span 2">
				<span style="color:var(--text-muted)">Tags:</span>
				<div style="margin-top:4px">${buildTagChips(v.tags)}</div>
			</div>
		</div>
	`

	const hideBtn = v.hidden
		? `<button onclick="unhideVideo(${id},this);updateModal(${id})"
			style="background:rgba(16,185,129,.2);border:1px solid rgba(16,185,129,.4);color:#6ee7b7;border-radius:9px;padding:8px 16px;font-size:13px;cursor:pointer;font-weight:600">👁 Unhide</button>`
		: `<button onclick="hideVideo(${id},this);updateModal(${id})"
			style="background:rgba(245,158,11,.2);border:1px solid rgba(245,158,11,.4);color:#fde68a;border-radius:9px;padding:8px 16px;font-size:13px;cursor:pointer;font-weight:600">🙈 Hide</button>`

	document.getElementById('modalActions').innerHTML = `
		${hideBtn}
		<button onclick="deleteVideo(${id},this)"
			style="background:rgba(244,63,94,.2);border:1px solid rgba(244,63,94,.4);color:#fca5a5;border-radius:9px;padding:8px 16px;font-size:13px;cursor:pointer;font-weight:600">🗑 Delete</button>
	`

	document.getElementById('videoModal').classList.add('show')
}

function updateModal(id) {
	// slight delay to let state update
	setTimeout(() => openModal(id), 200)
}

function closeModal() {
	const vid = document.getElementById('modalVideo')
	vid.pause(); vid.src = ''
	document.getElementById('videoModal').classList.remove('show')
}

// ── API helper ────────────────────────────────────────────────────────────────
async function postAction(url, btn, body, onSuccess) {
	if (btn) { btn.disabled = true; btn.style.opacity = '.5' }
	try {
		const opts = {
			method: 'POST',
			headers: { [CSRF_HEADER]: CSRF_TOKEN }
		}
		if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body) }
		const res = await fetch(url, opts)
		const data = await res.json()
		if (data.success) { onSuccess && onSuccess() }
		else toast(data.message || 'Action fail hua', 'error')
	} catch (e) {
		toast('Network error', 'error')
	} finally {
		if (btn) { btn.disabled = false; btn.style.opacity = '1' }
	}
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function buildTagChips(tags) {
	if (!tags) return '<span style="color:var(--text-dim);font-size:11px">—</span>'
	return tags.split(',').filter(t => t.trim()).map(t =>
		`<span class="tag-chip" onclick="filterByTag('${esc(t.trim())}');event.stopPropagation()" title="Filter by tag">${esc(t.trim())}</span>`
	).join(' ')
}

function buildTagChipsSmall(tags) {
	if (!tags) return ''
	return tags.split(',').filter(t => t.trim()).slice(0,3).map(t =>
		`<span style="background:rgba(99,102,241,.15);color:#a5b4fc;border-radius:12px;padding:1px 7px;font-size:10px;display:inline-block;margin:1px">${esc(t.trim())}</span>`
	).join('')
}

function formatDate(dt) {
	if (!dt) return '—'
	try {
		return new Date(dt).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' })
	} catch { return String(dt).substring(0,10) }
}

function esc(str) {
	if (!str) return ''
	return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')
}

function escUrl(url) {
	if (!url) return ''
	return url.replace(/"/g, '%22').replace(/'/g, '%27')
}

function showSpinner(show) {
	const s = document.getElementById('loadingSpinner')
	if (s) s.style.display = show ? '' : 'none'
}

function showEmpty(show) {
	const e = document.getElementById('emptyState')
	if (e) e.style.display = show ? '' : 'none'
}

function toast(msg, type='info') {
	const box = document.getElementById('toastBox')
	const el = document.createElement('div')
	const icon = type==='success' ? '✅' : type==='error' ? '❌' : 'ℹ️'
	el.className = `toast-item ${type}`
	el.innerHTML = `<span>${icon}</span><span>${msg}</span>`
	box.appendChild(el)
	setTimeout(() => { el.style.opacity='0'; el.style.transition='opacity .4s'; setTimeout(() => el.remove(), 400) }, 3000)
}
