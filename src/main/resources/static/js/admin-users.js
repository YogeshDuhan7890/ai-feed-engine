/* ================================
   admin-users.js
================================ */

function applyFilters() {
	const q = (document.getElementById('userSearch')?.value || '').toLowerCase().trim()
	const role = document.getElementById('filterRole')?.value || ''
	const status = document.getElementById('filterStatus')?.value || ''
	const sort = document.getElementById('sortBy')?.value || 'id-asc'

	const rows = Array.from(document.querySelectorAll('#usersBody tr'))
	let shown = 0

	rows.forEach(row => {
		const name = (row.dataset.name || '').toLowerCase()
		const email = (row.dataset.email || '').toLowerCase()
		const id = (row.id || '').replace('user-row-', '')
		const rowRole = (row.dataset.role || '').toUpperCase()
		const rowStatus = (row.dataset.status || '').toLowerCase()

		const matchQ = !q || name.includes(q) || email.includes(q) || id.includes(q)
		const matchRole = !role || rowRole === role.toUpperCase()
		const matchStatus = !status || rowStatus === status

		const visible = matchQ && matchRole && matchStatus
		row.style.display = visible ? '' : 'none'

		if (visible) shown++
	})

	const tbody = document.getElementById('usersBody')
	const visibleRows = rows.filter(r => r.style.display !== 'none')
	visibleRows.sort((a, b) => {
		switch (sort) {
			case 'id-asc': return parseInt(a.id?.replace('user-row-', '') || 0) - parseInt(b.id?.replace('user-row-', '') || 0)
			case 'id-desc': return parseInt(b.id?.replace('user-row-', '') || 0) - parseInt(a.id?.replace('user-row-', '') || 0)
			case 'name-asc': return (a.dataset.name || '').localeCompare(b.dataset.name || '')
			case 'name-desc': return (b.dataset.name || '').localeCompare(a.dataset.name || '')
			case 'role': return (a.dataset.role || '').localeCompare(b.dataset.role || '')
			case 'status': return (a.dataset.status || '').localeCompare(b.dataset.status || '')
			default: return 0
		}
	})
	visibleRows.forEach(r => tbody.appendChild(r))

	const shownEl = document.getElementById('shownCount')
	if (shownEl) shownEl.textContent = shown
}

function filterUsers() {
	applyFilters()
}

function getRoleBadgeClass(role) {
	switch ((role || '').toUpperCase()) {
		case 'ADMIN': return 'badge bg-danger'
		default: return 'badge bg-secondary'
	}
}

async function saveRole(btn) {
	const uid = btn.dataset.uid
	const name = btn.dataset.name || 'User'
	const select = document.getElementById(`role-select-${uid}`)
	const badge = document.getElementById(`role-badge-${uid}`)
	const row = document.getElementById(`user-row-${uid}`)
	if (!select || !badge) return

	const newRole = (select.value || 'USER').toUpperCase()
	const currentRole = (select.dataset.currentRole || badge.textContent || 'USER').toUpperCase()

	if (newRole === currentRole) {
		toast(`${name} pehle se ${newRole} hai`)
		return
	}

	if (!confirm(`"${name}" ka role ${currentRole} se ${newRole} karna hai?`)) {
		select.value = currentRole
		return
	}

	btn.disabled = true
	btn.textContent = '...'

	try {
		const res = await fetch('/api/admin/roles/assign', {
			method: 'POST',
			headers: csrfHeaders(),
			body: JSON.stringify({ userId: uid, role: newRole })
		})
		const data = await res.json()

		if (data.success) {
			badge.textContent = newRole
			badge.className = getRoleBadgeClass(newRole)
			select.dataset.currentRole = newRole
			if (row) row.dataset.role = newRole
			toast(data.message || `${name} ka role update ho gaya`)
		} else {
			select.value = currentRole
			toast(data.message || 'Role update fail', true)
		}
	} catch (e) {
		select.value = currentRole
		toast('Role update error', true)
	} finally {
		btn.disabled = false
		btn.textContent = 'Save'
	}
}

