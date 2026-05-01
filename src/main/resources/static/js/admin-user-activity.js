let allUsers = []
let activeUserId = null

document.addEventListener('DOMContentLoaded', () => {
	loadUsers()
})

async function loadUsers() {
	try {
		const response = await fetch('/api/admin/users')
		const data = await response.json()
		allUsers = data.users || data.data || data || []
		renderUsers(allUsers)
	} catch (error) {
		console.error(error)
		document.getElementById('userListMsg').innerText = 'Failed to load users.'
	}
}

function renderUsers(users) {
	const list = document.getElementById('userList')
	if (!list) return

	if (!users.length) {
		list.innerHTML = '<div class="text-muted text-center py-4">No users found.</div>'
		return
	}

	list.innerHTML = users.map(user => {
		const name = escapeHtml(user.name || user.username || 'User')
		const email = escapeHtml(user.email || '')
		const avatar = user.avatar
			? `<img class="avatar-sm" src="${escapeHtml(user.avatar)}" alt="${name}" onerror="this.remove()">`
			: '<div class="avatar-sm avatar-placeholder">U</div>'
		const role = escapeHtml(user.role || 'USER')
		const enabled = user.enabled !== false

		return `
			<div class="user-picker-item ${activeUserId === user.id ? 'active' : ''}" data-user-id="${user.id}" onclick="loadActivity(${user.id})">
				${avatar}
				<div style="min-width:0; flex:1;">
					<div class="fw-semibold text-truncate">${name}</div>
					<div class="text-muted small text-truncate">${email}</div>
					<div class="mt-1 d-flex gap-2 flex-wrap">
						<span class="badge ${enabled ? 'bg-success' : 'bg-danger'}">${enabled ? 'Active' : 'Blocked'}</span>
						<span class="badge bg-secondary">${role}</span>
					</div>
				</div>
			</div>
		`
	}).join('')
}

function filterUsers(query) {
	const value = String(query || '').toLowerCase().trim()
	const filtered = allUsers.filter(user =>
		(user.name || '').toLowerCase().includes(value) ||
		(user.username || '').toLowerCase().includes(value) ||
		(user.email || '').toLowerCase().includes(value)
	)
	renderUsers(filtered)
}

async function loadActivity(userId) {
	activeUserId = userId
	renderUsers(filterCurrentList())

	const panel = document.getElementById('activityPanel')
	panel.innerHTML = '<div class="card shadow-sm activity-empty">Loading user activity...</div>'

	try {
		const response = await fetch(`/api/admin/user-activity/${userId}`)
		const data = await response.json()

		if (!response.ok || data.error) {
			throw new Error(data.error || 'Failed to load activity')
		}

		renderActivity(data)
	} catch (error) {
		console.error(error)
		panel.innerHTML = '<div class="card shadow-sm activity-empty text-danger">Failed to load user activity.</div>'
	}
}

