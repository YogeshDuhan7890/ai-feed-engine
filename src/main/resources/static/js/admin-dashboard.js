/* ================================
   admin-dashboard.js
================================ */

let chartInstance = null

document.addEventListener('DOMContentLoaded', () => {
	initChart()
	refreshStats()
	loadTrending()
	loadAdminNotifications()
	loadAlerts()
	updateRefreshTime()

	setInterval(refreshStats, 5000)
	setInterval(loadAdminNotifications, 15000)
	setInterval(loadAlerts, 15000)
})

document.addEventListener('click', event => {
	const wrap = document.getElementById('notifWrap')
	if (wrap && !wrap.contains(event.target)) {
		const panel = document.getElementById('notifPanel')
		if (panel) panel.style.display = 'none'
	}
})

function initChart() {
	const ctx = document.getElementById('engChart')
	if (!ctx) return

	chartInstance = new Chart(ctx, {
		type: 'line',
		data: {
			labels: window.chartLabels || [],
			datasets: [{
				label: 'Daily Engagements',
				data: window.chartValues || [],
				borderColor: '#0d6efd',
				backgroundColor: 'rgba(13,110,253,0.1)',
				tension: 0.4,
				fill: true,
				pointRadius: 4
			}]
		},
		options: {
			responsive: true,
			plugins: {
				legend: {
					display: false
				}
			}
		}
	})
}

async function refreshStats() {
	try {
		const data = await fetchJson('/api/admin/stats')
		setText('usersCount', data.users)
		setText('postsCount', data.posts)
		setText('engagementCount', data.engagements)
		setText('pendingReports', data.pendingReports)

		if (chartInstance) {
			chartInstance.data.labels = data.chartLabels || []
			chartInstance.data.datasets[0].data = data.chartValues || []
			chartInstance.update()
		}

		updateRefreshTime()
	} catch (e) {
		console.error('Stats error:', e)
	}
}

async function loadAdminNotifications() {
	try {
		const data = await fetchJson('/api/admin/notifications')
		const pendingReports = data.pendingReports || 0
		const blockedUsers = data.blockedUsers || 0
		const activeTempBans = data.activeTempBans || 0
		const hiddenPosts = data.hiddenPosts || 0
		const total = pendingReports + activeTempBans

		const bellCount = document.getElementById('bellCount')
		if (bellCount) {
			bellCount.textContent = total
			bellCount.style.display = total > 0 ? '' : 'none'
		}

		const list = document.getElementById('notifList')
		if (!list) return

		const items = [
			{ label: 'Pending Reports', value: pendingReports, link: '/admin/reports', color: '#dc3545' },
			{ label: 'Blocked Users', value: blockedUsers, link: '/admin/users', color: '#fd7e14' },
			{ label: 'Active Temp Bans', value: activeTempBans, link: '/admin/users', color: '#ffc107' },
			{ label: 'Hidden Videos', value: hiddenPosts, link: '/admin/dashboard', color: '#6c757d' }
		]

		list.innerHTML = items.map(item => `
			<a href="${item.link}" style="text-decoration:none; color:inherit; display:flex; justify-content:space-between; align-items:center; padding:10px 14px; border-bottom:1px solid #f0f0f0;">
				<span>${item.label}</span>
				<span class="badge" style="background:${item.color}">${item.value}</span>
			</a>
		`).join('')
	} catch (e) {
		console.error('Notifications error:', e)
	}
}

function toggleNotifPanel() {
	const panel = document.getElementById('notifPanel')
	if (!panel) return
	panel.style.display = panel.style.display === 'none' ? '' : 'none'
}

async function loadTrending() {
	try {
		const data = await fetchJson('/api/admin/trending')
		const list = data.trending || data.data || data || []
		hide('trendingLoading')

		if (!list.length) {
			setText('trendingLoading', 'Koi trending video nahi mila')
			show('trendingLoading')
			return
		}

		const tbody = document.getElementById('trendingBody')
		if (!tbody) return

		tbody.innerHTML = ''

		list.forEach(p => {
			const id = p.postId || p.id
			const userId = Number(p.userId)
			const hasUser = Number.isFinite(userId) && userId > 0
			const userHref = hasUser ? `/profile/user/${userId}` : '#'
			const actionHtml = hasUser
				? `<a href="${userHref}" class="btn btn-outline-primary btn-sm">View User</a>`
				: '<span class="text-muted small">User unavailable</span>'
			const tr = document.createElement('tr')

			tr.innerHTML = `
				<td><code>${id}</code></td>
				<td>${esc(p.userName || '-')}</td>
				<td><span class="badge bg-warning text-dark">${p.score || 0}</span></td>
				<td>${actionHtml}</td>
			`

			tbody.appendChild(tr)
		})

		show('trendingTableWrap')
	} catch (e) {
		console.error('Trending error:', e)
		setText('trendingLoading', 'Trending load fail ho gaya.')
		show('trendingLoading')
	}
}

async function loadAlerts() {
	try {
		const alerts = await fetch('/api/admin/alerts/recent', {
			headers: csrfHeaders()
		}).then(r => r.json())

		const el = document.getElementById('alertsList')
		if (!el) return

		if (!alerts.length) {
			el.innerHTML = '<div class="alert alert-success py-2 small">Sab theek hai</div>'
			return
		}

		el.innerHTML = alerts.map(a => {
			const cls = 'alert-' + (a.severity || 'INFO')
			return `<div class="alert-item ${cls}">${esc(a.message || '-')}</div>`
		}).join('')
	} catch (e) {
		console.error('Alerts fail:', e)
	}
}

function updateRefreshTime() {
	const el = document.getElementById('lastRefresh')
	if (el) {
		el.textContent = 'updated ' + new Date().toLocaleTimeString()
	}
}

async function fetchJson(url) {
	const res = await fetch(url, {
		headers: csrfHeaders()
	})

	if (!res.ok) {
		throw new Error('API error ' + res.status)
	}

	return res.json()
}

function csrfHeaders() {
	const token = document.querySelector('meta[name="_csrf"]')?.content || ''
	const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'
	return token ? { [header]: token } : {}
}

function esc(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}

function setText(id, val) {
	const el = document.getElementById(id)
	if (el) el.innerText = val ?? '-'
}

function show(id) {
	const el = document.getElementById(id)
	if (el) el.style.display = ''
}

function hide(id) {
	const el = document.getElementById(id)
	if (el) el.style.display = 'none'
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
