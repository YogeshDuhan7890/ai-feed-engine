const ROLES = ['ADMIN', 'USER']
const ROLE_COLORS = {
	ADMIN: 'role-ADMIN',
	USER: 'role-USER'
}

let allUsers = []

function csrfHeaders() {
	return {
		'Content-Type': 'application/json',
		[CSRF_HEADER]: CSRF_TOKEN
	}
}

async function loadAll() {
	await Promise.all([loadUsers(), loadStats()])
}

async function loadUsers() {
	try {
		const res = await fetch('/api/admin/roles/users')
		allUsers = await res.json()
		renderTable(allUsers)
	} catch (error) {
		document.getElementById('roleTableWrap').innerHTML =
			'<div class="text-danger small">Load fail: ' + error.message + '</div>'
	}
}

async function loadStats() {
	try {
		const res = await fetch('/api/admin/roles/stats')
		const data = await res.json()
		const counts = data.roleCounts || {}
		setText('statAdmin', (counts.ADMIN || 0) + ' Admin')
		setText('statUser', (counts.USER || 0) + ' User')
		renderChangelog(data.recentChanges || [])
	} catch (error) {
		console.error('Role stats fail:', error)
	}
}

function renderTable(users) {
	const wrap = document.getElementById('roleTableWrap')
	if (!users || users.length === 0) {
		wrap.innerHTML = '<div class="text-muted text-center py-4">Koi user nahi mila</div>'
		return
	}

	const rows = users.map(user => {
		const roleOptions = ROLES.map(role =>
			`<option value="${role}" ${user.role === role ? 'selected' : ''}>${role}</option>`
		).join('')

		const avatar = user.avatar
			? `<img src="${escHtml(user.avatar)}" class="avatar-sm" onerror="this.src='/images/default.png'">`
			: '<div class="avatar-placeholder">USR</div>'

		return `<tr id="role-row-${user.id}" data-name="${escHtml(user.name)}" data-email="${escHtml(user.email)}">
			<td>${user.id}</td>
			<td>
				<div class="d-flex align-items-center gap-2">
					${avatar}
					<div>
						<div class="fw-semibold small">${escHtml(user.name)}</div>
						<div class="text-muted" style="font-size:0.75rem">${escHtml(user.email)}</div>
					</div>
				</div>
			</td>
			<td>
				<span class="role-badge ${ROLE_COLORS[user.role] || 'role-USER'}" id="badge-${user.id}">${user.role}</span>
			</td>
			<td>
				<div class="d-flex gap-2 align-items-center">
					<select class="form-select form-select-sm" id="select-${user.id}" style="width:130px">
						${roleOptions}
					</select>
					<button class="btn btn-primary btn-sm" onclick="confirmRoleChange(${user.id}, '${escHtml(user.name)}')">Assign</button>
				</div>
			</td>
		</tr>`
	}).join('')

	wrap.innerHTML = `<table class="table table-hover mb-0">
		<thead class="table-dark">
			<tr>
				<th style="width:50px">ID</th>
				<th>User</th>
				<th style="width:120px">Current Role</th>
				<th>Change Role</th>
			</tr>
		</thead>
		<tbody id="roleTableBody">${rows}</tbody>
	</table>`
}

function filterRoleUsers(query) {
	const q = String(query || '').toLowerCase()
	const filtered = allUsers.filter(user =>
		(user.name || '').toLowerCase().includes(q) ||
		(user.email || '').toLowerCase().includes(q) ||
		String(user.id).includes(q)
	)
	renderTable(filtered)
}

function confirmRoleChange(userId, userName) {
	const select = document.getElementById('select-' + userId)
	const newRole = select?.value
	if (!newRole) return

	const badge = document.getElementById('badge-' + userId)
	const currentRole = badge?.textContent || '?'

	if (currentRole === newRole) {
		showToast(userName + ' already has ' + newRole, 'warning')
		return
	}

	document.getElementById('confirmText').textContent =
		`"${userName}" ka role ${currentRole} to ${newRole} karna hai?`

	const modal = new bootstrap.Modal(document.getElementById('confirmModal'))
	modal.show()

	document.getElementById('confirmOkBtn').onclick = async () => {
		modal.hide()
		await assignRole(userId, newRole)
	}
}

async function assignRole(userId, newRole) {
	try {
		const res = await fetch('/api/admin/roles/assign', {
			method: 'POST',
			headers: csrfHeaders(),
			body: JSON.stringify({ userId, role: newRole })
		})
		const data = await res.json()

		if (data.success) {
			const badge = document.getElementById('badge-' + userId)
			if (badge) {
				badge.textContent = newRole
				badge.className = 'role-badge ' + (ROLE_COLORS[newRole] || 'role-USER')
			}

			const user = allUsers.find(item => item.id === userId)
			if (user) user.role = newRole

			showToast(data.message || 'Role updated', 'success')
			await loadStats()
		} else {
			showToast(data.message || 'Role assign fail', 'danger')
		}
	} catch (error) {
		showToast('Error: ' + error.message, 'danger')
	}
}

function renderChangelog(changes) {
	const el = document.getElementById('changelogList')
	if (!el) return

	if (!changes || changes.length === 0) {
		el.innerHTML = '<div class="text-muted small text-center py-2">Koi role change abhi tak nahi hua.</div>'
		return
	}

	el.innerHTML = changes.map(raw => {
		let item = {}
		try { item = JSON.parse(raw) } catch { return '' }
		return `<div class="changelog-item">
			<div>
				<strong>${escHtml(item.name || '')}</strong>
				<span class="role-badge ${ROLE_COLORS[item.oldRole] || 'role-USER'} ms-1" style="font-size:0.65rem">${item.oldRole}</span>
				to
				<span class="role-badge ${ROLE_COLORS[item.newRole] || 'role-USER'}" style="font-size:0.65rem">${item.newRole}</span>
			</div>
			<div class="text-muted" style="font-size:0.72rem">${item.time || ''}</div>
		</div>`
	}).join('')
}

function showToast(msg, type) {
	const div = document.createElement('div')
	div.className = `alert alert-${type} position-fixed bottom-0 end-0 m-3 shadow`
	div.style.cssText = 'z-index:9999;min-width:240px;font-size:0.85rem'
	div.textContent = msg
	document.body.appendChild(div)
	setTimeout(() => div.remove(), 3500)
}

function setText(id, val) {
	const el = document.getElementById(id)
	if (el) el.textContent = val
}

function escHtml(str) {
	if (!str) return ''
	return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

loadAll()