async function toggleBlock(btn) {
	const uid = btn.dataset.uid
	const action = btn.dataset.action
	btn.disabled = true
	btn.textContent = '...'

	try {
		const res = await fetch(`/admin/users/${action}Ajax/${uid}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			const row = document.getElementById(`user-row-${uid}`)
			const badge = row?.querySelector('.status-badge')
			if (action === 'block') {
				if (row) row.dataset.status = 'blocked'
				if (badge) {
					badge.className = 'badge bg-danger status-badge'
					badge.textContent = 'Blocked'
				}
				btn.textContent = 'Unblock'
				btn.className = 'btn btn-outline-success btn-sm'
				btn.dataset.action = 'unblock'
			} else {
				if (row) row.dataset.status = 'active'
				if (badge) {
					badge.className = 'badge bg-success status-badge'
					badge.textContent = 'Active'
				}
				btn.textContent = 'Block'
				btn.className = 'btn btn-outline-danger btn-sm'
				btn.dataset.action = 'block'
			}
			toast(action === 'block' ? 'User block ho gaya' : 'User unblock ho gaya')
		} else {
			toast('Action fail: ' + (data.message || ''), true)
		}
	} catch (e) {
		toast('Error ho gaya', true)
	}

	btn.disabled = false
}

async function deleteUser(btn) {
	const uid = btn.dataset.uid
	const name = btn.dataset.name
	if (!confirm(`"${name}" ko delete karna chahte ho?\n\nIs user ki saari videos bhi delete ho jaengi.\nYe action permanent hai.`)) return

	btn.disabled = true
	btn.textContent = '...'

	try {
		const res = await fetch(`/admin/users/delete/${uid}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			document.getElementById(`user-row-${uid}`)?.remove()
			toast(`${name} delete ho gaya`)
		} else {
			toast('Delete fail: ' + (data.message || ''), true)
			btn.disabled = false
			btn.textContent = 'Delete'
		}
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
		btn.textContent = 'Delete'
	}
}

function openTempBan(btn) {
	const uid = btn.dataset.uid
	const name = btn.dataset.name
	document.getElementById('tempBanUserId').value = uid
	document.getElementById('tempBanUserName').textContent = name
	new bootstrap.Modal(document.getElementById('tempBanModal')).show()
}

async function applyTempBan() {
	const uid = document.getElementById('tempBanUserId').value
	const days = parseInt(document.getElementById('tempBanDays').value)

	try {
		const res = await fetch(`/admin/users/tempban/${uid}`, {
			method: 'POST',
			headers: csrfHeaders(),
			body: JSON.stringify({ days })
		})
		const data = await res.json()

		if (data.success) {
			const row = document.getElementById(`user-row-${uid}`)
			const badge = row?.querySelector('.status-badge')
			if (badge) {
				badge.className = 'badge bg-warning text-dark status-badge'
				badge.textContent = `Banned (${days}d)`
			}
			bootstrap.Modal.getInstance(document.getElementById('tempBanModal'))?.hide()
			toast(data.message || 'Temp ban laga diya')
		} else {
			toast(data.message || 'Action fail', true)
		}
	} catch (e) {
		toast('Error', true)
	}
}

function openWarnModal(btn) {
	const uid = btn.dataset.uid
	const name = btn.dataset.name
	document.getElementById('warnUserId').value = uid
	document.getElementById('warnUserName').textContent = 'Warning bhejo: ' + name
	new bootstrap.Modal(document.getElementById('warnModal')).show()
}

async function sendWarning() {
	const uid = document.getElementById('warnUserId').value
	const reason = document.getElementById('warnReason').value

	try {
		const res = await fetch(`/admin/users/warn/${uid}`, {
			method: 'POST',
			headers: csrfHeaders(),
			body: JSON.stringify({ reason })
		})
		const data = await res.json()

		if (data.success) {
			bootstrap.Modal.getInstance(document.getElementById('warnModal'))?.hide()
			toast(data.message || 'Warning bhej di')
		} else {
			toast(data.message || 'Warning fail', true)
		}
	} catch (e) {
		toast('Error', true)
	}
}