function renderActivity(data) {
	const panel = document.getElementById('activityPanel')
	const timeline = buildTimeline(data)
	const statusBadges = [
		`<span class="badge ${data.enabled ? 'bg-success' : 'bg-danger'}">${data.enabled ? 'Active' : 'Blocked'}</span>`,
		`<span class="badge bg-secondary">${escapeHtml(data.role || 'USER')}</span>`
	]

	if (data.isBanned) {
		statusBadges.push(`<span class="badge bg-warning">Temp ban ${Number(data.banDays || 0)}d</span>`)
	}

	panel.innerHTML = `
		<div class="card shadow-sm p-4 mb-4">
			<div class="activity-hero">
				${renderAvatar(data)}
				<div>
					<div class="d-flex flex-wrap gap-2 align-items-center mb-2">
						<h5 class="mb-0">${escapeHtml(data.name || 'User')}</h5>
						${statusBadges.join('')}
					</div>
					<div class="text-muted">${escapeHtml(data.email || '-')}</div>
					<div class="mt-3 d-flex gap-2 flex-wrap">
						<a href="/profile/user/${Number(data.id) || 0}" class="btn btn-outline-secondary btn-sm">Open Profile</a>
						<a href="/admin/users" class="btn btn-outline-secondary btn-sm">Manage User</a>
					</div>
				</div>
			</div>

			<div class="activity-stat-grid">
				<div class="activity-stat">
					<span class="activity-stat-label">Total Posts</span>
					<div class="activity-stat-value">${Number(data.totalPosts || 0)}</div>
				</div>
				<div class="activity-stat">
					<span class="activity-stat-label">Warnings</span>
					<div class="activity-stat-value">${Number(data.warnings || 0)}</div>
				</div>
				<div class="activity-stat">
					<span class="activity-stat-label">Role Changes</span>
					<div class="activity-stat-value">${Array.isArray(data.roleChangelog) ? data.roleChangelog.length : 0}</div>
				</div>
				<div class="activity-stat">
					<span class="activity-stat-label">Recent Timeline</span>
					<div class="activity-stat-value">${timeline.length}</div>
				</div>
			</div>
		</div>

		<div class="card shadow-sm p-4">
			<div class="d-flex justify-content-between align-items-center mb-3">
				<h6 class="mb-0">Timeline</h6>
				<span class="text-muted small">Newest activity first</span>
			</div>
			${renderTimeline(timeline)}
		</div>
	`
}

function buildTimeline(data) {
	const timeline = []

	;(data.posts || []).forEach(post => {
		timeline.push({
			at: post.createdAt,
			title: `Posted video #${post.id || '-'}`,
			body: post.content || 'Video uploaded without caption.',
			meta: buildPostMeta(post)
		})
	})

	;(data.roleChangelog || []).forEach(change => {
		const oldRole = change.oldRole || change.previousRole || 'Unknown'
		const newRole = change.newRole || change.role || 'Updated'
		timeline.push({
			at: change.createdAt || change.time || change.updatedAt,
			title: 'Role updated',
			body: `${oldRole} -> ${newRole}`,
			meta: change.by ? `Changed by ${change.by}` : 'Role history entry'
		})
	})

	return timeline.sort((left, right) => parseTime(right.at) - parseTime(left.at))
}

function renderTimeline(items) {
	if (!items.length) {
		return '<div class="activity-empty">No recent activity found for this user.</div>'
	}

	return `<div class="timeline-list">
		${items.map(item => `
			<div class="timeline-item">
				<div class="timeline-dot"></div>
				<div class="timeline-title">${escapeHtml(item.title)}</div>
				<div class="timeline-meta">${escapeHtml(formatDate(item.at))}${item.meta ? ` | ${escapeHtml(item.meta)}` : ''}</div>
				<div class="timeline-body">${escapeHtml(item.body)}</div>
			</div>
		`).join('')}
	</div>`
}

function renderAvatar(data) {
	const name = escapeHtml(data.name || 'User')
	if (data.avatar) {
		return `<img class="activity-avatar" src="${escapeHtml(data.avatar)}" alt="${name}">`
	}
	return `<div class="activity-avatar">${name.charAt(0)}</div>`
}

function buildPostMeta(post) {
	const stats = []
	if (post.likes != null) stats.push(`${post.likes} likes`)
	if (post.comments != null) stats.push(`${post.comments} comments`)
	if (post.shares != null) stats.push(`${post.shares} shares`)
	return stats.join(' | ')
}

function filterCurrentList() {
	const search = document.getElementById('userSearch')
	if (!search || !search.value.trim()) return allUsers
	const value = search.value.toLowerCase().trim()
	return allUsers.filter(user =>
		(user.name || '').toLowerCase().includes(value) ||
		(user.username || '').toLowerCase().includes(value) ||
		(user.email || '').toLowerCase().includes(value)
	)
}

function parseTime(value) {
	if (!value) return 0
	const date = new Date(value)
	return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function formatDate(value) {
	if (!value) return 'Unknown time'
	const date = new Date(value)
	return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('en-IN')
}

function escapeHtml(value) {
	return String(value || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}