async function promoteUser(btn) {
	const uid = btn.dataset.uid
	const name = btn.dataset.name
	if (!confirm(`"${name}" ko ADMIN banana chahte ho?`)) return

	btn.disabled = true

	try {
		await fetch(`/admin/users/promote/${uid}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		window.location.reload()
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
	}
}

async function openActivity(btn) {
	const uid = btn.dataset.uid
	const modal = new bootstrap.Modal(document.getElementById('activityModal'))
	document.getElementById('activityBody').innerHTML = '<div class="text-center py-4 text-muted">Loading...</div>'
	modal.show()

	try {
		const data = await fetch(`/api/admin/users/${uid}/activity`).then(r => r.json())
		let html = `
			<div class="row g-3 mb-3">
				<div class="col-md-6">
					<div class="p-3 bg-light rounded">
						<div class="fw-bold">${esc(data.name)}</div>
						<div class="text-muted small">${esc(data.email)}</div>
						<div class="mt-1">
							<span class="badge ${data.role === 'ADMIN' ? 'bg-danger' : 'bg-secondary'}">${data.role}</span>
							<span class="badge ${data.enabled ? 'bg-success' : 'bg-danger'} ms-1">${data.enabled ? 'Active' : 'Blocked'}</span>
							${data.isBanned ? '<span class="badge bg-warning text-dark ms-1">Temp Banned</span>' : ''}
						</div>
					</div>
				</div>
				<div class="col-md-6">
					<div class="p-3 bg-light rounded">
						<div class="text-muted small">Total Videos</div>
						<div class="fw-bold fs-4 text-primary">${data.totalPosts}</div>
						<div class="text-muted small mt-2">Warnings: ${(data.warnings || []).length}</div>
					</div>
				</div>
			</div>
		`

		if (data.recentPosts && data.recentPosts.length > 0) {
			html += `
				<h6 class="mb-2">Recent Videos</h6>
				<table class="table table-sm table-hover">
					<thead><tr><th>ID</th><th>Content</th><th>Likes</th><th>Date</th><th>Action</th></tr></thead>
					<tbody>
			`

			data.recentPosts.forEach(p => {
				html += `
					<tr>
						<td><code>${p.id}</code></td>
						<td class="post-content-cell">${esc(p.content || '-')}</td>
						<td>${p.likes}</td>
						<td class="text-muted small">${p.createdAt ? p.createdAt.substring(0, 10) : '-'}</td>
						<td><button class="btn btn-danger btn-sm" onclick="deletePostFromModal(${p.id}, this)">Delete</button></td>
					</tr>
				`
			})

			html += '</tbody></table>'
		}

		if (data.warnings && data.warnings.length > 0) {
			html += '<h6 class="mb-2 mt-3">Warning History</h6><ul class="list-group list-group-flush">'
			data.warnings.forEach(w => {
				try {
					const obj = JSON.parse(w)
					html += `<li class="list-group-item small">${esc(obj.reason)} - <span class="text-muted">${esc(obj.time ? obj.time.substring(0, 10) : '')}</span></li>`
				} catch (e) {
					html += `<li class="list-group-item small">${esc(w)}</li>`
				}
			})
			html += '</ul>'
		}

		document.getElementById('activityBody').innerHTML = html
	} catch (e) {
		document.getElementById('activityBody').innerHTML = '<div class="text-danger p-3">Load fail ho gaya</div>'
	}
}

async function deletePostFromModal(postId, btn) {
	if (!confirm(`Post #${postId} delete karna chahte ho?`)) return

	btn.disabled = true
	btn.textContent = '...'

	try {
		const res = await fetch(`/admin/posts/delete/${postId}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			btn.closest('tr')?.remove()
			toast('Post delete ho gaya')
		} else {
			toast(data.message || 'Delete fail', true)
			btn.disabled = false
			btn.textContent = 'Delete'
		}
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
		btn.textContent = 'Delete'
	}
}

async function openVideosModal(btn) {
	const uid = btn.dataset.uid
	const userName = btn.dataset.name
	const modal = new bootstrap.Modal(document.getElementById('videosModal'))
	document.getElementById('videosModalTitle').textContent = `${userName} ki Videos`
	document.getElementById('videosModalBody').innerHTML = '<div class="text-center py-4 text-muted">Loading...</div>'
	modal.show()

	try {
		const videos = await fetch(`/api/admin/users/${uid}/videos`).then(r => r.json())

		if (!videos.length) {
			document.getElementById('videosModalBody').innerHTML = '<div class="text-center py-5 text-muted">Koi video nahi mili</div>'
			return
		}

		let html = `
			<table class="table table-sm table-hover">
				<thead class="table-dark">
					<tr><th>ID</th><th>Content</th><th>Tags</th><th>Likes</th><th>Comments</th><th>Shares</th><th>Status</th><th>Date</th><th>Actions</th></tr>
				</thead>
				<tbody>
		`

		videos.forEach(v => {
			const statusBadge = v.hidden
				? '<span class="badge bg-secondary">Hidden</span>'
				: '<span class="badge bg-success">Visible</span>'

			html += `
				<tr id="vmodal-row-${v.id}" ${v.hidden ? 'class="table-secondary"' : ''}>
					<td><code>${v.id}</code></td>
					<td class="post-content-cell">${esc(v.content || '-')}</td>
					<td class="text-muted small">${esc(v.tags || '-')}</td>
					<td class="text-danger">${v.likes}</td>
					<td class="text-primary">${v.comments}</td>
					<td class="text-success">${v.shares}</td>
					<td>${statusBadge}</td>
					<td class="text-muted small">${v.createdAt ? v.createdAt.substring(0, 10) : '-'}</td>
					<td>
						<div class="d-flex gap-1">
							<button class="btn btn-danger btn-sm" onclick="deletePost(${v.id}, this)">Delete</button>
							${v.hidden
								? `<button class="btn btn-outline-success btn-sm" onclick="unhidePost(${v.id}, this)">Show</button>`
								: `<button class="btn btn-outline-secondary btn-sm" onclick="hidePost(${v.id}, this)">Hide</button>`
							}
							<button class="btn btn-outline-primary btn-sm" onclick="openTagsModal(${v.id}, '${esc(v.tags || '')}')">Tags</button>
						</div>
					</td>
				</tr>
			`
		})

		html += '</tbody></table>'
		document.getElementById('videosModalBody').innerHTML = html
	} catch (e) {
		document.getElementById('videosModalBody').innerHTML = '<div class="text-danger p-3">Load fail ho gaya</div>'
	}
}

async function deletePost(postId, btn) {
	if (!confirm(`Post #${postId} delete karna chahte ho?`)) return

	btn.disabled = true
	btn.textContent = '...'

	try {
		const res = await fetch(`/admin/posts/delete/${postId}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			document.getElementById(`vmodal-row-${postId}`)?.remove()
			btn.closest('tr')?.remove()
			toast('Post delete ho gaya')
		} else {
			toast('Delete fail: ' + (data.message || ''), true)
			btn.disabled = false
			btn.textContent = 'Delete'
		}
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
		btn.textContent = 'Delete'
	}
}

async function hidePost(postId, btn) {
	btn.disabled = true

	try {
		const res = await fetch(`/admin/posts/hide/${postId}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			const row = document.getElementById(`vmodal-row-${postId}`)
			if (row) {
				row.classList.add('table-secondary')
				row.children[6].innerHTML = '<span class="badge bg-secondary">Hidden</span>'
			}
			btn.outerHTML = `<button class="btn btn-outline-success btn-sm" onclick="unhidePost(${postId}, this)">Show</button>`
			toast('Video hide ho gayi')
		} else {
			toast(data.message || 'Action fail', true)
			btn.disabled = false
		}
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
	}
}

async function unhidePost(postId, btn) {
	btn.disabled = true

	try {
		const res = await fetch(`/admin/posts/unhide/${postId}`, {
			method: 'POST',
			headers: csrfHeaders()
		})
		const data = await res.json()

		if (data.success) {
			const row = document.getElementById(`vmodal-row-${postId}`)
			if (row) {
				row.classList.remove('table-secondary')
				row.children[6].innerHTML = '<span class="badge bg-success">Visible</span>'
			}
			btn.outerHTML = `<button class="btn btn-outline-secondary btn-sm" onclick="hidePost(${postId}, this)">Hide</button>`
			toast('Video visible ho gayi')
		} else {
			toast(data.message || 'Action fail', true)
			btn.disabled = false
		}
	} catch (e) {
		toast('Error', true)
		btn.disabled = false
	}
}

function openTagsModal(postId, currentTags) {
	const modal = document.getElementById('tagsModal')
	if (!modal) {
		toast('Tags edit dashboard se karo', true)
		return
	}

	document.getElementById('tagsPostId').value = postId
	document.getElementById('tagsInput').value = currentTags
	new bootstrap.Modal(modal).show()
}

function csrfHeaders() {
	const token = document.querySelector('meta[name="_csrf"]')?.content || ''
	const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'
	return { [header]: token, 'Content-Type': 'application/json' }
}

function esc(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}

function toast(msg, err = false) {
	const t = document.getElementById('adminToast')
	if (!t) return

	t.textContent = msg
	t.style.background = err ? '#c0392b' : '#1f2937'
	t.style.display = 'block'

	setTimeout(() => {
		t.style.display = 'none'
	}, 3000)
}
